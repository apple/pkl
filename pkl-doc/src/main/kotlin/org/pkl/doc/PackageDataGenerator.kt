/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.doc

import java.io.IOException
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.writer
import kotlin.streams.toList
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.pkl.commons.createParentDirectories
import org.pkl.commons.readString
import org.pkl.commons.toUri
import org.pkl.commons.walk
import org.pkl.core.*
import org.pkl.core.util.IoUtils

/**
 * Reads and writes package-data.json, which contains enough information to include a package's
 * previously generated docs in a newly generated doc website. This is useful if there's a problem
 * with fetching or evaluating the latest package version.
 */
internal class PackageDataGenerator(private val outputDir: Path) {
    fun generate(pkg: DocPackage) {
        val path =
            outputDir.resolve(pkg.name).resolve(pkg.version).resolve("package-data.json").apply {
                createParentDirectories()
            }
        PackageData(pkg).write(path)
    }

    fun readAll(): List<PackageData> {
        return outputDir.walk().use { paths ->
            paths
                .filter { it.fileName?.toString() == "package-data.json" }
                .map { PackageData.read(it) }
                .toList()
        }
    }
}

/** Uniquely identifies a specific version of a package, module, class, or type alias. */
internal sealed class ElementRef {
    /** The package name. */
    abstract val pkg: String

    /// The URI of the package, if any
    abstract val pkgUri: URI?

    /** The package version. */
    abstract val version: String

    /** The Pkldoc page URL of the element relative to its Pkldoc website root. */
    abstract val pageUrl: URI

    /**
     * The Pkldoc page URL of the element relative to [other]'s page URL. Assumes that both elements
     * have the same Pkldoc website root.
     */
    fun pageUrlRelativeTo(other: ElementRef): String {
        return IoUtils.relativize(pageUrl, other.pageUrl).toString()
    }
}

/** Uniquely identifies a specific version of a package. */
@Serializable
internal data class PackageRef(
    /** The package name. */
    override val pkg: String,

    /** The package's URI, if any. */
    override val pkgUri: @Contextual URI?,

    /** The package version. */
    override val version: String
) : ElementRef() {
    override val pageUrl: URI by lazy { "$pkg/$version/index.html".toUri() }
}

/** Uniquely identifies a specific version of a module. */
@Serializable
internal data class ModuleRef(
    /** The package name. */
    override val pkg: String,

    /** The package's URI, if any. */
    override val pkgUri: @Contextual URI?,

    /** The package version. */
    override val version: String,

    /** The module path. */
    val module: String
) : ElementRef() {
    override val pageUrl: URI by lazy { "$pkg/$version/$module/index.html".toUri() }

    val moduleClassRef: TypeRef by lazy {
        TypeRef(pkg, pkgUri, version, module, PClassInfo.MODULE_CLASS_NAME)
    }

    val id: ModuleId by lazy { ModuleId(pkg, module) }

    val fullName: String by lazy { "$pkg.${module.replace('/', '.')}" }
}

/** Uniquely identifies a specific version of a class or type alias. */
@Serializable
internal data class TypeRef(
    /** The package name. */
    override val pkg: String,

    /** The package's URI, if any. */
    override val pkgUri: @Contextual URI?,

    /** The package version. */
    override val version: String,

    /** The module path. */
    val module: String,

    /** The simple type name. */
    val type: String,

    /** Whether this is a type alias rather than a class. */
    val isTypeAlias: Boolean = false
) : ElementRef() {

    val id: TypeId by lazy { TypeId(pkg, module, type) }

    val displayName: String by lazy { if (isModuleClass) module.substringAfterLast('/') else type }

    override val pageUrl: URI by lazy {
        when {
            isTypeAlias -> "$pkg/$version/$module/index.html#$type".toUri()
            isModuleClass -> "$pkg/$version/$module/index.html".toUri()
            else -> "$pkg/$version/$module/$type.html".toUri()
        }
    }

    private val isModuleClass: Boolean
        get() = type == PClassInfo.MODULE_CLASS_NAME
}

/** Uniquely identifies a package (modulo its version). */
internal typealias PackageId = String

/** Uniquely identifies a module (modulo its version). */
internal data class ModuleId(val pkg: String, val module: String)

/** Uniquely identifies a class or type alias (modulo its version). */
internal data class TypeId(val pkg: String, val module: String, val type: String)

/**
 * Persisted data for a package. Used by subsequent Pkldoc runs to generate main page and runtime
 * data (e.g., package versions and usages).
 */
@Serializable
internal class PackageData(
    /** The ref of this package. */
    val ref: PackageRef,

    /** The first paragraph of the overview documentation for this package. */
    val summary: String? = null,

    /** The deprecation message of this package, or `null` if this package isn't deprecated. */
    val deprecation: String? = null,

    /** The web URL of the source code for this package. */
    val sourceCode: @Contextual URI?,

    /** The source code pattern, with placeholders (e.g. `%{path}`) */
    val sourceCodeUrlScheme: String?,

    /** The dependencies of this package. */
    val dependencies: List<DependencyData> = listOf(),

    /** The modules in this package. */
    val modules: List<ModuleData> = listOf()
) {
    companion object {
        val json = Json { serializersModule = serializers }

        fun read(path: Path): PackageData {
            val jsonStr: String =
                try {
                    path.readString()
                } catch (e: IOException) {
                    throw DocGeneratorException("I/O error reading `$path`.", e)
                }

            return try {
                json.decodeFromString(jsonStr)
            } catch (e: SerializationException) {
                throw DocGeneratorException("Error deserializing `$path`.", e)
            }
        }
    }

    constructor(
        pkg: DocPackage
    ) : this(
        PackageRef(pkg.name, pkg.uri, pkg.version),
        getDocCommentSummary(pkg.overview),
        pkg.docPackageInfo.annotations.deprecation,
        pkg.docPackageInfo.sourceCode,
        pkg.docPackageInfo.sourceCodeUrlScheme,
        pkg.docPackageInfo.dependencies.map {
            DependencyData(PackageRef(it.name, it.uri, it.version))
        },
        pkg.docModules.mapNotNull { if (it.isUnlisted) null else ModuleData(pkg, it) }
    )

    fun write(path: Path) {
        val jsonStr =
            try {
                json.encodeToString(this)
            } catch (e: SerializationException) {
                throw DocGeneratorException("Error serializing `$path`.", e)
            }

        try {
            path.createParentDirectories()
            path.writer().use { it.write(jsonStr) }
        } catch (e: IOException) {
            throw DocGeneratorException("I/O error writing `$path`.", e)
        }
    }
}

/** A package depended upon by [PackageData]. */
@Serializable
internal class DependencyData(
    /** The ref of the depended-on package. */
    val ref: PackageRef
)

/** Persisted data for a module. */
@Serializable
internal class ModuleData(
    /** The ref of this module. */
    val ref: ModuleRef,

    /** The first paragraph of the overview documentation for this module. */
    val summary: String? = null,

    /** The deprecation message, or `null` if this module isn't deprecated. */
    val deprecation: String? = null,

    /** The supermodules of this module, starting from the direct supermodule. */
    @Suppress("unused") val supermodules: List<ModuleRef> = listOf(),

    /** The class of this module, or `null` if this module amends another module. */
    val moduleClass: ClassData? = null,

    /** The classes declared in this module. */
    val classes: List<ClassData> = listOf(),

    /** The type aliases declared in this module. */
    val typeAliases: List<TypeAliasData> = listOf()
) {
    constructor(
        pkg: DocPackage,
        module: DocModule
    ) : this(
        ModuleRef(pkg.name, pkg.uri, pkg.version, module.path),
        getDocCommentSummary(module.overview),
        module.schema.annotations.deprecation,
        generateSequence(module.schema.supermodule) { it.supermodule }
            .map { pkg.docPackageInfo.getModuleRef(module.name) }
            .filterNotNull()
            .toList(),
        if (module.schema.isAmend) null else ClassData(pkg, module, module.schema.moduleClass),
        module.schema.classes.mapNotNull {
            if (it.value.isUnlisted) null else ClassData(pkg, module, it.value)
        },
        module.schema.typeAliases.mapNotNull {
            if (it.value.isUnlisted) null else TypeAliasData(pkg, module, it.value)
        }
    )
}

/** Persisted data for a class or type alias. */
@Serializable
internal sealed class TypeData {
    /** The ref of this type. */
    abstract val ref: TypeRef

    /**
     * The classes (including module classes) used in the API of this type. Standard library classes
     * are not included.
     */
    abstract val usedTypes: List<TypeRef>
}

/** Persisted data for a class. */
@Serializable
internal class ClassData(
    override val ref: TypeRef,

    /**
     * The superclasses of this class, starting from the direct superclass. Every class except
     * `pkl.Any` has a superclass.
     */
    val superclasses: List<TypeRef> = listOf(),
    override val usedTypes: List<TypeRef> = listOf(),
) : TypeData() {
    constructor(
        pkg: DocPackage,
        module: DocModule,
        clazz: PClass
    ) : this(
        TypeRef(pkg.name, pkg.uri, pkg.version, module.path, clazz.simpleName),
        generateSequence(clazz.superclass) { it.superclass }
            .map { pkg.docPackageInfo.getTypeRef(it) }
            .filterNotNull()
            .toList(),
        findTypesUsedBy(clazz, pkg.docPackageInfo).toList()
    )
}

/** Persisted data for a type alias. */
@Serializable
internal class TypeAliasData(
    /** The ref of this type alias. */
    override val ref: TypeRef,

    /** The types used by this type alias. */
    override val usedTypes: List<TypeRef> = listOf()
) : TypeData() {
    constructor(
        pkg: DocPackage,
        module: DocModule,
        alias: TypeAlias
    ) : this(
        TypeRef(pkg.name, pkg.uri, pkg.version, module.path, alias.simpleName, isTypeAlias = true),
        findTypesUsedBy(alias, pkg.docPackageInfo).toList()
    )
}

private fun findTypesUsedBy(clazz: PClass, enclosingPackage: DocPackageInfo): Set<TypeRef> {
    val result = mutableSetOf<TypeRef>()
    clazz.supertype?.let { supertype ->
        for (typeArgument in supertype.typeArguments) {
            findTypesUsedBy(typeArgument, clazz, enclosingPackage, result)
        }
    }
    for ((_, property) in clazz.properties) {
        findTypesUsedBy(property.type, clazz, enclosingPackage, result)
    }
    for ((_, method) in clazz.methods) {
        for ((_, type) in method.parameters) {
            findTypesUsedBy(type, clazz, enclosingPackage, result)
        }
        findTypesUsedBy(method.returnType, clazz, enclosingPackage, result)
    }

    return result
}

private fun findTypesUsedBy(alias: TypeAlias, enclosingPackage: DocPackageInfo): Set<TypeRef> {
    val result = mutableSetOf<TypeRef>()
    findTypesUsedBy(alias.aliasedType, alias, enclosingPackage, result)
    return result
}

private fun findTypesUsedBy(
    type: PType,
    enclosingType: Member /* PClass|TypeAlias */,
    enclosingPackage: DocPackageInfo,
    result: MutableSet<TypeRef>
) {
    when (type) {
        is PType.Class -> {
            val target = type.pClass
            if (!target.isStandardLibraryMember && !target.isUnlisted && target != enclosingType) {
                enclosingPackage.getTypeRef(target)?.let { result.add(it) }
            }
            for (typeArgument in type.typeArguments) {
                findTypesUsedBy(typeArgument, enclosingType, enclosingPackage, result)
            }
        }
        is PType.Alias -> {
            val target = type.typeAlias
            if (!target.isStandardLibraryMember && !target.isUnlisted && target != enclosingType) {
                enclosingPackage.getTypeRef(target)?.let { result.add(it) }
            }
        }
        is PType.Constrained -> {
            findTypesUsedBy(type.baseType, enclosingType, enclosingPackage, result)
        }
        is PType.Function -> {
            for (parameterType in type.parameterTypes) {
                findTypesUsedBy(parameterType, enclosingType, enclosingPackage, result)
            }
            findTypesUsedBy(type.returnType, enclosingType, enclosingPackage, result)
        }
        PType.MODULE -> {
            if (enclosingType.simpleName != PClassInfo.MODULE_CLASS_NAME) {
                result.add(
                    TypeRef(
                        enclosingPackage.name,
                        enclosingPackage.uri,
                        enclosingPackage.version,
                        enclosingType.moduleName
                            .substring(enclosingPackage.name.length + 1)
                            .replace('.', '/'),
                        PClassInfo.MODULE_CLASS_NAME
                    )
                )
            }
        }
        PType.NOTHING -> {}
        is PType.Nullable -> {
            findTypesUsedBy(type.baseType, enclosingType, enclosingPackage, result)
        }
        is PType.Union -> {
            for (elementType in type.elementTypes) {
                findTypesUsedBy(elementType, enclosingType, enclosingPackage, result)
            }
        }
        is PType.StringLiteral -> {}
        is PType.TypeVariable -> {}
        PType.UNKNOWN -> {}
        else -> {
            throw AssertionError("Unknown PType: $type")
        }
    }
}

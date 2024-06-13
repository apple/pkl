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

import java.net.URI
import java.nio.file.Path
import org.pkl.commons.toUri
import org.pkl.core.*
import org.pkl.core.Member.SourceLocation
import org.pkl.core.util.IoUtils

/**
 * A lexical scope that can be the source or target of a doc link (that is, a link to a Pkl member
 * embedded in a doc comment). Used to resolve doc links as well as class names in type signatures.
 * Scoping rules conform to the Pkl language as much as possible.
 *
 * Implementation note: equals and hashCode implementations are based on identity comparisons of
 * underlying [ModuleSchema] types.
 */
internal sealed class DocScope {
  abstract val url: URI

  abstract val parent: DocScope?

  val siteScope: SiteScope? by lazy {
    var scope = this
    while (scope !is SiteScope) {
      scope = scope.parent ?: return@lazy null
    }
    scope
  }

  private val packageScope: PackageScope? by lazy {
    var scope = this
    while (scope !is PackageScope) {
      scope = scope.parent ?: return@lazy null
    }
    scope
  }

  val relativeSiteUrl: URI by lazy { siteScope!!.urlRelativeTo(this) }

  val relativePackageUrl: URI by lazy { packageScope!!.urlRelativeTo(this) }

  fun urlRelativeTo(other: DocScope): URI = IoUtils.relativize(url, other.url)

  /** Looks up the method with the given name in the program element associated with this scope. */
  abstract fun getMethod(name: String): MethodScope?

  /**
   * Looks up the property or class with the given name in the program element associated with this
   * scope.
   */
  abstract fun getProperty(name: String): DocScope?

  abstract fun resolveModuleNameToDocUrl(name: String): URI?

  fun resolveModuleNameToRelativeDocUrl(name: String): URI? =
    resolveModuleNameToDocUrl(name)?.let { IoUtils.relativize(it, url) }

  abstract fun resolveModuleNameToSourceUrl(name: String, sourceLocation: SourceLocation): URI?

  /** Resolves the given method name relative to this scope. */
  abstract fun resolveMethod(name: String): MethodScope?

  /**
   * Resolves the given property name, class name, type alias name, method parameter name, or type
   * parameter name relative to this scope.
   */
  abstract fun resolveVariable(name: String): DocScope?

  /**
   * Resolves a doc link such as `someImport.SomeClass` or `SomeClass.someMethod()` originating in
   * this scope.
   */
  fun resolveDocLink(text: String): DocScope? {
    var currScope: DocScope = this

    val parts = text.split('.')
    if (parts.isEmpty()) return null

    val first = parts[0]
    currScope =
      when {
        first.endsWith("()") -> currScope.resolveMethod(first.dropLast(2)) ?: return null
        else -> currScope.resolveVariable(first) ?: return null
      }

    for (i in 1..parts.lastIndex) {
      val part = parts[i]
      currScope =
        when {
          part.endsWith("()") -> currScope.getMethod(part.dropLast(2)) ?: return null
          else -> currScope.getProperty(part) ?: return null
        }
    }

    return currScope
  }

  override fun toString() = "${this::class.java.simpleName} { url=$url }"
}

/** A scope that corresponds to an entire Pkldoc page. */
internal abstract class PageScope : DocScope() {
  /** The location of the runtime data file for this page. */
  abstract val dataUrl: URI
}

// equality is identity
internal class SiteScope(
  docPackages: List<DocPackage>,
  private val overviewImports: Map<String, URI>,
  private val importResolver: (URI) -> ModuleSchema,
  outputDir: Path
) : PageScope() {
  private val pklVersion = Release.current().version().withBuild(null).toString()

  private val pklBaseModule: ModuleSchema by lazy { importResolver("pkl:base".toUri()) }

  val packageScopes: Map<String, PackageScope> by lazy {
    docPackages.associate { docPackage ->
      docPackage.name to
        PackageScope(
          docPackage.docPackageInfo,
          docPackage.docModules.map { it.schema },
          pklBaseModule,
          docPackage.docPackageInfo.overviewImports,
          this
        )
    }
  }

  private val pklBaseScope: ModuleScope by lazy {
    ModuleScope(pklBaseModule, resolveModuleNameToDocUrl("pkl.base")!!, null)
  }

  override val parent: DocScope?
    get() = null

  override val url: URI by lazy {
    IoUtils.ensurePathEndsWithSlash(outputDir.toUri()).resolve("index.html")
  }

  override val dataUrl: URI
    get() = throw UnsupportedOperationException("perVersionDataUrl")

  fun createEmptyPackageScope(
    name: String,
    version: String,
    sourceCodeUrlScheme: String?,
    sourceCode: URI?
  ): PackageScope =
    PackageScope(
      DocPackageInfo(
        name = name,
        version = version,
        sourceCode = sourceCode,
        sourceCodeUrlScheme = sourceCodeUrlScheme,
        authors = emptyList(),
        extraAttributes = emptyMap(),
        importUri = "",
        issueTracker = null,
        overview = null,
        uri = null
      ),
      emptyList(),
      pklBaseModule,
      emptyMap(),
      this
    )

  override fun getMethod(name: String): MethodScope? = null

  override fun getProperty(name: String): DocScope? = null

  fun getPackage(name: String): PackageScope = packageScopes.getValue(name)

  fun resolveImport(uri: URI): ModuleSchema = importResolver(uri)

  override fun resolveModuleNameToDocUrl(name: String): URI? =
    when {
      name.startsWith("pkl.") -> {
        val packagePage =
          packageScopes["pkl"]?.url // link to locally generated stdlib docs if available
           ?: PklInfo.current().packageIndex.getPackagePage("pkl", pklVersion).toUri()
        packagePage.resolve(name.substring(4) + "/")
      }
      // doesn't make much sense to search in [packageScopes]
      // because we don't know the requested module version
      else -> null
    }

  override fun resolveModuleNameToSourceUrl(name: String, sourceLocation: SourceLocation): URI? =
    when {
      name.startsWith("pkl.") -> {
        val path = "/stdlib/${name.substring(4)}.pkl"
        Release.current()
          .sourceCode()
          .sourceCodeUrlScheme
          .replaceSourceCodePlaceholders(path, sourceLocation)
          .toUri()
      }
      // doesn't make much sense to search in [packageScopes]
      // because we don't know the requested module version
      else -> null
    }

  // used to resolve Pkldoc links in docsite-info.pkl
  override fun resolveMethod(name: String): MethodScope? = pklBaseScope.getMethod(name)

  // used to resolve Pkldoc links in docsite-info.pkl
  override fun resolveVariable(name: String): DocScope? =
    overviewImports[name]?.let { uri ->
      val mod = resolveImport(uri)
      resolveModuleNameToDocUrl(mod.moduleName)?.let { url -> ModuleScope(mod, url, null) }
    }
      ?: pklBaseScope.getProperty(name)
}

internal class PackageScope(
  val docPackageInfo: DocPackageInfo,
  modules: List<ModuleSchema>,
  pklBaseModule: ModuleSchema,
  private val overviewImports: Map<String, URI>,
  override val parent: SiteScope
) : PageScope() {
  val name = docPackageInfo.name

  val version = docPackageInfo.version

  private val modulePrefix = docPackageInfo.moduleNamePrefix

  private val moduleScopes: Map<String, ModuleScope> by lazy {
    modules.associate { module ->
      val docUrl =
        url.resolve(
          getModulePath(module.moduleName, modulePrefix).pathEncoded.uriEncoded + "/index.html"
        )
      module.moduleName to ModuleScope(module, docUrl, this)
    }
  }

  private val pklBaseScope: ModuleScope by lazy {
    ModuleScope(pklBaseModule, resolveModuleNameToDocUrl("pkl.base")!!, null)
  }

  override val url: URI by lazy { parent.url.resolve("./${name.pathEncoded}/$version/index.html") }

  override val dataUrl: URI by lazy {
    parent.url.resolve("./data/${name.pathEncoded}/$version/index.js")
  }

  fun getModule(name: String): ModuleScope = moduleScopes.getValue(name)

  fun getPklBaseMethod(name: String): MethodScope? = pklBaseScope.getMethod(name)

  fun getPklBaseProperty(name: String): DocScope? = pklBaseScope.getProperty(name)

  override fun getMethod(name: String): MethodScope? = null

  override fun getProperty(name: String): DocScope? = null

  fun resolveImport(uri: URI): ModuleSchema = parent.resolveImport(uri)

  override fun resolveModuleNameToDocUrl(name: String): URI? {
    moduleScopes[name]?.url?.let {
      return it
    }
    for (dependency in docPackageInfo.dependencies) {
      dependency.getModuleDocUrl(name)?.let {
        return parent.url.resolve(it)
      }
    }
    return parent.resolveModuleNameToDocUrl(name)
  }

  override fun resolveModuleNameToSourceUrl(name: String, sourceLocation: SourceLocation): URI? {
    for (dependency in docPackageInfo.dependencies) {
      dependency.getModuleSourceCodeWithSourceLocation(name, sourceLocation)?.let {
        return it
      }
    }
    return parent.resolveModuleNameToSourceUrl(name, sourceLocation)
  }

  // used to resolve Pkldoc links in package-info.pkl
  override fun resolveMethod(name: String): MethodScope? = getPklBaseMethod(name)

  // used to resolve Pkldoc links in package-info.pkl
  override fun resolveVariable(name: String): DocScope? =
    overviewImports[name]?.let { uri ->
      val mod = resolveImport(uri)
      resolveModuleNameToDocUrl(mod.moduleName)?.let { url -> ModuleScope(mod, url, null) }
    }
      ?: getPklBaseProperty(name)

  override fun equals(other: Any?): Boolean =
    other is PackageScope && docPackageInfo.name == other.docPackageInfo.name

  override fun hashCode(): Int =
    PackageScope::class.hashCode() * 31 + docPackageInfo.name.hashCode()
}

internal class ModuleScope(
  val module: ModuleSchema,
  override val url: URI,
  override val parent: PackageScope?
) : PageScope() {
  val name: String
    get() = module.moduleName

  val path: String by lazy {
    getModulePath(module.moduleName, parent!!.docPackageInfo.moduleNamePrefix).uriEncoded
  }

  override val dataUrl: URI by lazy { parent!!.dataUrl.resolve("./$path/index.js") }

  override fun getMethod(name: String): MethodScope? =
    module.moduleClass.allMethods[name]?.let { MethodScope(it, this) }

  override fun getProperty(name: String): DocScope? =
    module.moduleClass.allProperties[name]?.let { PropertyScope(it, this) }
      ?: module.allClasses[name]?.let { ClassScope(it, url, this) }
        ?: module.allTypeAliases[name]?.let { TypeAliasScope(it, url, this) }

  private fun resolveImport(uri: URI): ModuleSchema = parent!!.resolveImport(uri)

  override fun resolveModuleNameToDocUrl(name: String): URI? =
    when (name) {
      module.moduleName -> url
      else -> parent!!.resolveModuleNameToDocUrl(name)
    }

  override fun resolveModuleNameToSourceUrl(name: String, sourceLocation: SourceLocation): URI? =
    when (name) {
      module.moduleName ->
        parent!!
          .docPackageInfo
          .sourceCodeUrlScheme
          ?.replaceSourceCodePlaceholders("/$path.pkl", sourceLocation)
          ?.toUri()
      else -> parent!!.resolveModuleNameToSourceUrl(name, sourceLocation)
    }

  override fun resolveMethod(name: String): MethodScope? =
    module.moduleClass.methods[name]?.let { MethodScope(it, this) }
      ?: parent!!.getPklBaseMethod(name) ?: getMethod(name)

  override fun resolveVariable(name: String): DocScope? =
    name.takeIf { it == "module" }?.let { this }
      ?: module.imports[name]?.let { uri ->
        val mod = resolveImport(uri)
        resolveModuleNameToDocUrl(mod.moduleName)?.let { url -> ModuleScope(mod, url, null) }
      }
        ?: module.moduleClass.properties[name]?.let { PropertyScope(it, this) }
      // inherited classes/type aliases are in scope when resolving types -> search `all`
      ?: module.allClasses[name]?.let { ClassScope(it, url, this) }
        ?: module.allTypeAliases[name]?.let { TypeAliasScope(it, url, this) }
        ?: parent!!.getPklBaseProperty(name) ?: getProperty(name)

  override fun equals(other: Any?): Boolean = other is ModuleScope && module == other.module

  override fun hashCode(): Int = module.hashCode()
}

internal class ClassScope(
  val clazz: PClass,
  private val parentUrl: URI,
  override val parent: ModuleScope?
) : PageScope() {
  override val url: URI by lazy {
    // `isModuleClass` distinction is relevant when this scope is a link target
    if (clazz.isModuleClass) parentUrl
    else parentUrl.resolve("${clazz.simpleName.pathEncoded.uriEncodedComponent}.html")
  }

  override val dataUrl: URI by lazy {
    parent!!.dataUrl.resolve("${clazz.simpleName.pathEncoded.uriEncodedComponent}.js")
  }

  override fun getMethod(name: String): MethodScope? =
    clazz.allMethods[name]?.let { MethodScope(it, this) }

  override fun getProperty(name: String): DocScope? =
    clazz.allProperties[name]?.let { PropertyScope(it, this) }

  override fun resolveModuleNameToDocUrl(name: String): URI? =
    parent!!.resolveModuleNameToDocUrl(name)

  override fun resolveModuleNameToSourceUrl(name: String, sourceLocation: SourceLocation): URI? =
    parent!!.resolveModuleNameToSourceUrl(name, sourceLocation)

  override fun resolveMethod(name: String): MethodScope? =
    clazz.methods[name]?.let { MethodScope(it, this) }
      ?: parent!!.resolveMethod(name) ?: getMethod(name)

  override fun resolveVariable(name: String): DocScope? =
    clazz.typeParameters.find { it.name == name }?.let { ParameterScope(name, this) }
      ?: clazz.properties[name]?.let { PropertyScope(it, this) } ?: parent!!.resolveVariable(name)
        ?: clazz.allProperties[name]?.let { PropertyScope(it, this) }

  override fun equals(other: Any?): Boolean = other is ClassScope && clazz == other.clazz

  override fun hashCode(): Int = clazz.hashCode()
}

internal class TypeAliasScope(
  val typeAlias: TypeAlias,
  private val parentDocUrl: URI,
  override val parent: ModuleScope?
) : DocScope() {
  override val url: URI
    get() = parentDocUrl.resolve("#${typeAlias.simpleName}")

  override fun getMethod(name: String): MethodScope? = parent?.getMethod(name)

  override fun getProperty(name: String): DocScope? = parent?.getProperty(name)

  override fun resolveModuleNameToDocUrl(name: String) =
    // only used for page scopes
    throw UnsupportedOperationException("resolveModuleNameToDocUrl")

  override fun resolveModuleNameToSourceUrl(name: String, sourceLocation: SourceLocation) =
    // only used for page scopes
    throw UnsupportedOperationException("resolveModuleNameToSourceUrl")

  override fun resolveMethod(name: String): MethodScope? = parent?.resolveMethod(name)

  override fun resolveVariable(name: String): DocScope? = parent?.resolveVariable(name)

  override fun equals(other: Any?): Boolean =
    other is TypeAliasScope && typeAlias == other.typeAlias

  override fun hashCode(): Int = typeAlias.hashCode()
}

internal class MethodScope(val method: PClass.Method, override val parent: DocScope) : DocScope() {
  override val url: URI
    get() = parent.url.resolve("#${method.simpleName}()")

  override fun getMethod(name: String): MethodScope? = null

  override fun getProperty(name: String): DocScope? = null

  override fun resolveModuleNameToDocUrl(name: String) =
    // only used for page scopes
    throw UnsupportedOperationException("resolveModuleNameToDocUrl")

  override fun resolveModuleNameToSourceUrl(name: String, sourceLocation: SourceLocation) =
    // only used for page scopes
    throw UnsupportedOperationException("resolveModuleNameToSourceUrl")

  override fun resolveMethod(name: String): MethodScope? = parent.resolveMethod(name)

  override fun resolveVariable(name: String): DocScope? =
    method.typeParameters.find { it.name == name }?.let { ParameterScope(name, this) }
      ?: method.parameters[name]?.let { ParameterScope(name, this) } ?: parent.resolveVariable(name)

  override fun equals(other: Any?): Boolean = other is MethodScope && method == other.method

  override fun hashCode(): Int = method.hashCode()
}

internal class PropertyScope(
  val property: PClass.Property,
  override val parent: DocScope // ModuleScope|ClassScope
) : DocScope() {
  override val url: URI
    get() = parent.url.resolve("#${property.simpleName}")

  override fun getProperty(name: String): DocScope? = null

  override fun getMethod(name: String): MethodScope? = null

  override fun resolveModuleNameToDocUrl(name: String) =
    // only used for page scopes
    throw UnsupportedOperationException("resolveModuleNameToDocUrl")

  override fun resolveModuleNameToSourceUrl(name: String, sourceLocation: SourceLocation) =
    // only used for page scopes
    throw UnsupportedOperationException("resolveModuleNameToSourceUrl")

  override fun resolveMethod(name: String): MethodScope? = parent.resolveMethod(name)

  override fun resolveVariable(name: String): DocScope? = parent.resolveVariable(name)

  override fun equals(other: Any?): Boolean = other is PropertyScope && property == other.property

  override fun hashCode(): Int = property.hashCode()
}

/** A method parameter or type parameter. */
internal class ParameterScope(val name: String, override val parent: DocScope) : DocScope() {
  override val url: URI
    get() =
      if (parent is ClassScope) {
        parent.url.resolve("#$name")
      } else {
        "${parent.url}.$name".toUri()
      }

  override fun getMethod(name: String): MethodScope? = null

  override fun getProperty(name: String): DocScope? = null

  override fun resolveModuleNameToDocUrl(name: String) =
    // only used for page scopes
    throw UnsupportedOperationException("resolveModuleNameToDocUrl")

  override fun resolveModuleNameToSourceUrl(name: String, sourceLocation: SourceLocation) =
    // only used for page scopes
    throw UnsupportedOperationException("resolveModuleNameToSourceUrl")

  override fun resolveMethod(name: String): MethodScope? = parent.resolveMethod(name)

  override fun resolveVariable(name: String): DocScope? = parent.resolveVariable(name)

  override fun equals(other: Any?): Boolean =
    other is ParameterScope && name == other.name && parent == other.parent

  override fun hashCode(): Int = name.hashCode() * 31 + parent.hashCode()
}

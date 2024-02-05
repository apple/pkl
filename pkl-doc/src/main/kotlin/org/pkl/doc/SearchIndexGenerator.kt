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

import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import org.pkl.commons.createParentDirectories
import org.pkl.core.Member
import org.pkl.core.PClass.Method
import org.pkl.core.PClass.Property
import org.pkl.core.PClassInfo
import org.pkl.core.PType
import org.pkl.core.util.json.JsonWriter

internal class SearchIndexGenerator(private val outputDir: Path) {
    fun generateSiteIndex(packagesData: List<PackageData>) {
        val path = outputDir.resolve("search-index.js").createParentDirectories()
        path.jsonWriter().use { writer ->
            writer.apply {
                // provide data as JSON string rather than JS literal (more flexible and secure)
                rawText("searchData='")
                array {
                    for (pkg in packagesData) {
                        val pkgPath = "${pkg.ref.pkg}/current"
                        obj {
                            name("name").value(pkg.ref.pkg)
                            name("kind").value(0)
                            name("url").value("$pkgPath/index.html")
                            if (pkg.deprecation != null) {
                                name("deprecated").value(true)
                            }
                        }

                        for (module in pkg.modules) {
                            obj {
                                name("name").value(module.ref.fullName)
                                name("kind").value(1)
                                name("url").value("$pkgPath/${module.ref.module}/index.html")
                                if (module.deprecation != null) {
                                    name("deprecated").value(true)
                                }
                            }
                        }
                    }
                }
                rawText("';\n")
            }
        }
    }

    fun generate(docPackage: DocPackage) {
        val path =
            outputDir
                .resolve("${docPackage.name}/${docPackage.version}/search-index.js")
                .createParentDirectories()
        JsonWriter(path.bufferedWriter()).use { writer ->
            writer.apply {
                serializeNulls = false
                // provide data as JSON string rather than JS literal (more flexible and secure)
                rawText("searchData='")
                var nextId = 0
                array {
                    for (docModule in docPackage.docModules) {
                        if (docModule.isUnlisted) continue

                        val module = docModule.schema
                        val moduleId = nextId

                        nextId += 1
                        obj {
                            name("name").value(module.moduleName)
                            name("kind").value(1)
                            name("url").value("${docModule.path}/index.html")
                            writeAnnotations(module.moduleClass)
                        }

                        for ((propertyName, property) in module.moduleClass.properties) {
                            if (property.isUnlisted) continue

                            nextId += 1
                            obj {
                                name("name").value(propertyName)
                                name("kind").value(5)
                                name("url").value("${docModule.path}/index.html#$propertyName")
                                name("sig").value(renderSignature(property))
                                name("parId").value(moduleId)
                                writeAnnotations(property)
                            }
                        }

                        for ((methodName, method) in module.moduleClass.methods) {
                            if (method.isUnlisted) continue

                            nextId += 1
                            obj {
                                name("name").value(methodName)
                                name("kind").value(4)
                                name("url").value("${docModule.path}/index.html#$methodName()")
                                name("sig").value(renderSignature(method))
                                name("parId").value(moduleId)
                                writeAnnotations(method)
                            }
                        }

                        for ((className, clazz) in module.classes) {
                            if (clazz.isUnlisted) continue

                            val classId = nextId

                            nextId += 1
                            obj {
                                name("name").value(className)
                                name("kind").value(3)
                                name("url").value("${docModule.path}/$className.html")
                                name("parId").value(moduleId)
                                writeAnnotations(clazz)
                            }

                            for ((propertyName, property) in clazz.properties) {
                                if (property.isUnlisted) continue

                                nextId += 1
                                obj {
                                    name("name").value(propertyName)
                                    name("kind").value(5)
                                    name("url")
                                        .value("${docModule.path}/$className.html#$propertyName")
                                    name("sig").value(renderSignature(property))
                                    name("parId").value(classId)
                                    writeAnnotations(property)
                                }
                            }

                            for ((methodName, method) in clazz.methods) {
                                if (method.isUnlisted) continue

                                nextId += 1
                                obj {
                                    name("name").value(methodName)
                                    name("kind").value(4)
                                    name("url")
                                        .value("${docModule.path}/$className.html#$methodName()")
                                    name("sig").value(renderSignature(method))
                                    name("parId").value(classId)
                                    writeAnnotations(method)
                                }
                            }
                        }

                        for ((typeAliasName, typeAlias) in module.typeAliases) {
                            if (typeAlias.isUnlisted) continue

                            nextId += 1
                            obj {
                                name("name").value(typeAliasName)
                                name("kind").value(2)
                                name("url").value("${docModule.path}/index.html#$typeAliasName")
                                name("parId").value(moduleId)
                                writeAnnotations(typeAlias)
                            }
                        }
                    }
                }
                rawText("';\n")
            }
        }
    }

    private fun renderSignature(method: Method): String =
        StringBuilder()
            .apply {
                append('(')
                var first = true
                for ((name, _) in method.parameters) {
                    if (first) first = false else append(", ")
                    append(name)
                }
                append(')')
                append(": ")
                appendType(method.returnType)
            }
            .toString()

    private fun renderSignature(property: Property): String =
        StringBuilder()
            .apply {
                append(": ")
                appendType(property.type)
            }
            .toString()

    private fun StringBuilder.appendType(type: PType) {
        when (type) {
            PType.UNKNOWN -> {
                append("unknown")
            }
            PType.NOTHING -> {
                append("nothing")
            }
            PType.MODULE -> {
                append("module")
            }
            is PType.StringLiteral -> {
                append("\\\"${type.literal}\\\"")
            }
            is PType.Class -> {
                val pClass = type.pClass
                val name =
                    if (pClass.isModuleClass) {
                        // use simple module name rather than class name (which is always
                        // `ModuleClass`)
                        pClass.moduleName.substring(pClass.moduleName.lastIndexOf('.') + 1)
                    } else {
                        pClass.simpleName
                    }
                append(name)
                if (type.typeArguments.isNotEmpty()) {
                    append('<')
                    var first = true
                    for (typeArg in type.typeArguments) {
                        if (first) first = false else append(", ")
                        appendType(typeArg)
                    }
                    append('>')
                }
            }
            is PType.Constrained -> appendType(type.baseType)
            is PType.Union -> {
                var first = true
                for (elemType in type.elementTypes) {
                    if (first) first = false else append("|")
                    appendType(elemType)
                }
            }
            is PType.Nullable -> {
                appendType(type.baseType)
                append("?")
            }
            is PType.Function -> {
                if (type.parameterTypes.size == 1) {
                    appendType(type.parameterTypes[0])
                } else {
                    append('(')
                    var first = true
                    for (paramType in type.parameterTypes) {
                        if (first) first = false else append(", ")
                        appendType(paramType)
                    }
                    append(')')
                }

                append(" -> ")
                appendType(type.returnType)
            }
            is PType.Alias -> append(type.typeAlias.simpleName)
            is PType.TypeVariable -> append(type.typeParameter.name)
            else -> throw AssertionError("Unknown PType: $type")
        }
    }

    private fun JsonWriter.writeAnnotations(member: Member?): JsonWriter {
        if (member == null) return this

        if (member.annotations.any { it.classInfo == PClassInfo.Deprecated }) {
            name("deprecated")
            value(true)
        }

        member.annotations
            .find { it.classInfo == PClassInfo.AlsoKnownAs }
            ?.let { alsoKnownAs ->
                name("aka")
                array {
                    @Suppress("UNCHECKED_CAST")
                    for (name in alsoKnownAs["names"] as List<String>) value(name)
                }
            }

        return this
    }
}

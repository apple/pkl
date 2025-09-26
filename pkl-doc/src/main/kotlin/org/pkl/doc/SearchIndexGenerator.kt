/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.isRegularFile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.pkl.commons.readString
import org.pkl.commons.writeString
import org.pkl.core.Member
import org.pkl.core.PClass
import org.pkl.core.PClass.Method
import org.pkl.core.PClass.Property
import org.pkl.core.PClassInfo
import org.pkl.core.PType
import org.pkl.core.TypeAlias

@OptIn(ExperimentalSerializationApi::class)
internal class SearchIndexGenerator(private val outputDir: Path, consoleOut: OutputStream) :
  AbstractGenerator(consoleOut) {
  companion object {
    private const val PREFIX = "searchData='"
    private const val POSTFIX = "';\n"

    val json = Json {
      prettyPrint = false
      explicitNulls = false
    }
  }

  private object KindSerializer : KSerializer<Kind> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Kind", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Kind) {
      encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): Kind {
      val intValue = decoder.decodeInt()
      return Kind.fromInt(intValue)
    }
  }

  @Serializable(with = KindSerializer::class)
  enum class Kind(val value: Int) {
    PACKAGE(0),
    MODULE(1),
    TYPEALIAS(2),
    CLASS(3),
    METHOD(4),
    PROPERTY(5);

    companion object {
      fun fromInt(value: Int) =
        entries.firstOrNull { it.value == value }
          ?: throw IllegalArgumentException("Unknown Kind value: $value")
    }
  }

  private val searchIndexFile = outputDir.resolve("search-index.js")

  @Serializable
  data class SearchIndexEntry(
    val name: String,
    val kind: Kind,
    val url: String,
    val sig: String? = null,
    val parId: Int? = null,
    val deprecated: Boolean? = null,
    val aka: List<String>? = null,
  )

  data class PackageIndexEntry(
    val packageEntry: SearchIndexEntry,
    val moduleEntries: List<SearchIndexEntry>,
  )

  private fun List<SearchIndexEntry>.writeTo(path: Path) {
    val self = this
    val text = buildString {
      append(PREFIX)
      append(json.encodeToString(self))
      append(POSTFIX)
    }
    path.writeString(text)
    writeOutput("Wrote file ${path.toUri()}\r")
  }

  private fun PackageData.toEntry(): SearchIndexEntry {
    val pkgPath = "${ref.pkg.pathEncoded}/current"
    return SearchIndexEntry(
      name = ref.pkg,
      kind = Kind.PACKAGE,
      url = "$pkgPath/${ref.packageRelativeHtmlPath}",
      deprecated = deprecation?.let { true },
    )
  }

  private fun ModuleData.toEntry(basePath: String): SearchIndexEntry {
    return SearchIndexEntry(
      name = ref.fullName,
      kind = Kind.MODULE,
      url = "$basePath/${ref.packageRelativeHtmlPath}",
      deprecated = deprecation?.let { true },
    )
  }

  private fun DocModule.toEntry(): SearchIndexEntry {
    val moduleSchema = schema
    return SearchIndexEntry(
        name = moduleSchema.moduleName,
        kind = Kind.MODULE,
        url = "$path/index.html",
      )
      .withAnnotations(moduleSchema.moduleClass)
  }

  private fun Property.toEntry(parentId: Int, basePath: String): SearchIndexEntry {
    return SearchIndexEntry(
        name = simpleName,
        kind = Kind.PROPERTY,
        url = "$basePath#$simpleName",
        sig = renderSignature(this),
        parId = parentId,
      )
      .withAnnotations(this)
  }

  private fun Method.toEntry(parentId: Int, basePath: String): SearchIndexEntry {
    return SearchIndexEntry(
        name = simpleName,
        kind = Kind.METHOD,
        url = "$basePath#${simpleName.pathEncoded}()",
        sig = renderSignature(this),
        parId = parentId,
      )
      .withAnnotations(this)
  }

  private fun PClass.toEntry(parentId: Int, basePath: String): SearchIndexEntry {
    return SearchIndexEntry(
        name = simpleName,
        kind = Kind.CLASS,
        url = "$basePath/${simpleName.pathEncoded}.html",
        parId = parentId,
      )
      .withAnnotations(this)
  }

  private fun TypeAlias.toEntry(parentId: Int, basePath: String): SearchIndexEntry {
    return SearchIndexEntry(
        name = simpleName,
        kind = Kind.TYPEALIAS,
        url = "$basePath#${simpleName.pathEncoded}",
        parId = parentId,
      )
      .withAnnotations(this)
  }

  @Suppress("UNCHECKED_CAST")
  private fun SearchIndexEntry.withAnnotations(member: Member?): SearchIndexEntry {
    if (member == null) return this
    val deprecatedAnnotation = member.annotations.find { it.classInfo == PClassInfo.Deprecated }
    val alsoKnownAs = member.annotations.find { it.classInfo == PClassInfo.AlsoKnownAs }
    return copy(
      deprecated = deprecatedAnnotation?.let { true },
      aka = alsoKnownAs?.let { it["names"] as List<String> },
    )
  }

  internal fun getCurrentSearchIndex(): List<PackageIndexEntry> {
    if (!searchIndexFile.isRegularFile()) {
      return emptyList()
    }
    val text = searchIndexFile.readString()
    if (!(text.startsWith(PREFIX) && text.endsWith(POSTFIX))) {
      writeOutputLine(
        "[error] Incorrect existing search-index.js; either doesnt start with prefix '$PREFIX', or end with postfix '$POSTFIX'"
      )
      return emptyList()
    }
    val jsonStr = text.substring(PREFIX.length, text.length - POSTFIX.length)
    val entries = json.decodeFromString<List<SearchIndexEntry>>(jsonStr)
    return buildList {
      var i = 0

      while (i < entries.size) {
        val packageEntry = entries[i]
        i++
        val moduleEntries = buildList {
          while (i < entries.size && entries[i].kind == Kind.MODULE) {
            add(entries[i])
            i++
          }
        }
        add(PackageIndexEntry(packageEntry = packageEntry, moduleEntries = moduleEntries))
      }
    }
  }

  fun buildSearchIndex(packages: List<PackageData>): List<PackageIndexEntry> = buildList {
    for (pkg in packages) {
      val pkgPath = "${pkg.ref.pkg.pathEncoded}/current"
      add(
        PackageIndexEntry(
          packageEntry = pkg.toEntry(),
          moduleEntries = pkg.modules.map { it.toEntry(basePath = pkgPath) },
        )
      )
    }
  }

  /** Reads the current site index, and adds the set of newly generated packages to the index. */
  fun generateSiteIndex(currentPackages: List<PackageData>) {
    val searchIndex = buildSearchIndex(currentPackages)
    searchIndexFile.createParentDirectories()
    val entries = buildList {
      for (packageIndexEntry in searchIndex) {
        add(packageIndexEntry.packageEntry)
        for (module in packageIndexEntry.moduleEntries) {
          add(module)
        }
      }
    }
    entries.writeTo(searchIndexFile)
  }

  fun generate(docPackage: DocPackage) {
    val path =
      outputDir
        .resolve("${docPackage.name.pathEncoded}/${docPackage.version}/search-index.js")
        .createParentDirectories()
    val entries = buildList {
      var nextId = 0
      for (docModule in docPackage.docModules) {
        if (docModule.isUnlisted) continue

        val module = docModule.schema
        val moduleId = nextId

        nextId += 1
        add(docModule.toEntry())
        val moduleBasePath = docModule.path

        for ((_, property) in module.moduleClass.properties) {
          if (property.isUnlisted) continue
          nextId += 1
          add(property.toEntry(parentId = moduleId, basePath = "${moduleBasePath}/index.html"))
        }
        for ((_, method) in module.moduleClass.methods) {
          if (method.isUnlisted) continue

          nextId += 1
          add(method.toEntry(parentId = moduleId, basePath = "${moduleBasePath}/index.html"))
        }
        for ((_, clazz) in module.classes) {
          if (clazz.isUnlisted) continue

          val classId = nextId

          nextId += 1
          add(clazz.toEntry(parentId = moduleId, basePath = moduleBasePath))
          val classBasePath = "${docModule.path}/${clazz.simpleName}.html"

          for ((_, property) in clazz.properties) {
            if (property.isUnlisted) continue

            nextId += 1
            add(property.toEntry(parentId = classId, basePath = classBasePath))
          }

          for ((_, method) in clazz.methods) {
            if (method.isUnlisted) continue

            nextId += 1
            add(method.toEntry(parentId = classId, basePath = classBasePath))
          }
        }

        for ((_, typeAlias) in module.typeAliases) {
          nextId += 1
          add(typeAlias.toEntry(parentId = moduleId, basePath = "${moduleBasePath}/index.html"))
        }
      }
    }
    entries.writeTo(path)
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
            // use simple module name rather than class name (which is always `ModuleClass`)
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
}

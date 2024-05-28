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
import org.pkl.commons.deleteRecursively
import org.pkl.core.util.json.JsonWriter

// Note: we don't currently make use of persisted type alias data (needs more thought).
internal class RuntimeDataGenerator(
  private val descendingVersionComparator: Comparator<String>,
  private val outputDir: Path
) {

  private val packageVersions = mutableMapOf<PackageId, MutableSet<String>>()
  private val moduleVersions = mutableMapOf<ModuleId, MutableSet<String>>()
  private val classVersions = mutableMapOf<TypeId, MutableSet<String>>()
  private val packageUsages = mutableMapOf<PackageRef, MutableSet<PackageRef>>()
  private val typeUsages = mutableMapOf<TypeRef, MutableSet<TypeRef>>()
  private val subtypes = mutableMapOf<TypeRef, MutableSet<TypeRef>>()

  fun deleteDataDir() {
    outputDir.resolve("data").deleteRecursively()
  }

  fun generate(packages: List<PackageData>) {
    collectData(packages)
    writeData(packages)
  }

  private fun collectData(packages: List<PackageData>) {
    for (pkg in packages) {
      packageVersions.add(pkg.ref.pkg, pkg.ref.version)
      for (dependency in pkg.dependencies) {
        if (dependency.isStdlib()) continue
        // Every package implicitly depends on the stdlib. Showing this dependency adds unwanted
        // noise.
        packageUsages.add(dependency.ref, pkg.ref)
      }
      for (module in pkg.modules) {
        moduleVersions.add(module.ref.id, module.ref.version)
        if (module.moduleClass != null) {
          collectData(module.moduleClass)
        }
        for (clazz in module.classes) {
          collectData(clazz)
        }
      }
    }
  }

  private fun collectData(clazz: ClassData) {
    classVersions.add(clazz.ref.id, clazz.ref.version)
    for (superclass in clazz.superclasses) {
      subtypes.add(superclass, clazz.ref)
    }
    for (type in clazz.usedTypes) {
      typeUsages.add(type, clazz.ref)
    }
  }

  private fun writeData(packages: List<PackageData>) {
    for (pkg in packages) {
      writePackageFile(pkg.ref)
      for (mod in pkg.modules) {
        writeModuleFile(mod.ref)
        for (clazz in mod.classes) {
          writeClassFile(clazz.ref)
        }
      }
    }
  }

  private fun writePackageFile(ref: PackageRef) {
    outputDir
      .resolve("data/${ref.pkg.pathEncoded}/${ref.version.pathEncoded}/index.js")
      .jsonWriter()
      .use { writer ->
        writer.isLenient = true
        writer.writeLinks(
          HtmlConstants.KNOWN_VERSIONS,
          packageVersions.getOrDefault(ref.pkg, setOf()).sortedWith(descendingVersionComparator),
          { it },
          { if (it == ref.version) null else ref.copy(version = it).pageUrlRelativeTo(ref) },
          { if (it == ref.version) CssConstants.CURRENT_VERSION else null }
        )
        writer.writeLinks(
          HtmlConstants.KNOWN_USAGES,
          packageUsages.getOrDefault(ref, setOf()).packagesWithHighestVersions().sortedBy {
            it.pkg
          },
          PackageRef::pkg,
          { it.pageUrlRelativeTo(ref) },
          { null }
        )
      }
  }

  private fun writeModuleFile(ref: ModuleRef) {
    outputDir
      .resolve(
        "data/${ref.pkg.pathEncoded}/${ref.version.pathEncoded}/${ref.module.pathEncoded}/index.js"
      )
      .jsonWriter()
      .use { writer ->
        writer.isLenient = true
        writer.writeLinks(
          HtmlConstants.KNOWN_VERSIONS,
          moduleVersions.getOrDefault(ref.id, setOf()).sortedWith(descendingVersionComparator),
          { it },
          { if (it == ref.version) null else ref.copy(version = it).pageUrlRelativeTo(ref) },
          { if (it == ref.version) CssConstants.CURRENT_VERSION else null }
        )
        writer.writeLinks(
          HtmlConstants.KNOWN_USAGES,
          typeUsages.getOrDefault(ref.moduleClassRef, setOf()).typesWithHighestVersions().sortedBy {
            it.displayName
          },
          TypeRef::displayName,
          { it.pageUrlRelativeTo(ref) },
          { null }
        )
        writer.writeLinks(
          HtmlConstants.KNOWN_SUBTYPES,
          subtypes.getOrDefault(ref.moduleClassRef, setOf()).typesWithHighestVersions().sortedBy {
            it.displayName
          },
          TypeRef::displayName,
          { it.pageUrlRelativeTo(ref) },
          { null }
        )
      }
  }

  private fun writeClassFile(ref: TypeRef) {
    outputDir
      .resolve(
        "data/${ref.pkg.pathEncoded}/${ref.version.pathEncoded}/${ref.module.pathEncoded}/${ref.type.pathEncoded}.js"
      )
      .jsonWriter()
      .use { writer ->
        writer.isLenient = true
        writer.writeLinks(
          HtmlConstants.KNOWN_VERSIONS,
          classVersions.getOrDefault(ref.id, setOf()).sortedWith(descendingVersionComparator),
          { it },
          { if (it == ref.version) null else ref.copy(version = it).pageUrlRelativeTo(ref) },
          { if (it == ref.version) CssConstants.CURRENT_VERSION else null }
        )
        writer.writeLinks(
          HtmlConstants.KNOWN_USAGES,
          typeUsages.getOrDefault(ref, setOf()).typesWithHighestVersions().sortedBy {
            it.displayName
          },
          TypeRef::displayName,
          { it.pageUrlRelativeTo(ref) },
          { null }
        )
        writer.writeLinks(
          HtmlConstants.KNOWN_SUBTYPES,
          subtypes.getOrDefault(ref, setOf()).typesWithHighestVersions().sortedBy {
            it.displayName
          },
          TypeRef::displayName,
          { it.pageUrlRelativeTo(ref) },
          { null }
        )
      }
  }

  private fun <T> JsonWriter.writeLinks(
    // HTML element ID
    id: String,
    // items based on which links are generated
    items: List<T>,
    // link text
    text: (T) -> String,
    // link href
    href: (T) -> String?,
    // link CSS classes
    classes: (T) -> String?
  ) {
    if (items.isEmpty()) return

    rawText("runtimeData.links('")
    rawText(id)
    rawText("','")

    array {
      for (item in items) {
        obj {
          name("text").value(text(item))
          name("href").value(href(item))
          name("classes").value(classes(item))
        }
      }
    }

    rawText("');\n")
  }

  private fun Set<PackageRef>.packagesWithHighestVersions(): Collection<PackageRef> {
    val highestVersions = mutableMapOf<PackageId, PackageRef>()
    for (ref in this) {
      val prev = highestVersions[ref.pkg]
      if (prev == null || descendingVersionComparator.compare(prev.version, ref.version) > 0) {
        highestVersions[ref.pkg] = ref
      }
    }
    return highestVersions.values
  }

  private fun Set<TypeRef>.typesWithHighestVersions(): Collection<TypeRef> {
    val highestVersions = mutableMapOf<TypeId, TypeRef>()
    for (ref in this) {
      val prev = highestVersions[ref.id]
      if (prev == null || descendingVersionComparator.compare(prev.version, ref.version) > 0) {
        highestVersions[ref.id] = ref
      }
    }
    return highestVersions.values
  }

  private fun <K, V> MutableMap<K, MutableSet<V>>.add(key: K, value: V) {
    val newValue =
      when (val oldValue = this[key]) {
        null -> mutableSetOf(value)
        else -> oldValue.apply { add(value) }
      }
    put(key, newValue)
  }
}

private fun DependencyData.isStdlib(): Boolean = ref.pkg == "pkl"

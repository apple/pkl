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

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.isRegularFile
import kotlinx.serialization.ExperimentalSerializationApi
import org.pkl.doc.RuntimeData.Companion.plus

// Note: we don't currently make use of persisted type alias data (needs more thought).
@OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
internal class RuntimeDataGenerator(
  private val descendingVersionComparator: Comparator<String>,
  private val outputDir: Path,
) {

  private val packageVersions = mutableMapOf<PackageRef, MutableSet<String>>()
  private val moduleVersions = mutableMapOf<ModuleRef, MutableSet<String>>()
  private val classVersions = mutableMapOf<TypeRef, MutableSet<String>>()
  private val packageUsages = mutableMapOf<PackageRef, MutableSet<PackageRef>>()
  private val subtypes = mutableMapOf<TypeRef, MutableSet<TypeRef>>()

  fun generate(packages: List<PackageData>) {
    collectData(packages)
    writeData(packages)
  }

  private fun collectData(packages: List<PackageData>) {
    for (pkg in packages) {
      packageVersions.add(pkg.ref, pkg.ref.version)
      for (dependency in pkg.dependencies) {
        if (dependency.isStdlib()) continue
        // Every package implicitly depends on the stdlib. Showing this dependency adds unwanted
        // noise.
        packageUsages.add(dependency.ref, pkg.ref)
      }
      for (module in pkg.modules) {
        moduleVersions.add(module.ref, pkg.ref.version)
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
    classVersions.add(clazz.ref, clazz.ref.version)
    for (superclass in clazz.superclasses) {
      if (superclass.isInSamePackageAs(clazz.ref)) {
        // only collect subtype information when belonging to the same package.
        subtypes.add(superclass, clazz.ref)
      }
    }
  }

  private fun writeData(packages: List<PackageData>) {
    for (ref in packageVersions.keys.distinctBy { it.pkg }) {
      writePackageFile(ref, packageVersions[ref], packageUsages[ref])
    }
    for (ref in classVersions.keys) {
      writeClassFile(ref, classVersions[ref], subtypes[ref])
    }
    for (ref in moduleVersions.keys) {
      writeModuleFile(ref, moduleVersions[ref])
    }
    updateKnownUsages(packages)
  }

  /**
   * It's possible that a new package uses types/packages from things that are already part of the
   * docsite.
   *
   * If so, update the known usages of those things.
   */
  private fun updateKnownUsages(packages: List<PackageData>) {
    val newlyWrittenPackageRefs = packages.mapTo(mutableSetOf()) { it.ref }
    val existingPackagesWithUpdatedKnownUsages = packageUsages.keys - newlyWrittenPackageRefs
    for (ref in existingPackagesWithUpdatedKnownUsages) {
      val runtimeDataPath = outputDir.resolve(ref.perPackageRuntimeDataPath)
      if (!runtimeDataPath.isRegularFile())
        continue // we must not have this package in our docsite.
      var runtimeData = getExistingRuntimeData(ref)
      val usages = packageUsages[ref]
      if (usages?.isNotEmpty() == true) {
        runtimeData =
          runtimeData.addKnownUsages(ref, usages.packagesWithHighestVersions(), PackageRef::pkg)
      }
      runtimeData.writeRef(outputDir, ref)
    }
  }

  private fun getExistingRuntimeData(ref: ElementRef<*>): RuntimeData {
    val perPackageDataPath = outputDir.resolve(ref.perPackageRuntimeDataPath)
    val perPackageVersionDataPath = outputDir.resolve(ref.perPackageVersionRuntimeDataPath)

    return (RuntimeData.readOrNull(perPackageDataPath) +
      RuntimeData.readOrNull(perPackageVersionDataPath)) ?: RuntimeData.EMPTY
  }

  private fun writePackageFile(ref: PackageRef, versions: Set<String>?, usages: Set<PackageRef>?) {
    val runtimeData =
      getExistingRuntimeData(ref)
        .addKnownVersions(ref, versions, descendingVersionComparator)
        .addKnownUsages(ref, usages, PackageRef::pkg)

    runtimeData.writeRef(outputDir, ref)
  }

  private fun writeClassFile(ref: TypeRef, versions: Set<String>?, subtypes: Set<TypeRef>?) {
    val runtimeData =
      getExistingRuntimeData(ref)
        .addKnownVersions(ref, versions, descendingVersionComparator)
        .addKnownSubtypes(ref, subtypes)

    runtimeData.writeRef(outputDir, ref)
  }

  private fun writeModuleFile(ref: ModuleRef, versions: Set<String>?) {
    val runtimeData =
      getExistingRuntimeData(ref).addKnownVersions(ref, versions, descendingVersionComparator)

    runtimeData.writeRef(outputDir, ref)
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

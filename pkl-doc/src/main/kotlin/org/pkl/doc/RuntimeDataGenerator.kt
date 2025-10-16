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
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.isRegularFile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import org.pkl.commons.lazyWithReceiver

// Note: we don't currently make use of persisted type alias data (needs more thought).
@OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
internal class RuntimeDataGenerator(
  private val descendingVersionComparator: Comparator<String>,
  private val outputDir: Path,
  consoleOut: OutputStream,
) : AbstractGenerator(consoleOut) {

  private val packageVersions: MutableMap<PackageId, MutableSet<PackageRef>> = mutableMapOf()
  private val classVersions: MutableMap<TypeId, MutableSet<String>> = mutableMapOf()
  private val packageUsages: MutableMap<PackageRef, MutableSet<PackageRef>> = mutableMapOf()
  private val typeUsages = mutableMapOf<TypeRef, MutableSet<TypeRef>>()
  private val subtypes: MutableMap<TypeRef, MutableSet<TypeRef>> = mutableMapOf()

  suspend fun generate(packages: List<PackageData>) {
    collectData(packages)
    writeData(packages)
  }

  private fun collectData(packages: List<PackageData>) {
    for (pkg in packages) {
      packageVersions.add(pkg.ref.pkg, pkg.ref)
      for (dependency in pkg.dependencies) {
        if (dependency.isStdlib()) continue
        // Every package implicitly depends on the stdlib. Showing this dependency adds unwanted
        // noise.
        packageUsages.add(dependency.ref, pkg.ref)
      }
      for (module in pkg.modules) {
        for (clazz in module.effectiveClasses) {
          collectData(clazz)
        }
      }
    }
  }

  // only collect type use information when belonging to the same package.
  private fun collectData(clazz: ClassData) {
    classVersions.add(clazz.ref.id, clazz.ref.version)
    for (superclass in clazz.superclasses) {
      if (superclass.isInSamePackageAs(clazz.ref)) {
        subtypes.add(superclass, clazz.ref)
      }
    }
    for (type in clazz.usedTypes) {
      if (type.isInSamePackageAs(clazz.ref)) {
        typeUsages.add(type, clazz.ref)
      }
    }
  }

  val writtenFiles = mutableSetOf<Path>()

  private suspend fun writeData(packages: List<PackageData>) {
    coroutineScope {
      for (pkg in packages) {
        launch { writePackageFilePerVersion(pkg) }
        for (module in pkg.modules) {
          for (clazz in module.effectiveClasses) {
            launch { writeClassFilePerVersion(clazz) }
          }
        }
      }
      for (pkg in packages.distinctBy { it.ref.pkg }) {
        launch { writePackageFile(pkg) }
        for (module in pkg.modules) {
          for (clazz in module.effectiveClasses) {
            launch { writeClassFile(clazz) }
          }
        }
      }
    }
    updateKnownUsages(packages)
  }

  private val ModuleData.effectiveClasses: List<ClassData>
    get() =
      when (moduleClass) {
        null -> classes
        else ->
          buildList {
            add(moduleClass)
            addAll(classes)
          }
      }

  /**
   * It's possible that a new package uses types/packages from things that are already part of the
   * docsite.
   *
   * If so, update the known usages of those things.
   */
  private suspend fun updateKnownUsages(packages: List<PackageData>) = coroutineScope {
    val newlyWrittenPackageRefs = packages.mapTo(mutableSetOf()) { it.ref }
    val existingPackagesWithUpdatedKnownUsages =
      packageUsages.keys.filterNot { newlyWrittenPackageRefs.contains(it) }
    for (ref in existingPackagesWithUpdatedKnownUsages) {
      launch {
        val runtimeDataPath = outputDir.resolve(ref.perPackageVersionRuntimeDataPath)
        // we must not have this package in our docsite.
        if (!runtimeDataPath.isRegularFile()) return@launch
        val usages = packageUsages[ref]
        val existingData = ref.existingPerPackageVersionRuntimeData
        val data =
          existingData.addKnownUsages(ref, usages, PackageRef::pkg, descendingVersionComparator)
        if (data != existingData) {
          data.doWriteTo(outputDir.resolve(ref.perPackageVersionRuntimeDataPath))
        }
      }
    }
  }

  private val ElementRef<*>.existingPerPackageRuntimeData: RuntimeData by lazyWithReceiver {
    val path = outputDir.resolve(perPackageRuntimeDataPath)
    RuntimeData.readOrEmpty(path)
  }

  private val ElementRef<*>.existingPerPackageVersionRuntimeData: RuntimeData by lazyWithReceiver {
    val path = outputDir.resolve(perPackageVersionRuntimeDataPath)
    RuntimeData.readOrEmpty(path)
  }

  private fun RuntimeData.doWriteTo(path: Path) {
    writeTo(path)
    consoleOut.write("Wrote file ${path.toUri()}\r")
  }

  private fun RuntimeData.writePerPackageVersion(ref: ElementRef<*>) {
    val path = outputDir.resolve(ref.perPackageVersionRuntimeDataPath)
    writtenFiles.add(path)
    perPackageVersion().writeTo(path)
    consoleOut.write("Wrote file ${path.toUri()}\r")
  }

  private fun RuntimeData.writePerPackage(ref: ElementRef<*>) {
    val path = outputDir.resolve(ref.perPackageRuntimeDataPath)
    writtenFiles.add(path)
    perPackage().writeTo(path)
    consoleOut.write("Wrote file ${path.toUri()}\r")
  }

  private fun writePackageFile(packageData: PackageData) {
    val ref = packageData.ref
    val newVersions = packageVersions[packageData.ref.pkg]?.mapTo(mutableSetOf()) { it.version }
    val data =
      ref.existingPerPackageRuntimeData.addKnownVersions(
        ref,
        newVersions,
        descendingVersionComparator,
      )
    data.writePerPackage(ref)
  }

  private fun writePackageFilePerVersion(packageData: PackageData) {
    val ref = packageData.ref
    val data =
      ref.existingPerPackageVersionRuntimeData.addKnownUsages(
        ref,
        packageUsages[ref],
        { it.pkg },
        descendingVersionComparator,
      )
    data.writePerPackageVersion(ref)
  }

  private fun writeClassFile(classData: ClassData) {
    val ref = classData.ref
    val newVersions = classVersions[ref.id]?.mapTo(mutableSetOf()) { it }
    val data =
      ref.existingPerPackageRuntimeData.addKnownVersions(
        ref,
        newVersions,
        descendingVersionComparator,
      )
    data.writePerPackage(ref)
  }

  private fun writeClassFilePerVersion(classData: ClassData) {
    val ref = classData.ref
    val newSubtypes = subtypes[ref]
    val newUsages = typeUsages[ref]
    val data =
      ref.existingPerPackageVersionRuntimeData
        .addKnownSubtypes(ref, newSubtypes, descendingVersionComparator)
        .addKnownUsages(ref, newUsages, TypeRef::displayName, descendingVersionComparator)
    data.writePerPackageVersion(ref)
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

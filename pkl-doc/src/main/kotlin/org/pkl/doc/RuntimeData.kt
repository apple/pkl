/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import kotlin.io.path.createParentDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

@Serializable internal data class RuntimeDataLink(val text: String, val href: String?)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class RuntimeData(
  val knownVersions: Set<RuntimeDataLink> = setOf(),
  val knownUsages: Set<RuntimeDataLink> = setOf(),
  val knownSubtypes: Set<RuntimeDataLink> = setOf(),
) {

  companion object {
    val EMPTY = RuntimeData()

    private val json = Json {
      prettyPrint = true
      explicitNulls = false
      ignoreUnknownKeys = true
      prettyPrintIndent = "  "
    }

    operator fun RuntimeData?.plus(other: RuntimeData?): RuntimeData? {
      return if (this == null) other
      else if (other == null) this
      else
        RuntimeData(
          knownVersions + other.knownVersions,
          knownUsages + other.knownUsages,
          knownSubtypes + other.knownSubtypes,
        )
    }

    fun readOrNull(path: Path): RuntimeData? {
      if (!path.isRegularFile()) return null
      return path.inputStream().use { json.decodeFromStream(it) }
    }
  }

  fun <T : ElementRef<*>> addKnownVersions(
    myRef: T,
    versions: Set<String>?,
    comparator: Comparator<String>,
  ): RuntimeData {
    if (versions == null) return this
    val newEffectiveVersions = knownVersions.mapTo(mutableSetOf()) { it.text } + versions
    val knownVersions =
      newEffectiveVersions
        .sortedWith(comparator)
        .map { version -> RuntimeDataLink(text = version, href = myRef.pageUrlForVersion(version)) }
        .toSet()
    return copy(knownVersions = knownVersions)
  }

  fun <T : ElementRef<*>> addKnownUsages(
    myRef: T,
    refs: Collection<T>?,
    text: (T) -> String,
  ): RuntimeData {
    if (refs == null) return this
    val newLinks =
      refs.mapTo(mutableSetOf()) { ref ->
        RuntimeDataLink(text = text(ref), href = ref.pageUrlRelativeTo(myRef))
      }
    val knownUsages = this.knownUsages + newLinks
    return copy(knownUsages = knownUsages.sortedBy { it.text }.toSet())
  }

  fun addKnownSubtypes(myRef: TypeRef, subtypes: Collection<TypeRef>?): RuntimeData {
    if (subtypes == null) return this
    val newLinks =
      subtypes.mapTo(mutableSetOf()) { ref ->
        RuntimeDataLink(text = ref.displayName, href = ref.pageUrlRelativeTo(myRef))
      }
    val knownSubtypes = this.knownSubtypes + newLinks
    return copy(knownSubtypes = knownSubtypes.sortedBy { it.text }.toSet())
  }

  fun writeRef(outputDir: Path, ref: ElementRef<*>) {
    perPackage().writeTo(outputDir.resolve(ref.perPackageRuntimeDataPath))
    perPackageVersion().writeTo(outputDir.resolve(ref.perPackageVersionRuntimeDataPath))
  }

  fun writeTo(path: Path) {
    path.createParentDirectories()
    path.outputStream().use { json.encodeToStream(this, it) }
  }

  fun perPackage(): RuntimeData = copy(knownUsages = setOf(), knownSubtypes = setOf())

  fun perPackageVersion(): RuntimeData = RuntimeData(knownVersions = setOf())
}

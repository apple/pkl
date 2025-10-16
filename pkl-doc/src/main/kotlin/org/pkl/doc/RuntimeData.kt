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

import java.io.FileNotFoundException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.pkl.commons.readString
import org.pkl.commons.writeString

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

    /** Compare two paths, comparing each segment. */
    private fun segmentComparator(comparator: Comparator<String>): Comparator<RuntimeDataLink> {
      return Comparator { o1, o2 ->
        val path1Segments = o1.href!!.split("/")
        val path2Segments = o2.href!!.split("/")
        for ((path1, path2) in path1Segments.zip(path2Segments)) {
          if (path1 == path2) continue
          try {
            val comparison = comparator.compare(path1, path2)
            if (comparison != 0) return@Comparator comparison
          } catch (_: Throwable) {
            // possibly happens if the version is invalid.
            continue
          }
        }
        0
      }
    }

    fun readOrEmpty(path: Path): RuntimeData {
      return try {
        json.decodeFromString(path.readString())
      } catch (e: Throwable) {
        when (e) {
          is NoSuchFileException,
          is FileNotFoundException -> EMPTY
          is SerializationException ->
            throw DocGeneratorBugException("Error deserializing `${path.toUri()}`.", e)
          else -> throw e
        }
      }
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
    if (knownVersions == this.knownVersions) {
      return this
    }
    return copy(knownVersions = knownVersions)
  }

  fun <T : ElementRef<*>> addKnownUsages(
    myRef: T,
    refs: Collection<T>?,
    text: (T) -> String,
    comparator: Comparator<String>,
  ): RuntimeData {
    if (refs == null) return this
    val newLinks =
      refs.mapTo(mutableSetOf()) { ref ->
        RuntimeDataLink(text = text(ref), href = ref.pageUrlRelativeTo(myRef))
      }
    val knownUsages =
      (this.knownUsages + newLinks).distinctByCommparator(comparator).sortedBy { it.text }.toSet()
    if (knownUsages == this.knownUsages) {
      return this
    }
    return copy(knownUsages = knownUsages)
  }

  fun addKnownSubtypes(
    myRef: TypeRef,
    subtypes: Collection<TypeRef>?,
    comparator: Comparator<String>,
  ): RuntimeData {
    if (subtypes == null) return this
    val newLinks =
      subtypes.mapTo(mutableSetOf()) { ref ->
        RuntimeDataLink(text = ref.displayName, href = ref.pageUrlRelativeTo(myRef))
      }
    val knownSubtypes =
      (this.knownSubtypes + newLinks).distinctByCommparator(comparator).sortedBy { it.text }.toSet()
    if (knownSubtypes == this.knownSubtypes) {
      return this
    }
    return copy(knownSubtypes = knownSubtypes)
  }

  fun Collection<RuntimeDataLink>.distinctByCommparator(
    comparator: Comparator<String>
  ): Collection<RuntimeDataLink> {
    val compareBySegment = segmentComparator(comparator)
    val highestVersions = mutableMapOf<String, RuntimeDataLink>()
    for (link in this) {
      val prev = highestVersions[link.text]
      if (prev == null || compareBySegment.compare(prev, link) > 0) {
        highestVersions[link.text] = link
      }
    }
    return highestVersions.values
  }

  fun writeTo(path: Path) {
    path.createParentDirectories()
    path.writeString(json.encodeToString(this))
  }

  fun perPackage(): RuntimeData = copy(knownUsages = setOf(), knownSubtypes = setOf())

  fun perPackageVersion(): RuntimeData = copy(knownVersions = setOf())
}

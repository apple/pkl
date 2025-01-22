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
@file:Suppress("MemberVisibilityCanBePrivate")

import java.util.*
import org.gradle.jvm.toolchain.JavaLanguageVersion

typealias JavaVersionPair = Pair<JavaLanguageVersion, JavaLanguageVersion>

// All LTS releases.
private val ltsReleases =
  sortedSetOf(
    JavaLanguageVersion.of(8),
    JavaLanguageVersion.of(11),
    JavaLanguageVersion.of(17),
    JavaLanguageVersion.of(21),
  )

/** Describes an inclusive range of JVM versions, based on the [JavaLanguageVersion] type. */
@JvmInline
value class JavaVersionRange private constructor(private val bounds: JavaVersionPair) :
  Iterable<JavaLanguageVersion> {
  @Suppress("unused")
  companion object {
    fun isLTS(version: JavaLanguageVersion): Boolean = version in ltsReleases

    fun inclusive(floor: JavaLanguageVersion, ceiling: JavaLanguageVersion): JavaVersionRange =
      JavaVersionRange(floor to ceiling)

    fun startingAt(floor: JavaLanguageVersion): JavaVersionRange =
      inclusive(floor, JavaLanguageVersion.of(PKL_TEST_JDK_MAXIMUM))

    fun upTo(ceiling: JavaLanguageVersion): JavaVersionRange =
      inclusive(JavaLanguageVersion.of(PKL_TEST_JDK_MINIMUM), ceiling)
  }

  operator fun contains(version: JavaLanguageVersion): Boolean =
    version >= bounds.first && version <= bounds.second

  fun asSequence(): Sequence<JavaLanguageVersion> = sequence {
    var current = bounds.first
    while (current <= bounds.second) {
      yield(current)
      current = JavaLanguageVersion.of(current.asInt() + 1)
    }
  }

  fun asSortedSet(): SortedSet<JavaLanguageVersion> = asSequence().toSortedSet()

  override fun iterator(): Iterator<JavaLanguageVersion> = asSortedSet().iterator()
}

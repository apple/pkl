/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.config.scala.mapper

import org.pkl.config.java.mapper.{Converter, ValueMapper}
import org.pkl.core.PClassInfo

import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages cached type information and retrieves converters dynamically based on the type of input.
 *
 * `CachedSourceTypeInfo` encapsulates the source type information (`classInfo`) and a reusable
 * converter, optimizing conversions by caching both type details and converters. This caching
 * approach is particularly useful in repeated conversions where source type remains consistent.
 *
 * Thread-safe: `ValueMapper` instances (and therefore this cache) can be shared across threads. The
 * paired `classInfo` and `converter` fields are kept coherent via a single `AtomicReference` — they
 * always update together or not at all.
 */
private[mapper] class CachedSourceTypeInfo {
  import CachedSourceTypeInfo.*

  private val ref: AtomicReference[Entry] = new AtomicReference[Entry]()

  /**
   * Updates the cached `classInfo` and retrieves a converter if the type of `v` differs from the
   * cached `classInfo`. If the types match, the cached converter is reused.
   *
   * Under contention, multiple threads may each compute a fresh converter for the same input type —
   * this wastes a lookup but never produces an inconsistent cache, because `classInfo` and
   * `converter` are stored as a single `Entry` object.
   */
  def updateAndGet(v: Any, t: Type, vm: ValueMapper): Any = {
    val current = ref.get()
    val converter: Converter[Any, Any] = {
      if (current != null && current.classInfo.isExactClassOf(v)) {
        current.converter
      } else {
        val newClassInfo = PClassInfo.forValue(v).asInstanceOf[PClassInfo[Any]]
        val newConverter = vm
          .getConverter(newClassInfo, t)
          .asInstanceOf[Converter[Any, Any]]
        ref.set(Entry(newClassInfo, newConverter))
        newConverter
      }
    }
    converter.convert(v, vm)
  }
}

private[mapper] object CachedSourceTypeInfo {
  private final case class Entry(
      classInfo: PClassInfo[Any],
      converter: Converter[Any, Any]
  )
}

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
package org.pkl.config.scala.mapper

import org.pkl.config.java.mapper.{Converter, ValueMapper}
import org.pkl.core.PClassInfo

import java.lang.reflect.Type

/** Manages cached type information and retrieves converters dynamically based
  * on the type of input.
  *
  * `CachedSourceTypeInfo` encapsulates the source type information
  * (`classInfo`) and a reusable converter, optimizing conversions by caching
  * both type details and converters. This caching approach is particularly
  * useful in repeated conversions where source type remains consistent.
  */
private[mapper] class CachedSourceTypeInfo {

  // Initially set to an unavailable type and will be updated based on the input value type.
  private var classInfo: PClassInfo[Any] =
    PClassInfo.Unavailable.asInstanceOf[PClassInfo[Any]]

  // Holds an optional converter, cached upon first retrieval.
  private var converter: Option[Converter[Any, Any]] = None

  /** Updates the `classInfo` and retrieves a converter if the type of `v`
    * differs from the cached `classInfo`. If the types match, the cached
    * converter is reused.
    *
    * This method leverages caching to avoid redundant converter lookups,
    * improving efficiency when the same type conversions are repeatedly
    * required.
    *
    * @param v
    *   The value for which conversion is needed.
    * @param t
    *   The target type to which the value should be converted.
    * @param vm
    *   The `ValueMapper` responsible for providing the appropriate converter.
    *
    * @return
    *   The converted value, transformed to match the specified target type `t`.
    *
    * @example
    *   Basic usage:
    *   {{{
    * val cachedInfo = new CachedSourceTypeInfo()
    * val result = cachedInfo.updateAndGet(myValue, targetType, myValueMapper)
    *   }}}
    */
  def updateAndGet(v: Any, t: Type, vm: ValueMapper): Any = {
    // Determine if the cached classInfo matches the type of v; if not, update and find new converter.
    val c: Converter[Any, Any] = if (!classInfo.isExactClassOf(v)) {
      classInfo = PClassInfo.forValue(v)
      vm.getConverter(classInfo, t)
    } else {
      // Use the cached converter or obtain a new one if not cached yet.
      converter getOrElse vm.getConverter(classInfo, t)
    }

    converter = Some(c) // Cache the converter for subsequent conversions
    c.convert(v, vm) // Convert and return the value
  }
}

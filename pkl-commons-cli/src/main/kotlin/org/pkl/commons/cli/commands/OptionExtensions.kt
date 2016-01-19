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
package org.pkl.commons.cli.commands

import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.transformAll

/** Forbid this option from being repeated. */
fun <EachT : Any, ValueT> NullableOption<EachT, ValueT>.single(): NullableOption<EachT, ValueT> {
  return transformAll {
    if (it.size > 1) {
      fail("Option cannot be repeated")
    }
    it.lastOrNull()
  }
}

/**
 * Allow this option to be repeated and to receive multiple values separated by [separator]. This is
 * a mix of [split][com.github.ajalt.clikt.parameters.options.split] and
 * [multiple][com.github.ajalt.clikt.parameters.options.multiple] joined together.
 */
fun <EachT : Any, ValueT> NullableOption<EachT, ValueT>.splitAll(
  separator: String = ",",
  default: List<ValueT> = emptyList()
): OptionWithValues<List<ValueT>, List<ValueT>, ValueT> {
  return copy(
    transformValue = transformValue,
    transformEach = { it },
    transformAll = { it.flatten().ifEmpty { default } },
    validator = {},
    nvalues = 1,
    valueSplit = Regex.fromLiteral(separator)
  )
}

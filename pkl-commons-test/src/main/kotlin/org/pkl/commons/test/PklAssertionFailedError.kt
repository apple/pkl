/*
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
package org.pkl.commons.test

import org.assertj.core.util.diff.DiffUtils
import org.opentest4j.AssertionFailedError

/**
 * Makes up for the fact that [AssertionFailedError] doesn't print a diff, resulting in
 * unintelligible errors outside IDEs (which show a diff dialog).
 * https://github.com/ota4j-team/opentest4j/issues/59
 */
class PklAssertionFailedError(message: String, expected: Any?, actual: Any?) :
  AssertionFailedError(message, expected, actual) {
  override fun toString(): String {
    val patch =
      DiffUtils.diff(expected.stringRepresentation.lines(), actual.stringRepresentation.lines())
    return patch.deltas.joinToString("\n\n")
  }
}

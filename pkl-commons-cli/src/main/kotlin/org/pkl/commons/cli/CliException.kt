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
package org.pkl.commons.cli

import org.pkl.commons.printStackTraceToString

/** A CLI error to report back to users. */
open class CliException(
  /**
   * The error message to report back to CLI users. The message is expected to be displayed as-is
   * without any further enrichment. As such the message should be comprehensive and designed with
   * the CLI user in mind.
   */
  message: String,

  /** The process exit code to use. */
  val exitCode: Int = 1
) : RuntimeException(message) {

  override fun toString(): String = message!!
}

/** An unexpected CLI error classified as bug. */
class CliBugException(
  /** The cause for the bug. */
  private val theCause: Exception,

  /** The process exit code to use. */
  exitCode: Int = 1
) :
  CliException("An unexpected error has occurred. Would you mind filing a bug report?", exitCode) {

  override fun toString(): String = "$message\n\n${theCause.printStackTraceToString()}"
}

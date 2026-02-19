/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate

abstract class BaseCommand(
  name: String,
  private val helpLink: String,
  useShortOptionNames: Boolean = true,
) : CliktCommand(name = name) {
  abstract val helpString: String

  override fun help(context: Context) = helpString

  final override fun helpEpilog(context: Context) = "For more information, visit $helpLink"

  val baseOptions: BaseOptions by BaseOptions(useShortOptionNames)
}

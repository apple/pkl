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
package org.pkl.commons.cli

import com.github.ajalt.clikt.core.parse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.commons.cli.commands.BaseCommand
import org.pkl.core.SecurityManagers

class CliCommandTest {

  class CliTest(private val options: CliBaseOptions) : CliCommand(options) {
    override fun doRun() = Unit

    val _allowedResources = allowedResources
    val _allowedModules = allowedModules
  }

  private val cmd =
    object : BaseCommand("test", "") {
      override fun run() = Unit

      override val helpString: String = ""
    }

  @Test
  fun `--external-resource-reader and --external-module-reader populate allowed modules and resources`() {
    cmd.parse(
      arrayOf(
        "--external-module-reader",
        "scheme3=reader3",
        "--external-module-reader",
        "scheme4=reader4 with args",
        "--external-module-reader",
        "scheme+ext=reader5 with args",
        "--external-resource-reader",
        "scheme1=reader1",
        "--external-resource-reader",
        "scheme2=reader2 with args",
        "--external-resource-reader",
        "scheme+ext=reader5 with args",
      )
    )
    val opts = cmd.baseOptions.baseOptions(emptyList(), null, true)
    val cliTest = CliTest(opts)
    assertThat(cliTest._allowedModules.map { it.pattern() })
      .isEqualTo(
        SecurityManagers.defaultAllowedModules.map { it.pattern() } +
          listOf("\\Qscheme3:\\E", "\\Qscheme4:\\E", "\\Qscheme+ext:\\E")
      )
    assertThat(cliTest._allowedResources.map { it.pattern() })
      .isEqualTo(
        SecurityManagers.defaultAllowedResources.map { it.pattern() } +
          listOf("\\Qscheme1:\\E", "\\Qscheme2:\\E", "\\Qscheme+ext:\\E")
      )
  }
}

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
package org.pkl.commons.cli

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.parse
import java.io.File
import java.nio.file.Path
import java.util.regex.Pattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.commons.cli.commands.BaseCommand
import org.pkl.core.evaluatorSettings.PklEvaluatorSettings.ExternalReader

class BaseCommandTest {

  private val cmd =
    object : BaseCommand("test", "") {
      override fun run() = Unit

      override val helpString: String = ""
    }

  @Test
  fun `invalid timeout`() {
    val e = assertThrows<BadParameterValue> { cmd.parse(arrayOf("--timeout", "abc")) }
    assertThat(e.message).isEqualTo("abc is not a valid integer")
  }

  @Test
  fun `help queries do not present as errors`() {
    assertThrows<PrintHelpMessage> { cmd.parse(arrayOf("--help")) }
  }

  @Test
  fun `difficult cases for external properties`() {
    cmd.parse(arrayOf("-p", "flag1", "-p", "flag2=", "-p", "FOO=bar", "-p", "baz=qux=quux"))
    val props = cmd.baseOptions.baseOptions(emptyList()).externalProperties

    assertThat(props)
      .isEqualTo(mapOf("flag1" to "true", "flag2" to "", "FOO" to "bar", "baz" to "qux=quux"))
  }

  @Test
  fun `--allowed-modules, --allowed-resources and --module-path can be repeated`() {
    cmd.parse(arrayOf("--allowed-modules", "m1,m2,m3", "--allowed-modules", "m4"))

    assertThat(cmd.baseOptions.allowedModules.map(Pattern::toString))
      .isEqualTo(listOf("m1", "m2", "m3", "m4"))

    cmd.parse(arrayOf("--allowed-resources", "r1,r2,r3", "--allowed-resources", "r4"))
    assertThat(cmd.baseOptions.allowedResources.map(Pattern::toString))
      .isEqualTo(listOf("r1", "r2", "r3", "r4"))

    val sep = File.pathSeparator
    cmd.parse(arrayOf("--module-path", "p1${sep}p2${sep}p3", "--module-path", "p4"))
    assertThat(cmd.baseOptions.modulePath).isEqualTo(listOf("p1", "p2", "p3", "p4").map(Path::of))

    cmd.parse(arrayOf())
    assertThat(cmd.baseOptions.allowedModules).isEmpty()

    assertThat(cmd.baseOptions.allowedResources).isEmpty()
  }

  @Test
  fun `--external-resource-reader and --external-module-reader are parsed correctly`() {
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
    assertThat(cmd.baseOptions.externalModuleReaders)
      .isEqualTo(
        mapOf(
          "scheme3" to ExternalReader("reader3", emptyList()),
          "scheme4" to ExternalReader("reader4", listOf("with", "args")),
          "scheme+ext" to ExternalReader("reader5", listOf("with", "args")),
        )
      )
    assertThat(cmd.baseOptions.externalResourceReaders)
      .isEqualTo(
        mapOf(
          "scheme1" to ExternalReader("reader1", emptyList()),
          "scheme2" to ExternalReader("reader2", listOf("with", "args")),
          "scheme+ext" to ExternalReader("reader5", listOf("with", "args")),
        )
      )
  }
}

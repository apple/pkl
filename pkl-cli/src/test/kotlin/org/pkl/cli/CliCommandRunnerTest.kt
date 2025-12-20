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
package org.pkl.cli

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.MissingOption
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.test.FileTestUtils
import org.pkl.commons.test.PackageServer
import org.pkl.commons.writeString

@OptIn(ExperimentalPathApi::class)
@WireMockTest(httpsEnabled = true, proxyMode = true)
class CliCommandRunnerTest {
  private val renderOptions =
    """
      extends "pkl:Command"
      
      options: Options
      
      output {
        value = options
      }
      
    """
      .trimIndent()

  companion object {
    private val packageServer = PackageServer()

    @AfterAll
    @JvmStatic
    fun afterAll() {
      packageServer.close()
    }
  }

  // use manually constructed temp dir instead of @TempDir to work around
  // https://forums.developer.apple.com/thread/118358
  private val tempDir: Path = run {
    val baseDir = FileTestUtils.rootProjectDir.resolve("pkl-cli/build/tmp/CliCommandRunnerTest")
    baseDir.createDirectories()
    Files.createTempDirectory(baseDir, null)
  }

  @AfterEach
  fun afterEach() {
    tempDir.deleteRecursively()
  }

  private fun writePklFile(fileName: String, contents: String): URI {
    tempDir.resolve(fileName).createParentDirectories()
    return tempDir.resolve(fileName).writeString(contents).toUri()
  }

  private fun runToStdout(options: CliBaseOptions, args: List<String>): String {
    val outWriter = ByteArrayOutputStream()
    CliCommandRunner(options, args, outWriter, ByteArrayOutputStream()).run()
    return outWriter.toString(StandardCharsets.UTF_8)
  }

  @Test
  fun `missing required flag`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo: String
      }
    """
            .trimIndent(),
      )

    val exc =
      assertThrows<MissingOption> {
        runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri), testMode = true), listOf())
      }
    assertThat(exc.paramName).isEqualTo("--foo")
  }

  @Test
  fun `primitive flags`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        `number-as-int`: Number
        `number-as-float`: Number
        `number-nullable`: Number?
        `number-default`: Number = 100.0
        `number-default-overridden`: Number = 100.0
        
        float: Float
        `float-without-decimals`: Float
        `float-nullable`: Float?
        `float-default`: Float = 100.0
        `float-default-overridden`: Float = 100.0
        
        int: Int
        `int-nullable`: Int?
        `int-default`: Int = 100
        `int-default-overridden`: Int = 100
        
        int8: Int8
        int16: Int16
        int32: Int32
        uint: UInt
        uint8: UInt8
        uint16: UInt16
        uint32: UInt32
        
        boolean: Boolean
        `boolean-nullable`: Boolean?
        `boolean-default`: Boolean = true
        `boolean-default-overridden`: Boolean = false
        
        string: String
        `string-nullable`: String?
        `string-default`: String = "default"
        `string-default-overridden`: String = "default"
        
        char: Char
        `char-nullable`: Char?
        `char-default`: Char = "a"
        `char-default-overridden`: Char = "b"
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf(
          "--number-as-int=123",
          "--number-as-float=123.0",
          "--number-default-overridden=-200.0",
          "--float=123.456",
          "--float-without-decimals=789",
          "--float-default-overridden=-200",
          "--int=123",
          "--int-default-overridden=-200",
          "--int8=127",
          "--int16=32767",
          "--int32=2147483647",
          "--uint=0",
          "--uint8=255",
          "--uint16=65535",
          "--uint32=4294967295",
          "--boolean=n",
          "--boolean-default-overridden=1",
          "--string=foobar",
          "--string-default-overridden=non-default",
          "--char=X",
          "--char-default-overridden=c",
        ),
      )
    assertThat(output)
      .isEqualTo(
        """
        `number-as-int` = 123
        `number-as-float` = 123.0
        `number-nullable` = null
        `number-default` = 100.0
        `number-default-overridden` = -200.0
        float = 123.456
        `float-without-decimals` = 789.0
        `float-nullable` = null
        `float-default` = 100.0
        `float-default-overridden` = -200.0
        int = 123
        `int-nullable` = null
        `int-default` = 100
        `int-default-overridden` = -200
        int8 = 127
        int16 = 32767
        int32 = 2147483647
        uint = 0
        uint8 = 255
        uint16 = 65535
        uint32 = 4294967295
        boolean = false
        `boolean-nullable` = null
        `boolean-default` = true
        `boolean-default-overridden` = true
        string = "foobar"
        `string-nullable` = null
        `string-default` = "default"
        `string-default-overridden` = "non-default"
        char = "X"
        `char-nullable` = null
        `char-default` = "a"
        `char-default-overridden` = "c"
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `primitive arguments`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument
        `number-as-int`: Number
        @Argument
        `number-as-float`: Number
        
        @Argument
        float: Float
        @Argument
        `float-without-decimals`: Float
        
        @Argument
        int: Int
        
        @Argument
        int8: Int8
        @Argument
        int16: Int16
        @Argument
        int32: Int32
        @Argument
        uint: UInt
        @Argument
        uint8: UInt8
        @Argument
        uint16: UInt16
        @Argument
        uint32: UInt32
        
        @Argument
        boolean: Boolean
        
        @Argument
        string: String
        
        @Argument
        char: Char
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf(
          "123",
          "123.0",
          "123.456",
          "789",
          "123",
          "127",
          "32767",
          "2147483647",
          "0",
          "255",
          "65535",
          "4294967295",
          "n",
          "foobar",
          "X",
        ),
      )
    assertThat(output)
      .isEqualTo(
        """
        `number-as-int` = 123
        `number-as-float` = 123.0
        float = 123.456
        `float-without-decimals` = 789.0
        int = 123
        int8 = 127
        int16 = 32767
        int32 = 2147483647
        uint = 0
        uint8 = 255
        uint16 = 65535
        uint32 = 4294967295
        boolean = false
        string = "foobar"
        char = "X"
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `enum flags`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      typealias MyEnum = "d" | "e" | *"f"
      class Options {
        enum: "a" | "b" | "c"
        `enum-default`: "a" | *"b" | "c"
        `enum-explicit-default`: "a" | "b" | "c" = "c"
        `enum-alias-default`: MyEnum
        `enum-alias-explicit-default`: MyEnum = "e"
        `enum-alias-default-overridden`: MyEnum
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf("--enum=a", "--enum-alias-default-overridden=d"),
      )
    assertThat(output)
      .isEqualTo(
        """
        enum = "a"
        `enum-default` = "b"
        `enum-explicit-default` = "c"
        `enum-alias-default` = "f"
        `enum-alias-explicit-default` = "e"
        `enum-alias-default-overridden` = "d"
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `enum args`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      typealias MyEnum = "d" | "e" | *"f"
      class Options {
        @Argument
        enum: "a" | "b" | "c"
        @Argument
        `enum-default`: "a" | *"b" | "c"
        @Argument
        `enum-alias-default`: MyEnum
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri)), listOf("a", "c", "d"))
    assertThat(output)
      .isEqualTo(
        """
        enum = "a"
        `enum-default` = "c"
        `enum-alias-default` = "d"
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `collection flags`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        list: List<Number>
        `list-nullable`: List<Number>?
        `list-default`: List<Number> = List(1, 2, 300.0)
        set: Set<Number>
        `set-nullable`: Set<Number>?
        `set-default`: Set<Number> = Set(1, 2, 300.0, 2)
        
        `enum-list`: List<"a" | "b" | *"c">
        `enum-set`: Set<"a" | "b" | *"c">
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf(
          "--list=1",
          "--list=0",
          "--list=0.0",
          "--list=1",
          "--set=1",
          "--set=0",
          "--set=0.0",
          "--set=1",
          "--enum-list=a",
          "--enum-list=a",
          "--enum-list=b",
          "--enum-set=a",
          "--enum-set=a",
          "--enum-set=b",
        ),
      )
    assertThat(output)
      .isEqualTo(
        """
        list = List(1, 0, 0.0, 1)
        `list-nullable` = null
        `list-default` = List(1, 2, 300.0)
        set = Set(1, 0, 0.0)
        `set-nullable` = null
        `set-default` = Set(1, 2, 300.0)
        `enum-list` = List("a", "a", "b")
        `enum-set` = Set("a", "b")
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `collection args`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument
        list: List<Number>
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri)), listOf("1", "0", "0.0", "1"))
    assertThat(output)
      .isEqualTo(
        """
        list = List(1, 0, 0.0, 1)
        
        """
          .trimIndent()
      )

    val moduleUri2 =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument
        set: Set<Number>
      }
    """
            .trimIndent(),
      )
    val output2 =
      runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri2)), listOf("1", "0", "0.0", "1"))
    assertThat(output2)
      .isEqualTo(
        """
        set = Set(1, 0, 0.0)
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `map flags`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      typealias MyEnum = "a" | "b" | *"c"
      class Options {
        map: Map<Char, Number>
        `map-nullable`: Map<Char, Number>?
        `map-default`: Map<Char, Number> = Map("x", 123, "y", 456.789)
        
        `enum-map`: Map<MyEnum, MyEnum>
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf("--map=a=0.0", "--map=b=1", "--enum-map=a=b", "--enum-map=b=c"),
      )
    assertThat(output)
      .isEqualTo(
        """
        map = Map("a", 0.0, "b", 1)
        `map-nullable` = null
        `map-default` = Map("x", 123, "y", 456.789)
        `enum-map` = Map("a", "b", "b", "c")
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `parse Duration`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument { parse = module.parseDuration }
        a: Duration
        @Argument { parse = module.parseDuration }
        b: Duration
        @Argument { parse = module.parseDuration }
        c: Duration
        @Argument { parse = module.parseDuration }
        d: Duration
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf("10.h", "10H", "10.5.MS", "10.5d"),
      )
    assertThat(output)
      .isEqualTo(
        """
        a = 10.h
        b = 10.h
        c = 10.5.ms
        d = 10.5.d
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `parse DataSize`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument { parse = module.parseDataSize }
        a: DataSize
        @Argument { parse = module.parseDataSize }
        b: DataSize
        @Argument { parse = module.parseDataSize }
        c: DataSize
        @Argument { parse = module.parseDataSize }
        d: DataSize
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf("10.gb", "10GB", "10.5.MB", "10.5tib"),
      )
    assertThat(output)
      .isEqualTo(
        """
        a = 10.gb
        b = 10.gb
        c = 10.5.mb
        d = 10.5.tib
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `parse import`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument { parse = "import" }
        fromImport: Module
      }
    """
            .trimIndent(),
      )

    val importUri =
      writePklFile(
        "import.pkl",
        """
      foo = 1
      bar = "baz"
    """
          .trimIndent(),
      )

    val output =
      runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri)), listOf(importUri.toString()))
    assertThat(output)
      .isEqualTo(
        """
        fromImport {
          foo = 1
          bar = "baz"
        }
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `parse glob import`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        """
      extends "pkl:Command"
      import "base.pkl"
      
      options: Options
      
      output {
        value = options
      }
      
      class Options {
        @Argument { parse = "import*" }
        fromGlobImport: Mapping<String, base>
      }
    """
          .trimIndent(),
      )

    val baseImport =
      writePklFile(
        "base.pkl",
        """
      foo: Int
      bar: String
    """
          .trimIndent(),
      )
    writePklFile(
      "glob1.pkl",
      """
      amends "base.pkl"
      foo = 1
      bar = "baz"
    """
        .trimIndent(),
    )
    writePklFile(
      "glob2.pkl",
      """
      amends "base.pkl"
      foo = 2
      bar = "qux"
    """
        .trimIndent(),
    )

    val importDirUri = baseImport.resolve(".")

    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf(importDirUri.resolve("./glob*.pkl").toString()),
      )
    assertThat(output.replace(importDirUri.toString(), "file:/<dir>/"))
      .isEqualTo(
        """
        fromGlobImport {
          ["file:/<dir>/glob1.pkl"] {
            foo = 1
            bar = "baz"
          }
          ["file:/<dir>/glob2.pkl"] {
            foo = 2
            bar = "qux"
          }
        }
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `parse that throws`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument { parse = (it) -> throw("oops!") }
        foo: String
      }
    """
            .trimIndent(),
      )

    val exc =
      assertThrows<CliktError> {
        runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri)), listOf("hi"))
      }
    assertThat(exc.message).isEqualTo("oops!")
  }
}

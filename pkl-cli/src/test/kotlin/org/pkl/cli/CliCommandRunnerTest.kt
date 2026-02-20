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
import com.github.ajalt.clikt.core.PrintCompletionMessage
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
import org.pkl.commons.cli.CliException
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
        
        `enum-single`: "x"
        `enum-single-nullable`: "x"?
        `enum-single-explicit-default`: "x" = "x"
        `enum-single-overridden`: "x"
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf("--enum=a", "--enum-alias-default-overridden=d", "--enum-single-overridden=x"),
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
        `enum-single` = "x"
        `enum-single-nullable` = null
        `enum-single-explicit-default` = "x"
        `enum-single-overridden` = "x"

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
  fun `sequence args`() {
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

    val moduleUri3 =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument
        listing: Listing<Number>
      }
    """
            .trimIndent(),
      )
    val output3 =
      runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri3)), listOf("1", "0", "0.0", "1"))
    assertThat(output3)
      .isEqualTo(
        """
        listing {
          1
          0
          0.0
          1
        }
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `keyval args`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument
        map: Map<Number, Number>
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri)), listOf("1=0", "0.0=1"))
    assertThat(output)
      .isEqualTo(
        """
        map = Map(1, 0, 0.0, 1)
        
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
        mapping: Mapping<Number, Number>
      }
    """
            .trimIndent(),
      )
    val output2 =
      runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri2)), listOf("1=0", "0.0=1"))
    assertThat(output2)
      .isEqualTo(
        """
        mapping {
          [1] = 0
          [0.0] = 1
        }
        
        """
          .trimIndent()
      )

    val moduleUri3 =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument
        pair: Pair<Number, Number>
      }
    """
            .trimIndent(),
      )
    val output3 = runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri3)), listOf("1=0.0"))
    assertThat(output3)
      .isEqualTo(
        """
        pair = Pair(1, 0.0)
        
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
  fun `mapping flags`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      typealias MyEnum = "a" | "b" | *"c"
      class Options {
        mapping: Mapping<Char, Number>
        `mapping-nullable`: Mapping<Char, Number>?
        `mapping-default`: Mapping<Char, Number> = new { ["x"] = 123; ["y"] = 456.789 }
        
        `enum-mapping`: Mapping<MyEnum, MyEnum>
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf("--mapping=a=0.0", "--mapping=b=1", "--enum-mapping=a=b", "--enum-mapping=b=c"),
      )
    assertThat(output)
      .isEqualTo(
        """
        mapping {
          ["a"] = 0.0
          ["b"] = 1
        }
        `mapping-nullable` = null
        `mapping-default` {
          ["x"] = 123
          ["y"] = 456.789
        }
        `enum-mapping` {
          ["a"] = "b"
          ["b"] = "c"
        }
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `pair flags`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      typealias MyEnum = "a" | "b" | *"c"
      class Options {
        pair: Pair<Char, Number>
        `pair-nullable`: Pair<Char, Number>?
        `pair-default`: Pair<Char, Number> = Pair("x", 123)
        
        `enum-pair`: Pair<MyEnum, MyEnum>
      }
    """
            .trimIndent(),
      )
    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf("--pair=a=0.0", "--enum-pair=a=b"),
      )
    assertThat(output)
      .isEqualTo(
        """
        pair = Pair("a", 0.0)
        `pair-nullable` = null
        `pair-default` = Pair("x", 123)
        `enum-pair` = Pair("a", "b")
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `convert Duration`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument { convert = module.convertDuration }
        a: Duration
        @Argument { convert = module.convertDuration }
        b: Duration
        @Argument { convert = module.convertDuration }
        c: Duration
        @Argument { convert = module.convertDuration }
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
  fun `convert DataSize`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument { convert = module.convertDataSize }
        a: DataSize
        @Argument { convert = module.convertDataSize }
        b: DataSize
        @Argument { convert = module.convertDataSize }
        c: DataSize
        @Argument { convert = module.convertDataSize }
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
  fun `convert import`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument { convert = (it) -> new Import{ uri = it } }
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
  fun `convert glob import`() {
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
        @Argument { convert = (it) -> new Import { uri = it; glob = true }; multiple = false }
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
  fun `convert that throws`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument { convert = (it) -> throw("oops!") }
        foo: String
      }
    """
            .trimIndent(),
      )

    val exc =
      assertThrows<CliktError> {
        runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri)), listOf("hi"))
      }
    assertThat(exc.message).contains("oops!")
  }

  @Test
  fun `convert with eval error`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument { convert = (it) -> it.noSuchMethod() }
        foo: String
      }
    """
            .trimIndent(),
      )

    val exc =
      assertThrows<CliktError> {
        runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri)), listOf("hi"))
      }
    assertThat(exc.message).contains("Cannot find method `noSuchMethod` in class `String`.")
  }

  @Test
  fun `convert with stack overflow`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      const function overflow(it) = overflow(it)
      class Options {
        @Argument { convert = (it) -> overflow(it) }
        foo: String
      }
    """
            .trimIndent(),
      )

    val exc =
      assertThrows<CliktError> {
        runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri)), listOf("hi"))
      }
    assertThat(exc.message).contains("A stack overflow occurred.")
  }

  @Test
  fun `boolean flag`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @BooleanFlag
        `bool-true`: Boolean
        @BooleanFlag
        `bool-false`: Boolean
        @BooleanFlag
        `bool-nullable`: Boolean?
        @BooleanFlag
        `bool-default-true`: Boolean = true
        @BooleanFlag
        `bool-default-false`: Boolean = false
      }
    """
            .trimIndent(),
      )

    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf("--bool-true", "--no-bool-false"),
      )
    assertThat(output)
      .isEqualTo(
        """
        `bool-true` = true
        `bool-false` = false
        `bool-nullable` = null
        `bool-default-true` = true
        `bool-default-false` = false
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `boolean flag with bad type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @BooleanFlag
        foo: String
      }
    """
            .trimIndent(),
      )

    val exc =
      assertThrows<CliException> {
        runToStdout(CliBaseOptions(sourceModules = listOf(moduleUri)), listOf("hi"))
      }
    assertThat(exc.message)
      .contains("Option `foo` with annotation `@BooleanFlag` has invalid type `String`.")
  }

  @Test
  fun `counted flag`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @CountedFlag { shortName = "a" }
        int: Int
        @CountedFlag { shortName = "b" }
        int8: Int8
        @CountedFlag { shortName = "c" }
        int16: Int16
        @CountedFlag { shortName = "d" }
        int32: Int32
        @CountedFlag { shortName = "e" }
        uint: UInt
        @CountedFlag { shortName = "f" }
        uint8: UInt8
        @CountedFlag { shortName = "g" }
        uint16: UInt16
        @CountedFlag { shortName = "i" }
        uint32: UInt32
      }
    """
            .trimIndent(),
      )

    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf("-abbcccddddeeeeeffffffgggggggiiiiiiii"),
      )
    assertThat(output)
      .isEqualTo(
        """
        int = 1
        int8 = 2
        int16 = 3
        int32 = 4
        uint = 5
        uint8 = 6
        uint16 = 7
        uint32 = 8
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `test transformAll`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Flag {
          multiple = true
          transformAll = (values) -> values.fold(0, (res, acc) -> res + acc)
        }
        foo: Int
      }
    """
            .trimIndent(),
      )

    val output =
      runToStdout(
        CliBaseOptions(sourceModules = listOf(moduleUri)),
        listOf("--foo=1", "--foo=5", "--foo=8"),
      )
    assertThat(output)
      .isEqualTo(
        """
        foo = 14
        
        """
          .trimIndent()
      )
  }

  @Test
  fun `completion candidates`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        none: String?
        enum: *"a" | "b" | "c"
        @Flag { completionCandidates = "paths" }
        path: String?
        @Flag { completionCandidates { "foo"; "bar"; "baz" } }
        explicit: String?
        @Argument
        enumArg: *"a" | "b" | "c"
        @Argument { completionCandidates = "paths" }
        pathArg: String
        @Argument { completionCandidates { "foo"; "bar"; "baz" } }
        explicitArg: String
      }
    """
            .trimIndent(),
      )
    val exc =
      assertThrows<PrintCompletionMessage> {
        runToStdout(
          CliBaseOptions(sourceModules = listOf(moduleUri)),
          listOf("a", "foo", "bar", "shell-completion", "bash"),
        )
      }
    assertThat(exc.message)
      .contains(
        """
    "--none")
      ;;
    "--enum")
      COMPREPLY=($(compgen -W 'a b c' -- "${'$'}{word}"))
      ;;
    "--path")
       __complete_files "${'$'}{word}"
      ;;
    "--explicit")
      COMPREPLY=($(compgen -W 'bar baz foo' -- "${'$'}{word}"))
      ;;
    "--help")
      ;;
    "enumArg")
      COMPREPLY=($(compgen -W '' -- "${'$'}{word}"))
      ;;
    "pathArg")
       __complete_files "${'$'}{word}"
      ;;
    "explicitArg")
      COMPREPLY=($(compgen -W 'bar baz foo' -- "${'$'}{word}"))
      ;;"""
      )
  }
}

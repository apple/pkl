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
package org.pkl.core.runtime

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.commons.writeString
import org.pkl.core.CommandSpec
import org.pkl.core.Evaluator
import org.pkl.core.ModuleSource.uri
import org.pkl.core.PklException

class CommandSpecParserTest {
  companion object {
    private val renderOptions =
      """
      extends "pkl:Command"
      import "pkl:Command"
      
      options: Options
      
      output {
        value = options
      }
      
    """
        .trimIndent()

    private val evaluator = Evaluator.preconfigured()
  }

  @TempDir private lateinit var tempDir: Path

  private fun writePklFile(fileName: String, contents: String): URI {
    tempDir.resolve(fileName).createParentDirectories()
    return tempDir.resolve(fileName).writeString(contents).toUri()
  }

  private fun parse(moduleUri: URI): CommandSpec {
    var spec: CommandSpec? = null
    evaluator.evaluateCommand(uri(moduleUri), setOf("root-dir")) { spec = it }
    return spec!!
  }

  @Test
  fun `command module does not amend pkl_Command`() {
    val moduleUri = writePklFile("cmd.pkl", "")

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("Expected value of type `pkl.Command`, but got type")
  }

  @Test
  fun `options property assigned`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        """
          extends "pkl:Command"
          options = new {}
        """
          .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("options = ")
    assertThat(exc.message).contains("Commands must not assign or amend property `options`.")
  }

  @Test
  fun `options property amended`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        """
          extends "pkl:Command"
          options {}
        """
          .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("options {")
    assertThat(exc.message).contains("Commands must not assign or amend property `options`.")
  }

  @Test
  fun `parent property assigned`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        """
          extends "pkl:Command"
          parent = new {}
        """
          .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("parent = ")
    assertThat(exc.message).contains("Commands must not assign or amend property `parent`.")
  }

  @Test
  fun `parent property amended`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        """
          extends "pkl:Command"
          parent {}
        """
          .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("parent {")
    assertThat(exc.message).contains("Commands must not assign or amend property `parent`.")
  }

  @Test
  fun `options type annotation does not reference class`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        """
          extends "pkl:Command"
          options: "nope" | "try again"
        """
          .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("options: \"nope\" | \"try again\"")
    assertThat(exc.message)
      .contains(
        "Type annotation `\"nope\" | \"try again\"` on `options` property in `pkl:Command` subclass must be a class type."
      )
  }

  @Test
  fun `options class is abstract`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        """
          extends "pkl:Command"
          options: Options
          abstract class Options {}
        """
          .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("abstract class Options {")
    assertThat(exc.message).contains("Command options class `cmd#Options` may not be abstract.")
  }

  @Test
  fun `command property value does not amend CommandInfo`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        """
          extends "pkl:Command"
          command = new Foo {}
          class Foo
        """
          .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("command = new Foo {}")
    assertThat(exc.message)
      .contains("Expected value of type `pkl.Command#CommandInfo`, but got type `cmd#Foo`.")
  }

  @Test
  fun `first annotation of the same type wins`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
          open class BaseOptions {
            /// foo in BaseOptions
            @Flag { shortName = "a" }
            foo: String
            
            /// bar in BaseOptions
            @Flag { shortName = "b" }
            bar: String
          }
          class Options extends BaseOptions {
            /// bar in Options
            @Flag { shortName = "x" }
            bar: String
            
            /// baz in Options
            @Flag { shortName = "y" }
            @CountedFlag { shortName = "z" }
            baz: Int
          }
        """
            .trimIndent(),
      )

    val spec = parse(moduleUri)

    // assert class overrides its superclass
    assertThat(spec.options.toList()[0]).isInstanceOf(CommandSpec.Flag::class.java)
    (spec.options.toList()[0] as CommandSpec.Flag).apply {
      assertThat(this.name).isEqualTo("bar")
      assertThat(this.shortName).isEqualTo("x")
      assertThat(this.helpText).isEqualTo("bar in Options")
    }

    // assert first flag annotation wins
    assertThat(spec.options.toList()[1]).isInstanceOf(CommandSpec.Flag::class.java)
    (spec.options.toList()[1] as CommandSpec.Flag).apply {
      assertThat(this.name).isEqualTo("baz")
      assertThat(this.shortName).isEqualTo("y")
      assertThat(this.helpText).isEqualTo("baz in Options")
    }

    // assert superclass options are inherited
    assertThat(spec.options.toList()[2]).isInstanceOf(CommandSpec.Flag::class.java)
    (spec.options.toList()[2] as CommandSpec.Flag).apply {
      assertThat(this.name).isEqualTo("foo")
      assertThat(this.shortName).isEqualTo("a")
      assertThat(this.helpText).isEqualTo("foo in BaseOptions")
    }
  }

  @Test
  fun `@Flag and @Argument on the same option`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Flag
        @Argument
        foo: String
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: String")
    assertThat(exc.message)
      .contains("Found both `@Flag` and `@Argument` annotations for options property `foo`.")
  }

  @Test
  fun `option with no type annotation`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo = "bar"
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo = \"bar\"")
    assertThat(exc.message).contains("No type annotation found for `foo` property.")
  }

  @Test
  fun `nullable option with default not allowed`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo: String? = "bar"
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: String? = \"bar\"")
    assertThat(exc.message)
      .contains("Unexpected option property `foo` with nullable type and default value")
  }

  @Test
  fun `option with union type containing non-string-literals`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo: "oops" | String
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: \"oops\" | String")
    assertThat(exc.message)
      .contains("Command option property `foo` has unsupported type `\"oops\" | String`.")
  }

  @Test
  fun `argument with default not allowed`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument
        foo: String = "bar"
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: String = \"bar\"")
    assertThat(exc.message).contains("Unexpected default value for `@Argument` property `foo`.")
  }

  @Test
  fun `nullable non-collection argument not allowed`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument
        foo: String?
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: String?")
    assertThat(exc.message)
      .contains("Unexpected nullable type for non-collection `@Argument` property `foo`.")
  }

  @Test
  fun `non-constant default values result in an optional flag with no default`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
          class Options {
            foo: String = "hi"
            bar: String = foo
            baz: Map<String, String> = Map()
            qux: Map<String, String> = baz
            quux: Int = 5
          }
        """
            .trimIndent(),
      )

    val spec = parse(moduleUri)

    assertThat(spec.options.toList()[0]).isInstanceOf(CommandSpec.Flag::class.java)
    (spec.options.toList()[0] as CommandSpec.Flag).apply {
      assertThat(this.name).isEqualTo("foo")
      assertThat(this.defaultValue).isEqualTo("hi")
    }

    assertThat(spec.options.toList()[1]).isInstanceOf(CommandSpec.Flag::class.java)
    (spec.options.toList()[1] as CommandSpec.Flag).apply {
      assertThat(this.name).isEqualTo("bar")
      assertThat(this.defaultValue).isNull()
    }

    assertThat(spec.options.toList()[2]).isInstanceOf(CommandSpec.Flag::class.java)
    (spec.options.toList()[2] as CommandSpec.Flag).apply {
      assertThat(this.name).isEqualTo("baz")
      assertThat(this.defaultValue).isNull()
    }

    assertThat(spec.options.toList()[3]).isInstanceOf(CommandSpec.Flag::class.java)
    (spec.options.toList()[3] as CommandSpec.Flag).apply {
      assertThat(this.name).isEqualTo("qux")
      assertThat(this.defaultValue).isNull()
    }

    assertThat(spec.options.toList()[4]).isInstanceOf(CommandSpec.Flag::class.java)
    (spec.options.toList()[4] as CommandSpec.Flag).apply {
      assertThat(this.name).isEqualTo("quux")
      assertThat(this.defaultValue).isEqualTo("5")
    }
  }

  @Test
  fun `flag with collision on --help`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        help: Boolean
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("help: Boolean")
    assertThat(exc.message).contains("Flag option `help` name collides with a reserved flag name.")
  }

  @Test
  fun `flag with collision on -h`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Flag { shortName = "h" }
        showHelp: Boolean
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("showHelp: Boolean")
    assertThat(exc.message)
      .contains("Flag option `showHelp` short name `h` collides with a reserved flag short name.")
  }

  @Test
  fun `flag with collision on reserved option name`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        `root-dir`: String
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("`root-dir`: String")
    assertThat(exc.message)
      .contains("Flag option `root-dir` name collides with a reserved flag name.")
  }

  @Test
  fun `multiple arguments with collection types not allowed`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Argument
        list: List<String>
        @Argument
        set: Set<String>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("class Options {")
    assertThat(exc.message)
      .contains("More than one repeated option annotated with `@Argument` found: `list` and `set`.")
    assertThat(exc.message).contains("Only one repeated argument is permitted per command.")
  }

  @Test
  fun `collection option with collection element type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo: List<List<"a" | "b">>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: List<List<\"a\" | \"b\">>")
    assertThat(exc.message)
      .contains("Command option property `foo` has unsupported element type `List<\"a\" | \"b\">`.")
  }

  @Test
  fun `collection option with map element type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo: List<Map<String, "a" | "b">>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: List<Map<String, \"a\" | \"b\">>")
    assertThat(exc.message)
      .contains(
        "Command option property `foo` has unsupported element type `Map<String, \"a\" | \"b\">`."
      )
  }

  @Test
  fun `map option with collection value type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo: Map<String, List<"a" | "b">>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: Map<String, List<\"a\" | \"b\">>")
    assertThat(exc.message)
      .contains("Command option property `foo` has unsupported value type `List<\"a\" | \"b\">`.")
  }

  @Test
  fun `map option with map value type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo: Map<String, Map<String, "a" | "b">>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: Map<String, Map<String, \"a\" | \"b\">>")
    assertThat(exc.message)
      .contains(
        "Command option property `foo` has unsupported value type `Map<String, \"a\" | \"b\">`."
      )
  }

  @Test
  fun `map option with collection key type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo: Map<Map<String, "a" | "b">, String>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: Map<Map<String, \"a\" | \"b\">, String>")
    assertThat(exc.message)
      .contains(
        "Command option property `foo` has unsupported key type `Map<String, \"a\" | \"b\">`."
      )
  }

  @Test
  fun `map option with map key type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo: Map<Map<String, "a" | "b">, String>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: Map<Map<String, \"a\" | \"b\">, String>")
    assertThat(exc.message)
      .contains(
        "Command option property `foo` has unsupported key type `Map<String, \"a\" | \"b\">`."
      )
  }

  @Test
  fun `map option with map key type allowed with convert`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @Flag { convert = (it) -> Pair("foo", "a") }
        foo: Map<Map<String, "a" | "b">, String>
      }
    """
            .trimIndent(),
      )

    assertDoesNotThrow { parse(moduleUri) }
  }

  @Test
  fun `unsupported option type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo: Foo
      }
      class Foo
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: Foo")
    assertThat(exc.message).contains("Command option property `foo` has unsupported type `Foo`.")
  }

  @Test
  fun `options constraints in all positions are erased`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        a: String(true)
        b: String?(true)
        c: String(true)?
        d: List<String(true)>
        e: List<String(true)>(true)
        f: List<String(true)>(true)?(true)
        g: (Map<String(true), String(true)>(true)?(true))(true)
      }
    """
            .trimIndent(),
      )

    parse(moduleUri)
  }

  @Test
  fun `conflicting subcommand names`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        """
      extends "pkl:Command"
      import "pkl:Command"
      command {
        subcommands {
          new Sub { command { name = "foo" } }
          new Sub { command { name = "foo" } }
        }
      }
      
      class Sub extends Command
    """
          .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("Command `cmd` has subcommands with conflicting name \"foo\".")
  }

  @Test
  fun `list or set option with no type arguments`() {
    for (type in listOf("List", "Set")) {
      val moduleUri =
        writePklFile(
          "cmd_$type.pkl",
          renderOptions +
            """
      class Options {
        foo: $type
      }
    """
              .trimIndent(),
        )

      val exc = assertThrows<PklException> { parse(moduleUri) }
      assertThat(exc.message).contains("foo: $type")
      assertThat(exc.message)
        .contains("Command option property `foo` has unsupported type `$type`.")
      assertThat(exc.message).contains("$type options must provide one type argument.")
    }
  }

  @Test
  fun `map option with no type arguments`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        foo: Map
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: Map")
    assertThat(exc.message).contains("Command option property `foo` has unsupported type `Map`.")
    assertThat(exc.message).contains("Map options must provide two type arguments.")
  }

  @Test
  fun `boolean flag with incorrect type`() {
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

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: String")
    assertThat(exc.message)
      .contains("Option `foo` with annotation `@BooleanFlag` has invalid type `String`.")
    assertThat(exc.message).contains("Expected type: `Boolean`")
  }

  @Test
  fun `counted flag with incorrect type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options {
        @CountedFlag
        foo: String
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: String")
    assertThat(exc.message)
      .contains("Option `foo` with annotation `@CountedFlag` has invalid type `String`.")
    assertThat(exc.message).contains("Expected type: `Int`")
  }
}

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
    evaluator.evaluateCommand(uri(moduleUri)) { spec = it }
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
          abstract class Options extends BaseOptions {}
        """
          .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("abstract class Options extends BaseOptions {")
    assertThat(exc.message)
      .contains(
        "Class `cmd#Options` for type annotation on `options` property in `pkl:Command` subclass must not be abstract."
      )
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
            @Flag { shortName = "z" }
            baz: String
          }
        """
            .trimIndent(),
      )

    val spec = parse(moduleUri)
    assertThat(spec.flags[0])
      .usingRecursiveComparison()
      .isEqualTo(
        CommandSpec.Flag(
          "bar",
          "x",
          CommandSpec.OptionType.Primitive(CommandSpec.OptionType.Primitive.Type.STRING, true),
          null,
          null,
          "bar in Options",
          false,
        )
      )
    assertThat(spec.flags[1])
      .usingRecursiveComparison()
      .isEqualTo(
        CommandSpec.Flag(
          "baz",
          "y",
          CommandSpec.OptionType.Primitive(CommandSpec.OptionType.Primitive.Type.STRING, true),
          null,
          null,
          "baz in Options",
          false,
        )
      )
    assertThat(spec.flags[2])
      .usingRecursiveComparison()
      .isEqualTo(
        CommandSpec.Flag(
          "foo",
          "a",
          CommandSpec.OptionType.Primitive(CommandSpec.OptionType.Primitive.Type.STRING, true),
          null,
          null,
          "foo in BaseOptions",
          false,
        )
      )
  }

  @Test
  fun `@Flag and @Argument on the same option`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
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
      class Options extends BaseOptions {
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
      class Options extends BaseOptions {
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
      class Options extends BaseOptions {
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
      class Options extends BaseOptions {
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
      class Options extends BaseOptions {
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
  fun `map argument not allowed`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
        @Argument
        foo: Map<String, String>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: Map<String, String>")
    assertThat(exc.message).contains("Unexpected `Map` type for `@Argument` property `foo`.")
  }

  @Test
  fun `non-constant default values result in an optional flag with no default`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
          class Options extends BaseOptions {
            foo: String = "hi"
            bar: String = foo
            baz: Map<String, String> = Map()
            qux: Map<String, String> = baz
          }
        """
            .trimIndent(),
      )

    val spec = parse(moduleUri)
    val str = CommandSpec.OptionType.Primitive(CommandSpec.OptionType.Primitive.Type.STRING, true)
    assertThat(spec.flags[0])
      .usingRecursiveComparison()
      .isEqualTo(
        CommandSpec.Flag(
          "foo",
          null,
          CommandSpec.OptionType.Primitive(CommandSpec.OptionType.Primitive.Type.STRING, false),
          "hi",
          null,
          null,
          false,
        )
      )
    assertThat(spec.flags[1])
      .usingRecursiveComparison()
      .isEqualTo(
        CommandSpec.Flag(
          "bar",
          null,
          CommandSpec.OptionType.Primitive(CommandSpec.OptionType.Primitive.Type.STRING, false),
          null,
          null,
          null,
          false,
        )
      )
    assertThat(spec.flags[2])
      .usingRecursiveComparison()
      .isEqualTo(
        CommandSpec.Flag(
          "baz",
          null,
          CommandSpec.OptionType.Map(str, str, false),
          null,
          null,
          null,
          false,
        )
      )
    assertThat(spec.flags[3])
      .usingRecursiveComparison()
      .isEqualTo(
        CommandSpec.Flag(
          "qux",
          null,
          CommandSpec.OptionType.Map(str, str, false),
          null,
          null,
          null,
          false,
        )
      )
  }

  @Test
  fun `flag with collision on --help`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
        help: Boolean
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("help: Boolean")
    assertThat(exc.message)
      .contains("Flag option `help` may not have name \"help\" or short name \"h\".")
  }

  @Test
  fun `flag with collision on -h`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
        @Flag { shortName = "h" }
        showHelp: Boolean
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("showHelp: Boolean")
    assertThat(exc.message)
      .contains("Flag option `showHelp` may not have name \"help\" or short name \"h\".")
  }

  @Test
  fun `multiple arguments with collection types not allowed`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
        @Argument
        list: List<String>
        @Argument
        set: Set<String>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("class Options extends BaseOptions {")
    assertThat(exc.message)
      .contains("More than one `List` or `Set` property annotated with `@Argument` found.")
  }

  @Test
  fun `collection option with collection element type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
        foo: List<List<"a" | "b">>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: List<List<\"a\" | \"b\">>")
    assertThat(exc.message)
      .contains("Command option property `foo` has unsupported element type `List<\"a\"|\"b\">`.")
  }

  @Test
  fun `collection option with map element type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
        foo: List<Map<String, "a" | "b">>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: List<Map<String, \"a\" | \"b\">>")
    assertThat(exc.message)
      .contains(
        "Command option property `foo` has unsupported element type `Map<String, \"a\"|\"b\">`."
      )
  }

  @Test
  fun `map option with collection value type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
        foo: Map<String, List<"a" | "b">>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: Map<String, List<\"a\" | \"b\">>")
    assertThat(exc.message)
      .contains("Command option property `foo` has unsupported value type `List<\"a\"|\"b\">`.")
  }

  @Test
  fun `map option with map value type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
        foo: Map<String, Map<String, "a" | "b">>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: Map<String, Map<String, \"a\" | \"b\">>")
    assertThat(exc.message)
      .contains(
        "Command option property `foo` has unsupported value type `Map<String, \"a\"|\"b\">`."
      )
  }

  @Test
  fun `map option with collection key type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
        foo: Map<Map<String, "a" | "b">, String>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: Map<Map<String, \"a\" | \"b\">, String>")
    assertThat(exc.message)
      .contains(
        "Command option property `foo` has unsupported key type `Map<String, \"a\"|\"b\">`."
      )
  }

  @Test
  fun `map option with map key type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
        foo: Map<Map<String, "a" | "b">, String>
      }
    """
            .trimIndent(),
      )

    val exc = assertThrows<PklException> { parse(moduleUri) }
    assertThat(exc.message).contains("foo: Map<Map<String, \"a\" | \"b\">, String>")
    assertThat(exc.message)
      .contains(
        "Command option property `foo` has unsupported key type `Map<String, \"a\"|\"b\">`."
      )
  }

  @Test
  fun `unsupported option type`() {
    val moduleUri =
      writePklFile(
        "cmd.pkl",
        renderOptions +
          """
      class Options extends BaseOptions {
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
      class Options extends BaseOptions {
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
      class Options extends BaseOptions {
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
      class Options extends BaseOptions {
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
}

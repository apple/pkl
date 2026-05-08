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
package org.pkl.core;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.pkl.core.util.Nullable;

/**
 * A specification parsed from a valid {@code pkl:Command} submodule.
 *
 * @param name the name of the (sub)command.
 * @param helpText the CLI help text of the (sub)command.
 * @param hidden whether the subcommand should be hidden from CLI help.
 * @param noOp whether the (sub)command cannot be directly be executed (if true, it must have
 *     subcommands).
 * @param options specifications for the (sub)command's CLI options.
 * @param subcommands specifications for the (sub)command's subcommands.
 * @param apply a function that transforms command execution {@link State}, applying the parsed
 *     options from this command.
 */
public record CommandSpec(
    String name,
    @Nullable String helpText,
    boolean hidden,
    boolean noOp,
    Iterable<Option> options,
    List<CommandSpec> subcommands,
    ApplyFunction apply) {

  /** A command line option. */
  public sealed interface Option {
    /** Primary name of the option with no type-specific punctuation. */
    String name();

    /**
     * All option names include type-specific punctuation (e.g. {@code -} for flags). If multiple,
     * the primary name is listed last.
     */
    String[] getNames();

    /**
     * An exception thrown by {@link Flag} and {@link Argument} transform functions to indicate that
     * no value was provided for a required option.
     */
    class MissingOption extends RuntimeException {
      public MissingOption() {}
    }

    /**
     * An exception thrown by {@link Flag} and {@link Argument} transform functions to indicate that
     * a provided option value is not valid.
     */
    class BadValue extends RuntimeException {
      public BadValue(String message) {
        super(message);
      }

      /** Creates an exception for type mismatches. */
      public static BadValue invalid(String value, String type) {
        return new BadValue(String.format("%s is not a valid %s", value, type));
      }

      /** Creates an exception for invalid key=value strings. */
      public static BadValue badKeyValue(String value) {
        return new BadValue(String.format("%s is not a valid key=value pair", value));
      }

      /** Creates an exception for an invalid value with a set of known valid values. */
      public static BadValue invalidChoice(String value, List<String> choices) {
        return new BadValue(
            String.format(
                "invalid choice: %s. (choose from %s)", value, String.join(", ", choices)));
      }

      /** Creates an exception for an invalid value with a single known valid value. */
      public static BadValue invalidChoice(String value, String choice) {
        return new BadValue(String.format("invalid choice: %s. (choose from %s)", value, choice));
      }
    }
  }

  /** Specifications for how shells should auto-complete an {@link Option}'s value */
  public abstract static sealed class CompletionCandidates {
    /** Shells should auto-complete file paths for this option. */
    public static final CompletionCandidates PATH = new StaticCompletionCandidates();

    /** Shells should auto-complete a set of static values for this option. */
    public static final class Fixed extends CompletionCandidates {
      private final Set<String> values;

      public Fixed(Set<String> values) {
        this.values = values;
      }

      /** Specific static values offered by shell auto-complete. */
      public Set<String> getValues() {
        return values;
      }
    }

    private static final class StaticCompletionCandidates extends CompletionCandidates {}
  }

  /**
   * A specification for a single CLI flag.
   *
   * <p>A flag's name becomes its CLI flag (prefixed with {@code --}) and may optionally have a
   * single-character short name (prefixed with {@code -}). A flag is always followed by a single
   * argument that becomes its value.
   *
   * @param name the primary (long) name of the flag.
   * @param helpText the flag's CLI help text.
   * @param showAsRequired determines whether the CLI help should show the flag as required.
   * @param transformEach a function that transforms a flag's raw value (and working dir URI) into a
   *     valid Pkl value.
   * @param transformAll a function that transforms the list of parsed Pkl values for the flag into
   *     a single final value.
   * @param completionCandidates specifies if/how the shell should auto-complete the flag value.
   * @param shortName the single-character abbreviated form of the flag.
   * @param metavar the text used to substitute for the flag's value in CLI help text.
   * @param hidden whether the flag should be hidden from CLI help.
   * @param defaultValue a string representation of the flag's default value for use in CLI help.
   */
  public record Flag(
      String name,
      @Nullable String helpText,
      boolean showAsRequired,
      BiFunction<String, URI, Object> transformEach,
      BiFunction<List<Object>, URI, Object> transformAll,
      @Nullable CompletionCandidates completionCandidates,
      @Nullable String shortName,
      String metavar,
      boolean hidden,
      @Nullable String defaultValue)
      implements Option {
    @Override
    public String[] getNames() {
      return shortName == null
          ? new String[] {"--" + name}
          : new String[] {"-" + shortName, "--" + name};
    }
  }

  /**
   * A specification for a single on/off CLI flag.
   *
   * <p>A boolean flag's name becomes its CLI flag (prefixed with {@code --}) and may optionally
   * have a single-character short name (prefixed with {@code -}). An additional name for the
   * inverted form of the flag (prefixed with {@code --no-} is also added. A boolean flag accepts no
   * value argument.
   *
   * @param name the primary (long) name of the flag.
   * @param helpText the flag's CLI help text.
   * @param shortName the single-character abbreviated form of the flag.
   * @param hidden whether the flag should be hidden from CLI help.
   * @param defaultValue a string representation of the flag's default value for use in CLI help.
   */
  public record BooleanFlag(
      String name,
      @Nullable String helpText,
      @Nullable String shortName,
      boolean hidden,
      @Nullable Boolean defaultValue)
      implements Option {
    @Override
    public String[] getNames() {
      return shortName == null
          ? new String[] {"--" + name}
          : new String[] {"-" + shortName, "--" + name};
    }
  }

  /**
   * A specification for a single CLI flag that counts how many times it is used.
   *
   * <p>A counted flag's name becomes its CLI flag (prefixed with {@code --}) and may optionally
   * have a single-character short name (prefixed with {@code -}). A counted flag accepts no value
   * argument.
   *
   * @param name the primary (long) name of the flag.
   * @param helpText the flag's CLI help text.
   * @param shortName the single-character abbreviated form of the flag.
   * @param hidden whether the flag should be hidden from CLI help.
   */
  public record CountedFlag(
      String name, @Nullable String helpText, @Nullable String shortName, boolean hidden)
      implements Option {
    @Override
    public String[] getNames() {
      return shortName == null
          ? new String[] {"--" + name}
          : new String[] {"-" + shortName, "--" + name};
    }
  }

  /**
   * A specification for a single CLI positional argument.
   *
   * @param name the name of the argument.
   * @param helpText the argument's CLI help text.
   * @param transformEach a function that transforms a argument's raw value (and working dir URI)
   *     into a valid Pkl value.
   * @param transformAll a function that transforms the list of parsed Pkl values for the argument
   *     into a single final value.
   * @param completionCandidates specifies if/how the shell should auto-complete the argument value.
   * @param repeated whether the argument accepts multiple values.
   */
  public record Argument(
      String name,
      @Nullable String helpText,
      BiFunction<String, URI, Object> transformEach,
      BiFunction<List<Object>, URI, Object> transformAll,
      @Nullable CompletionCandidates completionCandidates,
      boolean repeated)
      implements Option {
    @Override
    public String[] getNames() {
      return new String[] {name};
    }
  }

  /** A function used to transform command {@link State} as arguments are parsed. */
  public interface ApplyFunction {
    State apply(Map<String, Object> options, @Nullable State parent);
  }

  /**
   * The execution state of a command.
   *
   * @param contents the opaque, implementation-defined state of the command. It must not be
   *     directly inspected or modified outside of an {@link ApplyFunction}.
   * @param reify an implementation-defined function that transforms some state contents to a {@link
   *     Result}.
   */
  public record State(Object contents, Function<Object, Result> reify) {
    /** Evaluate the state to produce its output {@link Result}. */
    public Result evaluate() {
      return reify.apply(contents);
    }
  }

  /**
   * The result of executing a command.
   *
   * @param outputBytes the executed command's {@code output.bytes}.
   * @param outputFiles the executed command's {@code output.files}.
   */
  public record Result(byte[] outputBytes, Map<String, FileOutput> outputFiles) {}
}

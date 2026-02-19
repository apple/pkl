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

public record CommandSpec(
    String name,
    @Nullable String helpText,
    boolean hidden,
    boolean noOp,
    Iterable<Option> options,
    List<CommandSpec> subcommands,
    ApplyFunction apply) {

  public sealed interface Option {
    String name();

    String[] getNames();

    class MissingOption extends RuntimeException {
      public MissingOption() {}
    }

    class BadValue extends RuntimeException {
      public BadValue(String message) {
        super(message);
      }

      public static BadValue invalid(String value, String type) {
        return new BadValue(String.format("%s is not a valid %s", value, type));
      }

      public static BadValue badKeyValue(String value) {
        return new BadValue(String.format("%s is not a valid key=value pair", value));
      }

      public static BadValue invalidChoice(String value, List<String> choices) {
        return new BadValue(
            String.format(
                "invalid choice: %s. (choose from %s)", value, String.join(", ", choices)));
      }

      public static BadValue invalidChoice(String value, String choice) {
        return new BadValue(String.format("invalid choice: %s. (choose from %s)", value, choice));
      }
    }
  }

  public abstract static sealed class CompletionCandidates {
    public static final CompletionCandidates PATH = new StaticCompletionCandidates();

    public static final class Fixed extends CompletionCandidates {
      private final Set<String> values;

      public Fixed(Set<String> values) {
        this.values = values;
      }

      public Set<String> getValues() {
        return values;
      }
    }

    private static final class StaticCompletionCandidates extends CompletionCandidates {}
  }

  public record Flag(
      String name,
      @Nullable String helpText,
      boolean showAsRequired,
      BiFunction<String, URI, Object> transformEach,
      Function<List<Object>, Object> transformAll,
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

  public record Argument(
      String name,
      @Nullable String helpText,
      BiFunction<String, URI, Object> transformEach,
      Function<List<Object>, Object> transformAll,
      @Nullable CompletionCandidates completionCandidates,
      boolean repeated)
      implements Option {
    @Override
    public String[] getNames() {
      return new String[] {name};
    }
  }

  public interface ApplyFunction {
    State apply(Map<String, Object> options, @Nullable State parent);
  }

  public record State(Object contents, Function<Object, Result> reify) {
    public Result evaluate() {
      return reify.apply(contents);
    }
  }

  public record Result(byte[] outputBytes, Map<String, FileOutput> outputFiles) {}
}

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
package org.pkl.core;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.pkl.core.util.Nullable;

public record CommandSpec(
    String name,
    List<String> aliases,
    @Nullable String description,
    boolean hide,
    boolean noOp,
    List<Flag> flags,
    List<Argument> arguments,
    List<CommandSpec> subcommands,
    ApplyFunction apply) {
  
  public record Flag(
      String name,
      @Nullable String shortName,
      @Nullable String separator,
      @Nullable String keyValueSeparator,
      Object type, // TODO
      @Nullable Object defaultValue,
      @Nullable ParseOptionFunction parse,
      @Nullable String description,
      boolean required,
      boolean hide) {}

  public record Argument(
      String name,
      Object type, // TODO
      @Nullable ParseOptionFunction parse,
      @Nullable String description) {}

  public interface ParseOptionFunction {
    Object parse(String value);
  }

  public interface ApplyFunction {
    State apply(Map<String, Object> options, @Nullable State parent);
  }

  public record State(Object moduleNode, Function<Object, Result> reify) {
    public Result evaluate() {
      return reify.apply(moduleNode);
    }
  }

  public record Result(byte[] outputBytes, Map<String, FileOutput> outputFiles) {}
}

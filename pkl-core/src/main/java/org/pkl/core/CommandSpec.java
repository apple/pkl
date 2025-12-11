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
      OptionType type,
      @Nullable Object defaultValue,
      @Nullable ParseOptionFunction parse,
      @Nullable String description,
      boolean hide) {}

  public record Argument(
      String name,
      OptionType type,
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

  public abstract static sealed class OptionType {
    private final boolean required;

    protected OptionType(boolean required) {
      this.required = required;
    }

    public boolean isRequired() {
      return required;
    }

    public static final class Primitive extends OptionType {
      public enum Type {
        NUMBER,
        FLOAT,
        INT,
        INT8,
        INT16,
        INT32,
        UINT,
        UINT8,
        UINT16,
        UINT32,
        BOOLEAN,
        STRING,
        CHAR
      }

      private final Type type;

      public Primitive(Type type, boolean required) {
        super(required);
        this.type = type;
      }

      public Type getType() {
        return type;
      }
    }

    public static final class Enum extends OptionType {
      private final List<String> choices;

      public Enum(List<String> choices, boolean required) {
        super(required);
        this.choices = choices;
      }

      public List<String> getChoices() {
        return choices;
      }
    }

    public static final class Collection extends OptionType {
      public enum Type {
        LIST,
        SET
      }

      private final Type type;
      private final OptionType valueType;

      public Collection(Type type, OptionType valueType, boolean required) {
        super(required);
        this.type = type;
        this.valueType = valueType;
      }

      public Type getType() {
        return type;
      }

      public OptionType getValueType() {
        return valueType;
      }
    }

    public static final class Map extends OptionType {
      private final OptionType keyType;
      private final OptionType valueType;

      public Map(OptionType keyType, OptionType valueType, boolean required) {
        super(required);
        this.keyType = keyType;
        this.valueType = valueType;
      }

      public OptionType getKeyType() {
        return keyType;
      }

      public OptionType getValueType() {
        return valueType;
      }
    }
  }
}

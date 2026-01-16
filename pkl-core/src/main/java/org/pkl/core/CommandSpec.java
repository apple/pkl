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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    boolean isImport();

    Object parse(String value);
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
        NUMBER("Number"),
        FLOAT("Float"),
        INT("Int"),
        INT8("Int8"),
        INT16("Int16"),
        INT32("Int32"),
        UINT("UInt"),
        UINT8("UInt8"),
        UINT16("UInt16"),
        UINT32("UInt32"),
        BOOLEAN("Boolean"),
        STRING("String"),
        CHAR("Char");

        private final String pklTypeName;

        Type(String pklTypeName) {
          this.pklTypeName = pklTypeName;
        }

        @Override
        public String toString() {
          return pklTypeName;
        }
      }

      private final Type type;

      public Primitive(Type type, boolean required) {
        super(required);
        this.type = type;
      }

      public Type getType() {
        return type;
      }

      @Override
      public String toString() {
        return type.toString();
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

      @Override
      public String toString() {
        return choices.stream().map((it) -> "\"" + it + "\"").collect(Collectors.joining("|"));
      }
    }

    public static final class Collection extends OptionType {
      public enum Type {
        LIST("List"),
        SET("Set");

        private final String pklTypeName;

        Type(String pklTypeName) {
          this.pklTypeName = pklTypeName;
        }

        @Override
        public String toString() {
          return pklTypeName;
        }
      }

      private final Type type;
      private final OptionType valueType;

      public Collection(Type type, OptionType valueType, boolean required) {
        super(required);
        assert valueType instanceof Primitive || valueType instanceof Enum;
        this.type = type;
        this.valueType = valueType;
      }

      public Type getType() {
        return type;
      }

      public OptionType getValueType() {
        return valueType;
      }

      @Override
      public String toString() {
        return String.format("%s<%s>", type.toString(), valueType);
      }
    }

    public static final class Map extends OptionType {
      private final OptionType keyType;
      private final OptionType valueType;

      public Map(OptionType keyType, OptionType valueType, boolean required) {
        super(required);
        assert keyType instanceof Primitive || keyType instanceof Enum;
        assert valueType instanceof Primitive || valueType instanceof Enum;
        this.keyType = keyType;
        this.valueType = valueType;
      }

      public OptionType getKeyType() {
        return keyType;
      }

      public OptionType getValueType() {
        return valueType;
      }

      @Override
      public String toString() {
        return String.format("Map<%s, %s>", keyType, valueType);
      }
    }
  }
}

/*
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.config.java.mapper;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import org.pkl.core.DataSizeUnit;
import org.pkl.core.DurationUnit;
import org.pkl.core.PClassInfo;
import org.pkl.core.util.CodeGeneratorUtils;
import org.pkl.core.util.CollectionUtils;

final class PStringToEnum implements ConverterFactory {
  @Override
  public Optional<Converter<?, ?>> create(PClassInfo<?> sourceType, Type targetType) {
    var rawTargetType = Reflection.toRawType(targetType);
    if (sourceType != PClassInfo.String || !rawTargetType.isEnum()) return Optional.empty();

    return Optional.of(new ConverterImpl(rawTargetType));
  }

  private static final class ConverterImpl implements Converter<String, Enum<?>> {
    private final Class<?> enumType;
    private final Map<String, Enum<?>> enumValuesByName;

    private ConverterImpl(Class<?> enumType) {
      this.enumType = enumType;
      var values = (Enum<?>[]) enumType.getEnumConstants();
      enumValuesByName = CollectionUtils.newConcurrentHashMap(values.length);
      // special-case: enums in the standard library have a different name compared to the Pkl
      // string
      if (enumType == DataSizeUnit.class) {
        for (var value : values) {
          var unit = (DataSizeUnit) value;
          enumValuesByName.put(CodeGeneratorUtils.toEnumConstantName(unit.getSymbol()), value);
        }
      } else if (enumType == DurationUnit.class) {
        for (var value : values) {
          var unit = (DurationUnit) value;
          enumValuesByName.put(CodeGeneratorUtils.toEnumConstantName(unit.getSymbol()), value);
        }
      } else {
        for (Enum<?> value : values) {
          enumValuesByName.put(value.name(), value);
        }
      }
    }

    @Override
    public Enum<?> convert(String value, ValueMapper valueMapper) {
      var enumValue = enumValuesByName.get(value);
      if (enumValue == null) {
        enumValue = enumValuesByName.get(CodeGeneratorUtils.toEnumConstantName(value));
        if (enumValue != null) {
          enumValuesByName.put(value, enumValue);
        }
      }
      if (enumValue != null) {
        return enumValue;
      }
      throw new ConversionException(
          String.format(
              "Cannot convert String `%s` to Enum value of type `%s`.",
              value, enumType.getTypeName()));
    }
  }
}

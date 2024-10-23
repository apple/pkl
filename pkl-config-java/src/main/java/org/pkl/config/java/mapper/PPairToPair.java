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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import org.pkl.core.PClassInfo;
import org.pkl.core.Pair;
import org.pkl.core.util.Nullable;

final class PPairToPair implements ConverterFactory {
  @Override
  public Optional<Converter<?, ?>> create(PClassInfo<?> sourceType, Type targetType) {
    if (sourceType != PClassInfo.Pair) return Optional.empty();

    var targetClass = Reflection.toRawType(targetType);
    if (!Pair.class.isAssignableFrom(targetClass)) {
      return Optional.empty();
    }

    var pairType = (ParameterizedType) Reflection.getExactSupertype(targetType, Pair.class);
    return Optional.of(
        new ConverterImpl<>(
            pairType.getActualTypeArguments()[0], pairType.getActualTypeArguments()[1]));
  }

  private static class ConverterImpl<F, S> implements Converter<Pair<Object, Object>, Pair<F, S>> {
    private final Type firstTargetType;
    private final Type secondTargetType;

    private PClassInfo<Object> firstCachedType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, F> firstCachedConverter;

    private PClassInfo<Object> secondCachedType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, S> secondCachedConverter;

    public ConverterImpl(Type firstTargetType, Type secondTargetType) {
      this.firstTargetType = firstTargetType;
      this.secondTargetType = secondTargetType;
    }

    @Override
    public Pair<F, S> convert(Pair<Object, Object> value, ValueMapper valueMapper) {
      var first = value.getFirst();
      if (!firstCachedType.isExactClassOf(first)) {
        firstCachedType = PClassInfo.forValue(first);
        firstCachedConverter = valueMapper.getConverter(firstCachedType, firstTargetType);
      }

      var second = value.getSecond();
      if (!secondCachedType.isExactClassOf(second)) {
        secondCachedType = PClassInfo.forValue(second);
        secondCachedConverter = valueMapper.getConverter(secondCachedType, secondTargetType);
      }

      assert firstCachedConverter != null;
      assert secondCachedConverter != null;
      return new Pair<>(
          firstCachedConverter.convert(first, valueMapper),
          secondCachedConverter.convert(second, valueMapper));
    }
  }
}

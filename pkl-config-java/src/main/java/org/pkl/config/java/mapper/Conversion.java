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
import org.pkl.core.PClassInfo;

/**
 * Describes a conversion from a Pkl source type to a (possibly parameterized) Java target type,
 * performed by the given {@link Converter}.
 *
 * @param <S> Java type representing the Pkl source type
 * @param <T> Java target type
 */
public final class Conversion<S, T> {
  public final PClassInfo<S> sourceType;
  public final Type targetType;
  public final Converter<? super S, ? extends T> converter;

  private Conversion(
      PClassInfo<S> sourceType, Type targetType, Converter<? super S, ? extends T> converter) {
    this.sourceType = sourceType;
    this.targetType = targetType;
    this.converter = converter;
  }

  /**
   * Creates a conversion from the given Pkl source type to the given (possibly parameterized) Java
   * type, using the given converter.
   */
  public static <S, T> Conversion<S, T> of(
      PClassInfo<S> sourceType, Type targetType, Converter<? super S, ? extends T> converter) {
    return new Conversion<>(sourceType, targetType, converter);
  }

  /**
   * Creates a conversion from the given Pkl source type to the given non-parameterized Java type,
   * using the given converter. This overload is provided to allow for better type inference.
   */
  public static <S, T> Conversion<S, T> of(
      PClassInfo<S> sourceType, Class<T> targetType, Converter<? super S, ? extends T> converter) {
    return new Conversion<>(sourceType, targetType, converter);
  }
}

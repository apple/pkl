/**
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
import org.pkl.core.PModule;

/**
 * Automatically converts Pkl objects to Java objects. Use {@link ValueMapperBuilder} to create an
 * instance of this type, configured according to your needs.
 */
public interface ValueMapper {
  /** Shorthand for {@code ValueMapperBuilder.preconfigured().build()}. */
  static ValueMapper preconfigured() {
    return ValueMapperBuilder.preconfigured().build();
  }

  /**
   * Converts the given Pkl object to the given Java target type. The Pkl object can be an entire
   * {@link PModule} or any value contained therein. See {@link PClassInfo#forValue} for which Java
   * types are used to represent Pkl objects.
   *
   * <p>When mapping to a generic target type, a fully parameterized type needs to be passed, e.g.
   * {@code List<String>}. Parameterized type literals can be created using {@link Types}, e.g.
   * {@code Types.listOf(String.class)}.
   *
   * <p>If an error occurs during conversion, or if {@link ValueMapper} does not know how to convert
   * from the given object to the given target type, a {@link ConversionException} is thrown.
   */
  <S, T> T map(S model, Type targetType);

  /**
   * Same as {@link #map(Object, Type)}, except that the target type is narrowed from {@link Type}
   * to {@link Class} to allow for better type inference.
   */
  default <S, T> T map(S model, Class<T> targetType) {
    return map(model, (Type) targetType);
  }

  /**
   * Returns the converter with the given source and target types. Throws {@link
   * ConversionException} if no such converter exists.
   */
  <S, T> Converter<S, T> getConverter(PClassInfo<S> sourceType, Type targetType);

  /**
   * Same as {@link #getConverter(PClassInfo, Type)}, except that the target type is narrowed from
   * {@link Type} to {@link Class} to allow for better type inference.
   */
  default <S, T> Converter<S, T> getConverter(PClassInfo<S> sourceType, Class<T> targetType) {
    return getConverter(sourceType, (Type) targetType);
  }

  /**
   * Returns a value mapper builder that, unless further configured, will build value mappers with
   * the same configuration as this value mapper. In other words, this is the inverse operation of
   * {@link ValueMapperBuilder#build()}, except that a new builder is returned.
   */
  ValueMapperBuilder toBuilder();
}

/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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

/**
 * Converter for a particular source and target type.
 *
 * @param <S> the converter's source type
 * @param <T> the converter's target type
 */
@FunctionalInterface
public interface Converter<S, T> {
  /**
   * Converts the given value. The given {@link ValueMapper} can be used to convert nested values of
   * composite values (objects, collections, etc.).
   */
  T convert(S value, ValueMapper valueMapper);

  /** Returns an identity converter for the requested type. */
  static <S> Converter<S, S> identity() {
    return (value, valueMapper) -> value;
  }
}

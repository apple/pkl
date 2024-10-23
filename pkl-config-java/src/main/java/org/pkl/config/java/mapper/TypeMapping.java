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

import java.lang.reflect.Modifier;
import org.pkl.core.util.Nullable;

/**
 * Maps a type requested during conversion to the implementation type to be instantiated. The
 * requested type is often an interface type. The implementation type is always a class type. A
 * typical example is mapping {@link java.util.List} to {@link java.util.ArrayList}.
 */
// kept simple for now
// if we wanted to have more sophisticated mappings, we could:
// * support mapping factories/strategies (cf. ConverterFactory/Converter)
// * support parameterized mappings (e.g. Set<MyEnum> -> EnumSet<MyEnum>)
public final class TypeMapping<S, T extends S> {
  public final Class<S> requestedType;
  public final Class<T> implementationType;

  private TypeMapping(Class<S> requestedType, Class<T> implementationType) {
    if (Modifier.isAbstract(implementationType.getModifiers())) {
      throw new IllegalArgumentException(
          String.format(
              "`implementationType` must not be abstract, but `%s` is.",
              implementationType.getTypeName()));
    }
    if (!requestedType.isAssignableFrom(implementationType)) {
      throw new IllegalArgumentException(
          String.format(
              "`implementationType` must be assignable to `requestedType`, but `%s` is not assignable to `%s`.",
              implementationType.getTypeName(), requestedType.getTypeName()));
    }
    if (requestedType.isArray() || implementationType.isArray()) {
      throw new IllegalArgumentException("Type mappings are not supported for array types.");
    }
    this.requestedType = requestedType;
    this.implementationType = implementationType;
  }

  public static <S, T extends S> TypeMapping<S, T> of(
      Class<S> requestedType, Class<T> implementationType) {
    return new TypeMapping<>(requestedType, implementationType);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof TypeMapping<?, ?> other)) return false;
    return requestedType == other.requestedType && implementationType == other.implementationType;
  }

  @Override
  public int hashCode() {
    return requestedType.hashCode() * 31 + implementationType.hashCode();
  }

  @Override
  public String toString() {
    return String.format(
        "TypeMapping(%s, %s)", requestedType.getTypeName(), implementationType.getTypeName());
  }
}

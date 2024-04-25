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

import io.leangen.geantyref.TypeArgumentNotInBoundException;
import io.leangen.geantyref.TypeFactory;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import org.pkl.core.Pair;

/**
 * A factory for parameterized type literals such as {@code List<String>} or {@code MyClass<Foo,
 * Bar>}. Used to express the desired target type in {@link ValueMapper#map(Object, Type)}.
 */
public final class Types {
  private Types() {}

  public static ParameterizedType parameterizedType(Class<?> rawType, Type... typeArguments) {
    var typeParamsCount = rawType.getTypeParameters().length;
    if (typeParamsCount == 0) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot parameterize `%s` because it does not have any type parameters.",
              rawType.getTypeName()));
    }
    if (typeArguments.length != typeParamsCount) {
      throw new IllegalArgumentException(
          String.format(
              "Expected %d type arguments for `%s`, but got %d.",
              typeParamsCount, rawType.getTypeName(), typeArguments.length));
    }
    for (Type arg : typeArguments) {
      if (arg instanceof Class<?> clazz) {
        if (clazz.isPrimitive()) {
          throw new IllegalArgumentException(
              String.format(
                  "`%s.class` is not a valid type argument. Did you mean `%s.class`?",
                  clazz, Reflection.toWrapperType(clazz).getSimpleName()));
        }
      }
    }
    try {
      return (ParameterizedType) TypeFactory.parameterizedClass(rawType, typeArguments);
    } catch (TypeArgumentNotInBoundException e) {
      throw new IllegalArgumentException(
          String.format(
              "Type argument `%s` for type parameter `%s` is not within bound `%s`.",
              e.getArgument().getTypeName(),
              e.getParameter().getTypeName(),
              e.getBound().getTypeName()));
    }
  }

  public static ParameterizedType optionalOf(Type elementType) {
    return parameterizedType(Optional.class, elementType);
  }

  public static Type arrayOf(Type elementType) {
    return TypeFactory.arrayOf(elementType);
  }

  public static ParameterizedType pairOf(Type firstType, Type secondType) {
    return parameterizedType(Pair.class, firstType, secondType);
  }

  public static ParameterizedType iterableOf(Type elementType) {
    return parameterizedType(Iterable.class, elementType);
  }

  public static ParameterizedType collectionOf(Type elementType) {
    return parameterizedType(Collection.class, elementType);
  }

  public static ParameterizedType listOf(Type elementType) {
    return parameterizedType(List.class, elementType);
  }

  public static ParameterizedType setOf(Type elementType) {
    return parameterizedType(Set.class, elementType);
  }

  public static ParameterizedType mapOf(Type keyType, Type valueType) {
    return parameterizedType(Map.class, keyType, valueType);
  }
}

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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import org.pkl.core.PClassInfo;
import org.pkl.core.util.Nullable;

class PCollectionToCollection implements ConverterFactory {
  private static final Lookup lookup = MethodHandles.lookup();

  @Override
  public Optional<Converter<?, ?>> create(PClassInfo<?> sourceType, Type targetType) {
    var targetClass = Reflection.toRawType(targetType);
    if (!(sourceType.isConcreteCollectionClass()
        && Collection.class.isAssignableFrom(targetClass))) {
      return Optional.empty();
    }

    var iterableType =
        (ParameterizedType) Reflection.getExactSupertype(targetType, Collection.class);
    var elementType = Reflection.normalize(iterableType.getActualTypeArguments()[0]);

    return createInstantiator(targetClass)
        .map(instantiator -> new ConverterImpl<>(instantiator, elementType));
  }

  private <T> Optional<Function<Integer, Collection<T>>> createInstantiator(Class<T> clazz) {
    try {
      try {
        // constructor with capacity and load factor parameters, e.g. HashSet
        var ctor2 =
            lookup.findConstructor(
                clazz, MethodType.methodType(void.class, int.class, float.class));
        return Optional.of(
            length -> {
              try {
                //noinspection unchecked
                return (Collection<T>) ctor2.invoke((int) (length / .75f) + 1, .75f);
              } catch (Throwable t) {
                throw new ConversionException(
                    String.format("Error invoking constructor of class `%s`.", clazz), t);
              }
            });
      } catch (NoSuchMethodException e2) {
        try {
          // constructor with size parameter, e.g. ArrayList
          var ctor1 = lookup.findConstructor(clazz, MethodType.methodType(void.class, int.class));
          return Optional.of(
              length -> {
                try {
                  //noinspection unchecked
                  return (Collection<T>) ctor1.invoke(length);
                } catch (Throwable t) {
                  throw new ConversionException(
                      String.format("Error invoking constructor of class `%s`.", clazz), t);
                }
              });
        } catch (NoSuchMethodException e1) {
          try {
            // default constructor
            var ctor0 = lookup.findConstructor(clazz, MethodType.methodType(void.class));
            return Optional.of(
                length -> {
                  try {
                    //noinspection unchecked
                    return (Collection<T>) ctor0.invoke();
                  } catch (Throwable t) {
                    throw new ConversionException(
                        String.format("Error invoking constructor of class `%s`.", clazz), t);
                  }
                });
          } catch (NoSuchMethodException e0) {
            return Optional.empty();
          }
        }
      }
    } catch (IllegalAccessException e) {
      throw new ConversionException(
          String.format("Error accessing constructor of class `%s`.", clazz), e);
    }
  }

  private static class ConverterImpl<T> implements Converter<Collection<Object>, Collection<T>> {
    private final Function<Integer, Collection<T>> targetInstantiator;
    private final Type targetElementType;

    private PClassInfo<Object> cachedElementType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, T> cachedConverter;

    private ConverterImpl(
        Function<Integer, Collection<T>> targetInstantiator, Type targetElementType) {
      this.targetInstantiator = targetInstantiator;
      this.targetElementType = targetElementType;
    }

    @Override
    public Collection<T> convert(Collection<Object> value, ValueMapper valueMapper) {
      var result = targetInstantiator.apply(value.size());

      for (Object elem : value) {
        if (!cachedElementType.isExactClassOf(elem)) {
          cachedElementType = PClassInfo.forValue(elem);
          cachedConverter = valueMapper.getConverter(cachedElementType, targetElementType);
        }
        assert cachedConverter != null;
        result.add(cachedConverter.convert(elem, valueMapper));
      }

      return result;
    }
  }
}

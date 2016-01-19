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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import org.pkl.core.PClassInfo;
import org.pkl.core.util.Nullable;

class PMapToMap implements ConverterFactory {
  private static final Lookup lookup = MethodHandles.lookup();

  @Override
  public Optional<Converter<?, ?>> create(PClassInfo<?> sourceType, Type targetType) {
    var targetClass = Reflection.toRawType(targetType);
    if (!(sourceType == PClassInfo.Map && Map.class.isAssignableFrom(targetClass))) {
      return Optional.empty();
    }

    ParameterizedType mapType;
    if (Properties.class.isAssignableFrom(targetClass)) {
      // Properties is-a Map<Object,Object> but is supposed to only contain String keys/values
      mapType = Types.mapOf(String.class, String.class);
    } else {
      mapType = (ParameterizedType) Reflection.getExactSupertype(targetType, Map.class);
    }
    var typeArguments = mapType.getActualTypeArguments();
    var keyType = Reflection.normalize(typeArguments[0]);
    var valueType = Reflection.normalize(typeArguments[1]);
    return createInstantiator(targetClass)
        .map(instantiator -> new ConverterImpl<>(instantiator, keyType, valueType));
  }

  private <K, V> Optional<Function<Integer, Map<K, V>>> createInstantiator(Class<?> clazz) {
    try {
      // constructor with capacity and load factor arguments
      var ctor2 =
          lookup.findConstructor(clazz, MethodType.methodType(void.class, int.class, float.class));
      return Optional.of(
          length -> {
            try {
              //noinspection unchecked
              return (Map<K, V>) ctor2.invoke((int) (length / .75f) + 1, .75f);
            } catch (Throwable t) {
              throw new ConversionException(
                  String.format("Error invoking constructor of class `%s`.", clazz), t);
            }
          });
    } catch (NoSuchMethodException e2) {
      try {
        // default constructor
        var ctor0 = lookup.findConstructor(clazz, MethodType.methodType(void.class));
        return Optional.of(
            length -> {
              try {
                //noinspection unchecked
                return (Map<K, V>) ctor0.invoke();
              } catch (Throwable t) {
                throw new ConversionException(
                    String.format("Error invoking constructor of class `%s`.", clazz), t);
              }
            });
      } catch (NoSuchMethodException e0) {
        return Optional.empty();
      } catch (IllegalAccessException e) {
        throw new ConversionException(
            String.format("Error accessing constructor of class `%s`.", clazz), e);
      }
    } catch (IllegalAccessException e) {
      throw new ConversionException(
          String.format("Error accessing constructor of class `%s`.", clazz), e);
    }
  }

  private static class ConverterImpl<K, V> implements Converter<Map<Object, Object>, Map<K, V>> {
    private final Function<Integer, Map<K, V>> targetInstantiator;
    private final Type targetKeyType;
    private final Type targetValueType;

    private PClassInfo<Object> cachedKeyType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, K> cachedKeyConverter;

    private PClassInfo<Object> cachedValueType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, V> cachedValueConverter;

    private ConverterImpl(
        Function<Integer, Map<K, V>> targetInstantiator, Type targetKeyType, Type targetValueType) {
      this.targetInstantiator = targetInstantiator;
      this.targetKeyType = targetKeyType;
      this.targetValueType = targetValueType;
    }

    @Override
    public Map<K, V> convert(Map<Object, Object> map, ValueMapper valueMapper) {
      var result = targetInstantiator.apply(map.size());

      for (Map.Entry<Object, Object> entry : map.entrySet()) {
        var key = entry.getKey();
        if (!cachedKeyType.isExactClassOf(key)) {
          cachedKeyType = PClassInfo.forValue(key);
          cachedKeyConverter = valueMapper.getConverter(cachedKeyType, targetKeyType);
        }
        assert cachedKeyConverter != null;

        var value = entry.getValue();
        if (!cachedValueType.isExactClassOf(value)) {
          cachedValueType = PClassInfo.forValue(value);
          cachedValueConverter = valueMapper.getConverter(cachedValueType, targetValueType);
        }
        assert cachedValueConverter != null;

        result.put(
            cachedKeyConverter.convert(key, valueMapper),
            cachedValueConverter.convert(value, valueMapper));
      }

      return result;
    }
  }
}

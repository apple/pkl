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

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Optional;
import org.pkl.core.PClassInfo;
import org.pkl.core.util.Nullable;

final class PCollectionToArray implements ConverterFactory {
  @Override
  public Optional<Converter<?, ?>> create(PClassInfo<?> sourceType, Type targetType) {
    var targetClass = Reflection.toRawType(targetType);
    if (!(sourceType.isConcreteCollectionClass() && targetClass.isArray())) {
      return Optional.empty();
    }

    if (targetClass.getComponentType().isPrimitive()) {
      if (targetClass == boolean[].class) {
        return Optional.of(new BooleanArrayConverterImpl());
      }
      if (targetClass == char[].class) {
        return Optional.of(new CharArrayConverterImpl());
      }
      if (targetClass == long[].class) {
        return Optional.of(new LongArrayConverterImpl());
      }
      if (targetClass == int[].class) {
        return Optional.of(new IntArrayConverterImpl());
      }
      if (targetClass == short[].class) {
        return Optional.of(new ShortArrayConverterImpl());
      }
      if (targetClass == byte[].class) {
        return Optional.of(new ByteArrayConverterImpl());
      }
      if (targetClass == double[].class) {
        return Optional.of(new DoubleArrayConverterImpl());
      }
      if (targetClass == float[].class) {
        return Optional.of(new FloatArrayConverterImpl());
      }
      throw new AssertionError("unreachable code");
    }

    var elementType = Reflection.getArrayElementType(targetType);
    return Optional.of(new ObjectArrayConverterImpl<>(elementType));
  }

  // having a separate converter for each primitive array type
  // saves some reflection at the expense of some code duplication
  private static final class BooleanArrayConverterImpl
      implements Converter<Collection<Object>, boolean[]> {
    private PClassInfo<Object> cachedElementType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, Boolean> cachedConverter;

    @Override
    public boolean[] convert(Collection<Object> value, ValueMapper valueMapper) {
      var result = new boolean[value.size()];
      var i = 0;
      for (Object elem : value) {
        if (!cachedElementType.isExactClassOf(elem)) {
          cachedElementType = PClassInfo.forValue(elem);
          cachedConverter = valueMapper.getConverter(cachedElementType, boolean.class);
        }
        assert cachedConverter != null;
        result[i++] = cachedConverter.convert(elem, valueMapper);
      }
      return result;
    }
  }

  private static final class CharArrayConverterImpl
      implements Converter<Collection<Object>, char[]> {
    private PClassInfo<Object> cachedElementType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, Character> cachedConverter;

    @Override
    public char[] convert(Collection<Object> value, ValueMapper valueMapper) {
      var result = new char[value.size()];
      var i = 0;
      for (var elem : value) {
        if (!cachedElementType.isExactClassOf(elem)) {
          cachedElementType = PClassInfo.forValue(elem);
          cachedConverter = valueMapper.getConverter(cachedElementType, char.class);
        }
        assert cachedConverter != null;
        result[i++] = cachedConverter.convert(elem, valueMapper);
      }
      return result;
    }
  }

  private static final class ByteArrayConverterImpl
      implements Converter<Collection<Object>, byte[]> {
    private PClassInfo<Object> cachedElementType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, Byte> cachedConverter;

    @Override
    public byte[] convert(Collection<Object> value, ValueMapper valueMapper) {
      var result = new byte[value.size()];
      var i = 0;
      for (Object elem : value) {
        if (!cachedElementType.isExactClassOf(elem)) {
          cachedElementType = PClassInfo.forValue(elem);
          cachedConverter = valueMapper.getConverter(cachedElementType, byte.class);
        }
        assert cachedConverter != null;
        result[i++] = cachedConverter.convert(elem, valueMapper);
      }
      return result;
    }
  }

  private static final class ShortArrayConverterImpl
      implements Converter<Collection<Object>, short[]> {
    private PClassInfo<Object> cachedElementType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, Short> cachedConverter;

    @Override
    public short[] convert(Collection<Object> value, ValueMapper valueMapper) {
      var result = new short[value.size()];
      var i = 0;
      for (Object elem : value) {
        if (!cachedElementType.isExactClassOf(elem)) {
          cachedElementType = PClassInfo.forValue(elem);
          cachedConverter = valueMapper.getConverter(cachedElementType, short.class);
        }
        assert cachedConverter != null;
        result[i++] = cachedConverter.convert(elem, valueMapper);
      }
      return result;
    }
  }

  private static final class IntArrayConverterImpl implements Converter<Collection<Object>, int[]> {
    private PClassInfo<Object> cachedElementType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, Integer> cachedConverter;

    @Override
    public int[] convert(Collection<Object> value, ValueMapper valueMapper) {
      var result = new int[value.size()];
      var i = 0;
      for (Object elem : value) {
        if (!cachedElementType.isExactClassOf(elem)) {
          cachedElementType = PClassInfo.forValue(elem);
          cachedConverter = valueMapper.getConverter(cachedElementType, int.class);
        }
        assert cachedConverter != null;
        result[i++] = cachedConverter.convert(elem, valueMapper);
      }
      return result;
    }
  }

  private static final class LongArrayConverterImpl
      implements Converter<Collection<Object>, long[]> {
    private PClassInfo<Object> cachedElementType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, Long> cachedConverter;

    @Override
    public long[] convert(Collection<Object> value, ValueMapper valueMapper) {
      var result = new long[value.size()];
      var i = 0;
      for (Object elem : value) {
        if (!cachedElementType.isExactClassOf(elem)) {
          cachedElementType = PClassInfo.forValue(elem);
          cachedConverter = valueMapper.getConverter(cachedElementType, long.class);
        }
        assert cachedConverter != null;
        result[i++] = cachedConverter.convert(elem, valueMapper);
      }
      return result;
    }
  }

  private static final class FloatArrayConverterImpl
      implements Converter<Collection<Object>, float[]> {
    private PClassInfo<Object> cachedElementType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, Float> cachedConverter;

    @Override
    public float[] convert(Collection<Object> value, ValueMapper valueMapper) {
      var result = new float[value.size()];
      var i = 0;
      for (Object elem : value) {
        if (!cachedElementType.isExactClassOf(elem)) {
          cachedElementType = PClassInfo.forValue(elem);
          cachedConverter = valueMapper.getConverter(cachedElementType, float.class);
        }
        assert cachedConverter != null;
        result[i++] = cachedConverter.convert(elem, valueMapper);
      }
      return result;
    }
  }

  private static final class DoubleArrayConverterImpl
      implements Converter<Collection<Object>, double[]> {
    private PClassInfo<Object> cachedElementType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, Double> cachedConverter;

    @Override
    public double[] convert(Collection<Object> value, ValueMapper valueMapper) {
      var result = new double[value.size()];
      var i = 0;
      for (Object elem : value) {
        if (!cachedElementType.isExactClassOf(elem)) {
          cachedElementType = PClassInfo.forValue(elem);
          cachedConverter = valueMapper.getConverter(cachedElementType, double.class);
        }
        assert cachedConverter != null;
        result[i++] = cachedConverter.convert(elem, valueMapper);
      }
      return result;
    }
  }

  private static final class ObjectArrayConverterImpl<T>
      implements Converter<Collection<Object>, T[]> {
    private final Type componentType;
    private final Class<T> rawComponentType;

    private PClassInfo<Object> cachedElementType = PClassInfo.Unavailable;
    private @Nullable Converter<Object, T> cachedConverter;

    private ObjectArrayConverterImpl(Type componentType) {
      this.componentType = componentType;

      @SuppressWarnings("unchecked")
      var rawComponentType = (Class<T>) Reflection.toRawType(componentType);
      this.rawComponentType = rawComponentType;
    }

    @Override
    public T[] convert(Collection<Object> value, ValueMapper valueMapper) {
      @SuppressWarnings("unchecked")
      var result = (T[]) Array.newInstance(rawComponentType, value.size());
      var i = 0;
      for (Object elem : value) {
        if (!cachedElementType.isExactClassOf(elem)) {
          cachedElementType = PClassInfo.forValue(elem);
          cachedConverter = valueMapper.getConverter(cachedElementType, componentType);
        }
        assert cachedConverter != null;
        result[i++] = cachedConverter.convert(elem, valueMapper);
      }
      return result;
    }
  }
}

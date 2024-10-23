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

import java.beans.ConstructorProperties;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.*;
import java.util.*;
import org.pkl.core.Composite;
import org.pkl.core.PClassInfo;
import org.pkl.core.PObject;
import org.pkl.core.util.Nullable;

public class PObjectToDataObject implements ConverterFactory {
  private static final Lookup lookup = MethodHandles.lookup();

  @SuppressWarnings("unchecked")
  private static final @Nullable Class<? extends Annotation> javaxInjectNamedClass =
      (Class<? extends Annotation>) Reflection.tryLoadClass("javax.inject.Named");

  private static final @Nullable Method javaxInjectNamedValueMethod;

  static {
    try {
      javaxInjectNamedValueMethod =
          javaxInjectNamedClass == null ? null : javaxInjectNamedClass.getMethod("value");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  protected PObjectToDataObject() {}

  @Override
  public final Optional<Converter<?, ?>> create(PClassInfo<?> sourceType, Type targetType) {
    if (!(sourceType == PClassInfo.Module || sourceType.getJavaClass() == PObject.class)) {
      return Optional.empty();
    }

    return selectConstructor(Reflection.toRawType(targetType))
        .flatMap(
            constructor ->
                getParameters(constructor, targetType)
                    .map(
                        parameters -> {
                          try {
                            return new ConverterImpl<>(
                                targetType, lookup.unreflectConstructor(constructor), parameters);
                          } catch (IllegalAccessException e) {
                            throw new ConversionException(
                                String.format("Error accessing constructor `%s`.", constructor), e);
                          }
                        }));
  }

  protected Optional<Constructor<?>> selectConstructor(Class<?> clazz) {
    return Arrays.stream(clazz.getDeclaredConstructors())
        .max(Comparator.comparingInt(Constructor::getParameterCount));
  }

  protected Optional<List<String>> getParameterNames(Constructor<?> constructor) {
    var paramNames = new ArrayList<String>(constructor.getParameterCount());

    var properties = getAnnotation(constructor, ConstructorProperties.class);
    if (properties != null) {
      return Optional.of(Arrays.asList(properties.value()));
    }

    for (Parameter parameter : constructor.getParameters()) {
      var name = getParameterName(parameter);
      if (name == null) return Optional.empty();
      paramNames.add(name);
    }
    return Optional.of(paramNames);
  }

  private Optional<List<Tuple2<String, Type>>> getParameters(
      Constructor<?> constructor, Type targetType) {
    return getParameterNames(constructor)
        .map(
            paramNames -> {
              var paramTypes = Reflection.getExactParameterTypes(constructor, targetType);
              var parameters = new ArrayList<Tuple2<String, Type>>(paramNames.size());
              for (int i = 0; i < paramNames.size(); i++) {
                var name = paramNames.get(i);
                parameters.add(Tuple2.of(name, paramTypes[i]));
              }
              return parameters;
            });
  }

  private static @Nullable String getParameterName(Parameter parameter) {
    if (parameter.isNamePresent()) {
      return parameter.getName();
    }

    Named named = getAnnotation(parameter, Named.class);
    if (named != null) {
      return named.value();
    }

    if (javaxInjectNamedClass != null) {
      assert javaxInjectNamedValueMethod != null;
      var ann = getAnnotation(parameter, javaxInjectNamedClass);
      if (ann != null) {
        try {
          return (String) javaxInjectNamedValueMethod.invoke(ann);
        } catch (IllegalAccessException | InvocationTargetException e) {
          throw new ConversionException("Failed to invoke `javax.inject.Named.value()`.", e);
        }
      }
    }

    return null;
  }

  private static @Nullable <T extends Annotation> T getAnnotation(
      Constructor<?> constructor, Class<T> annotationClass) {
    try {
      return constructor.getAnnotation(annotationClass);
    } catch (IndexOutOfBoundsException e) {
      // workaround for https://bugs.openjdk.java.net/browse/JDK-8025806
      return null;
    }
  }

  private static @Nullable <T extends Annotation> T getAnnotation(
      Parameter parameter, Class<T> annotationClass) {
    try {
      return parameter.getAnnotation(annotationClass);
    } catch (
        IndexOutOfBoundsException
            e) { // workaround for https://bugs.openjdk.java.net/browse/JDK-8025806
      return null;
    }
  }

  private static class ConverterImpl<T> implements Converter<Composite, T> {
    private final Type targetType;
    private final MethodHandle constructorHandle;
    private final Collection<Tuple2<String, Type>> parameters;
    private final PClassInfo<Object>[] cachedPropertyTypes;
    private final Converter<Object, T>[] cachedConverters;

    ConverterImpl(
        Type targetType,
        MethodHandle constructorHandle,
        Collection<Tuple2<String, Type>> parameters) {
      this.targetType = targetType;
      this.constructorHandle = constructorHandle;
      this.parameters = parameters;

      @SuppressWarnings("unchecked")
      PClassInfo<Object>[] cachedPropertyTypes = new PClassInfo[parameters.size()];
      this.cachedPropertyTypes = cachedPropertyTypes;
      Arrays.fill(cachedPropertyTypes, PClassInfo.Unavailable);

      @SuppressWarnings("unchecked")
      Converter<Object, T>[] cachedConverters = new Converter[parameters.size()];
      this.cachedConverters = cachedConverters;
    }

    @Override
    public T convert(Composite value, ValueMapper valueMapper) {
      var properties = value.getProperties();
      var args = new Object[parameters.size()];
      var i = 0;

      for (var param : parameters) {
        var property = properties.get(param.first);
        if (property == null) {
          var message =
              String.format(
                  "Cannot convert Pkl object to Java object."
                      + "%nPkl type             : %s"
                      + "%nJava type            : %s"
                      + "%nMissing Pkl property : %s"
                      + "%nActual Pkl properties: %s",
                  value.getClassInfo(), targetType.getTypeName(), param.first, properties.keySet());
          throw new ConversionException(message);
        }

        try {
          var cachedPropertyType = cachedPropertyTypes[i];
          if (!cachedPropertyType.isExactClassOf(property)) {
            cachedPropertyType = PClassInfo.forValue(property);
            cachedPropertyTypes[i] = cachedPropertyType;
            cachedConverters[i] = valueMapper.getConverter(cachedPropertyType, param.second);
          }
          assert cachedConverters[i] != null;
          args[i] = cachedConverters[i].convert(property, valueMapper);
          i += 1;
        } catch (ConversionException e) {
          throw new ConversionException(
              String.format(
                  "Error converting property `%s` in Pkl object of type `%s` "
                      + "to equally named constructor parameter in Java class `%s`: "
                      + e.getMessage(),
                  param.first,
                  value.getClassInfo(),
                  Reflection.toRawType(targetType).getTypeName()),
              e.getCause());
        }
      }

      try {
        @SuppressWarnings("unchecked")
        var result = (T) constructorHandle.invokeWithArguments(args);
        return result;
      } catch (Throwable t) {
        throw new ConversionException(
            String.format("Error invoking constructor `%s`.", constructorHandle), t);
      }
    }
  }
}

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
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.pkl.core.PClassInfo;
import org.pkl.core.util.CollectionUtils;

class ValueMapperImpl implements ValueMapper {
  private final Collection<Conversion<?, ?>> conversions;
  private final Collection<ConverterFactory> factories;
  private final Collection<TypeMapping<?, ?>> typeMappings;

  private final Map<Tuple2<PClassInfo<?>, Type>, Converter<?, ?>> convertersMap;
  private final Map<Class<?>, Class<?>> typeMappingsMap;

  ValueMapperImpl(
      Collection<Conversion<?, ?>> conversions,
      Collection<ConverterFactory> factories,
      Collection<TypeMapping<?, ?>> typeMappings) {
    this.conversions = conversions;
    this.factories = factories;
    this.typeMappings = typeMappings;

    convertersMap = CollectionUtils.newHashMap(conversions.size());
    for (var conversion : conversions) {
      convertersMap.put(
          Tuple2.of(conversion.sourceType, conversion.targetType), conversion.converter);
    }

    this.typeMappingsMap = CollectionUtils.newHashMap(typeMappings.size());
    for (var mapping : typeMappings) {
      this.typeMappingsMap.put(mapping.requestedType, mapping.implementationType);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <S, T> T map(S model, Type targetType) {
    var sourceType = PClassInfo.forValue(model);
    return (T) getConverter(sourceType, targetType).convert(model, this);
  }

  private <S> Class<?> getTargetType(PClassInfo<S> sourceType, Type targetType) {
    var rawTargetType = Reflection.toRawType(targetType);
    var determinedClass = ClassRegistry.get(sourceType);
    if (determinedClass == null) {
      return rawTargetType;
    }
    var rawRegisteredSchema = Reflection.toRawType(determinedClass);
    if (rawTargetType.isAssignableFrom(rawRegisteredSchema)) {
      return rawRegisteredSchema;
    }
    return rawTargetType;
  }

  @Override
  public <S, T> Converter<S, T> getConverter(PClassInfo<S> sourceType, Type targetType) {
    Tuple2<PClassInfo<?>, Type> key = Tuple2.of(sourceType, targetType);

    @SuppressWarnings("unchecked")
    Converter<S, T> converter = (Converter<S, T>) convertersMap.get(key);
    if (converter != null) return converter;

    if (Reflection.isMissingTypeArguments(targetType)) {
      throw new IllegalArgumentException(
          String.format("Target type `%s` is missing type arguments.", targetType));
    }

    Class<?> rawTargetType = getTargetType(sourceType, targetType);
    @SuppressWarnings("unchecked")
    var rawImplType = (Class<T>) typeMappingsMap.getOrDefault(rawTargetType, rawTargetType);
    var implType = Reflection.getExactSubtype(targetType, rawImplType);

    // look up implType converter
    // because implType has wildcards removed, conversion to
    // `? extends Number` or `? super Number` finds converter for `Number`
    // (assuming `converters` has such a converter)
    var implKey = Tuple2.of(sourceType, implType);
    @SuppressWarnings("unchecked")
    var implConverter = (Converter<S, T>) convertersMap.get(implKey);
    if (implConverter != null) {
      // TODO: give converter a chance to copy itself to avoid pollution of its inline caches
      convertersMap.put(key, implConverter);
      return implConverter;
    }

    // create implType converter
    for (ConverterFactory factory : factories) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Optional<Converter<S, T>> newConverter = (Optional) factory.create(sourceType, implType);
      if (newConverter.isPresent()) {
        convertersMap.put(key, newConverter.get());
        return newConverter.get();
      }
    }

    throw new ConversionException(
        String.format(
            "Cannot convert `%s` to `%s` because no conversion was found.",
            sourceType.getQualifiedName(), targetType.getTypeName()));
  }

  @Override
  public ValueMapperBuilder toBuilder() {
    return ValueMapperBuilder.unconfigured()
        .setConversions(conversions)
        .setConverterFactories(factories)
        .setTypeMappings(typeMappings);
  }
}

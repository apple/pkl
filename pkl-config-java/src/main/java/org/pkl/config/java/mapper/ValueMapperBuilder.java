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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds a {@link ValueMapper} configured with appropriate conversions, converter factories, and
 * type mappings.
 */
@SuppressWarnings("UnusedReturnValue")
public final class ValueMapperBuilder {
  private final List<Conversion<?, ?>> conversions = new ArrayList<>();
  private final List<ConverterFactory> factories = new ArrayList<>();
  private final List<TypeMapping<?, ?>> mappings = new ArrayList<>();

  private ValueMapperBuilder() {}

  /**
   * Creates a builder without any preconfigured conversions, converter factories, or type mappings.
   *
   * @return a builder without any preconfigured conversions, converter factories, or type mappings
   */
  public static ValueMapperBuilder unconfigured() {
    return new ValueMapperBuilder();
  }

  /**
   * Creates a builder preconfigured with all conversions, converter factories, and type mappings
   * defined in this module.
   *
   * @return a builder preconfigured with all conversions, converter factories, and type mappings
   *     defined in this module
   */
  public static ValueMapperBuilder preconfigured() {
    return unconfigured()
        .addConversions(Conversions.all)
        .addConverterFactories(ConverterFactories.all)
        .addTypeMappings(TypeMappings.all);
  }

  /**
   * Adds the given conversion. For conversions to a primitive type, a conversion to the
   * corresponding wrapper type is automatically added.
   *
   * @param conversion the conversion to be added
   * @return this
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ValueMapperBuilder addConversion(Conversion<?, ?> conversion) {
    conversions.add(conversion);
    if (conversion.targetType instanceof Class<?> clazz) {
      if (clazz.isPrimitive()) {
        conversions.add(
            Conversion.of(
                conversion.sourceType,
                Reflection.toWrapperType(clazz),
                (Converter) conversion.converter));
      }
    }
    return this;
  }

  /**
   * Adds the given conversions. For conversions to a primitive type, a conversion to the
   * corresponding wrapper type is automatically added.
   *
   * @param conversions the conversions to be added
   * @return this
   */
  public ValueMapperBuilder addConversions(Collection<Conversion<?, ?>> conversions) {
    conversions.forEach(this::addConversion);
    return this;
  }

  /** Removes any existing conversions, then adds the given conversions. */
  public ValueMapperBuilder setConversions(Collection<Conversion<?, ?>> conversions) {
    this.conversions.clear();
    return addConversions(conversions);
  }

  /** Returns the currently set conversions. */
  public List<Conversion<?, ?>> getConversions() {
    return conversions;
  }

  /**
   * Adds the given converter factory. Factories will be queried for converters in the same order as
   * they are being added to this builder.
   *
   * @param factory the converter factory to be added
   * @return this
   */
  public ValueMapperBuilder addConverterFactory(ConverterFactory factory) {
    factories.add(factory);
    return this;
  }

  /**
   * Adds the given converter factories. Factories will be queried for converters in the same order
   * as they are being added to this builder.
   *
   * @param factories the converter factories to be added
   * @return this
   */
  public ValueMapperBuilder addConverterFactories(Collection<ConverterFactory> factories) {
    factories.forEach(this::addConverterFactory);
    return this;
  }

  /** Removes any existing converter factories, then adds the given factories. */
  public ValueMapperBuilder setConverterFactories(Collection<ConverterFactory> factories) {
    this.factories.clear();
    return addConverterFactories(factories);
  }

  /** Returns the currently set converter factories. */
  public List<ConverterFactory> getConverterFactories() {
    return factories;
  }

  /**
   * Adds the given type mapping.
   *
   * @param mapping the type mapping to be added
   * @return this
   */
  public ValueMapperBuilder addTypeMapping(TypeMapping<?, ?> mapping) {
    mappings.add(mapping);
    return this;
  }

  /**
   * Adds the given type mappings.
   *
   * @param mappings the type mappings to be added
   * @return this
   */
  public ValueMapperBuilder addTypeMappings(Collection<TypeMapping<?, ?>> mappings) {
    mappings.forEach(this::addTypeMapping);
    return this;
  }

  /** Removes any existing type mappings, then adds the given mappings. */
  public ValueMapperBuilder setTypeMappings(Collection<TypeMapping<?, ?>> mappings) {
    this.mappings.clear();
    return addTypeMappings(mappings);
  }

  /** Returns the currently set type mappings. */
  public List<TypeMapping<?, ?>> getTypeMappings() {
    return mappings;
  }

  /**
   * Builds a mapper with the configured conversions, converter factories, and type mappings. If
   * desired, the same builder can be used to build multiple mappers.
   *
   * @return a mapper with the configured conversions, converter factories, and type mappings
   */
  public ValueMapper build() {
    return new ValueMapperImpl(
        // copy to shield against subsequent modification through builder
        new ArrayList<>(conversions), new ArrayList<>(factories), new ArrayList<>(mappings));
  }
}

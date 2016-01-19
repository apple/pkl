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

import java.util.*;
import org.pkl.core.PObject;
import org.pkl.core.Pair;

/** Predefined conversions for composite types (objects, collections, etc.). */
public final class ConverterFactories {
  private ConverterFactories() {}

  /**
   * Conversion from {@code pkl.base#Null} to any non-primitive type. The conversion result is
   * always {@code null}.
   */
  public static final ConverterFactory pNullToAny = new PNullToAny();

  /** Identity conversion for {@link PObject}. */
  public static final ConverterFactory pObjectToPObject = new PObjectToPObject();

  /**
   * Conversion from {@code pkl.base#String} to Java Enum type. If there is no exact match between
   * string and enum value, some variations are tried. For example, both {@code "house-of-cards"}
   * and {@code "house of cards"} will be successfully matched to enum value {@code HOUSE_OF_CARDS}.
   */
  public static final ConverterFactory pStringToEnum = new PStringToEnum();

  /**
   * Conversion from any Pkl value to {@link java.util.Optional}. Returns an empty optional for
   * {@code pkl.base#Null} and a present optional otherwise.
   */
  public static final ConverterFactory pAnyToOptional = new PAnyToOptional();

  /** Conversion from {@code pkl.base#Collection} to Java primitive or object array. */
  public static final ConverterFactory pCollectionToArray = new PCollectionToArray();

  /**
   * Conversion from {@code pkl.base#Collection} to {@link Collection}. The concrete implementation
   * type is determined using {@link TypeMapping}s.
   */
  public static final ConverterFactory pCollectionToCollection = new PCollectionToCollection();

  /**
   * Conversion from {@code pkl.base#Map} to {@link Map}. The concrete implementation type is
   * determined using {@link TypeMapping}s.
   */
  public static final ConverterFactory pMapToMap = new PMapToMap();

  /**
   * Conversion from Pkl module or object to Java data object. The conversion is performed as
   * follows:
   *
   * <p>
   *
   * <ol>
   *   <li>Find the Java class constructor with the highest number of parameters.
   *   <li>Correlate constructor parameters with Pkl object properties by name.
   *   <li>Convert each Pkl property value to the corresponding constructor parameter's type.
   *   <li>Invoke the constructor.
   * </ol>
   *
   * <p>Dynamic and class based Pkl objects are equally supported. The Pkl object must contain all
   * properties defined by the Java class constructor. Any additional Pkl object properties are
   * ignored.
   *
   * <p>Unless the Java 8+ compiler option {@code -parameters} is set, constructor parameters must
   * be annotated with {@link Named} or {@code javax.inject.Named}.
   */
  public static final ConverterFactory pObjectToDataObject = new PObjectToDataObject();

  public static final ConverterFactory pObjectToMap = new PObjectToMap();

  /** Conversion from {@code pkl.base#Pair} to {@link Pair}. */
  public static final ConverterFactory pPairToPair = new PPairToPair();

  /** All conversions defined in this class. */
  public static final Collection<ConverterFactory> all =
      List.of(
          pAnyToOptional,
          pNullToAny,
          pObjectToPObject,
          pStringToEnum,
          pCollectionToArray,
          pCollectionToCollection,
          pMapToMap,
          pObjectToDataObject,
          pObjectToMap,
          pPairToPair);
}

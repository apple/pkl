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

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;
import org.pkl.core.*;

/** Predefined conversions for scalar types. */
public final class Conversions {
  private Conversions() {}

  /**
   * Conversion from {@code pkl.base#Int} to {@link Byte}. Throws {@link ConversionException} if the
   * value is too large.
   */
  public static final Conversion<Long, Byte> pIntToByte =
      Conversion.of(
          PClassInfo.Int,
          byte.class,
          (value, mapper) -> {
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
              throw new ConversionException(
                  String.format(
                      "Cannot convert pkl.base#Int `%s` to java.lang.Byte because it is outside range `%s..%s`",
                      value, Byte.MIN_VALUE, Byte.MAX_VALUE));
            }
            return value.byteValue();
          });

  /**
   * Conversion from {@code pkl.base#Int} to {@link Short}. Throws {@link ConversionException} if
   * the value is too large.
   */
  public static final Conversion<Long, Short> pIntToShort =
      Conversion.of(
          PClassInfo.Int,
          short.class,
          (value, mapper) -> {
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
              throw new ConversionException(
                  String.format(
                      "Cannot convert pkl.base#Int `%s` to java.lang.Short because it is outside range `%s..%s`",
                      value, Short.MIN_VALUE, Short.MAX_VALUE));
            }
            return value.shortValue();
          });

  /**
   * Conversion from {@code pkl.base#Int} to {@link Integer}. Throws {@link ConversionException} if
   * the value is too large.
   */
  public static final Conversion<Long, Integer> pIntToInteger =
      Conversion.of(
          PClassInfo.Int,
          int.class,
          (value, mapper) -> {
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
              throw new ConversionException(
                  String.format(
                      "Cannot convert pkl.base#Int `%s` to java.lang.Integer because it is outside range `%s..%s`",
                      value, Integer.MIN_VALUE, Integer.MAX_VALUE));
            }
            return value.intValue();
          });

  /** Conversion from {@code pkl.base#Int} to {@link Float}. May lose precision. */
  public static final Conversion<Long, Float> pIntToFloat =
      Conversion.of(PClassInfo.Int, float.class, (value, mapper) -> value.floatValue());

  /** Conversion from {@code pkl.base#Int} to {@link Double}. May lose precision. */
  public static final Conversion<Long, Double> pIntToDouble =
      Conversion.of(PClassInfo.Int, double.class, (value, mapper) -> value.doubleValue());

  /** Conversion from {@code pkl.base#Int} to {@link BigInteger}. */
  public static final Conversion<Long, BigInteger> pIntToBigInteger =
      Conversion.of(PClassInfo.Int, BigInteger.class, (value, mapper) -> BigInteger.valueOf(value));

  /** Conversion from {@code pkl.base#Int} to {@link BigDecimal}. */
  public static final Conversion<Long, BigDecimal> pIntToBigDecimal =
      Conversion.of(PClassInfo.Int, BigDecimal.class, (value, mapper) -> BigDecimal.valueOf(value));

  /** Conversion from {@code pkl.base#Float} to {@link Float}. May lose precision. */
  public static final Conversion<Double, Float> pFloatToFloat =
      Conversion.of(PClassInfo.Float, float.class, (value, mapper) -> value.floatValue());

  /** Conversion from {@code pkl.base#Float} to {@link BigDecimal}. */
  public static final Conversion<Double, BigDecimal> pFloatToBigDecimal =
      Conversion.of(
          PClassInfo.Float, BigDecimal.class, (value, mapper) -> BigDecimal.valueOf(value));

  /**
   * Conversion from {@code pkl.base#String} to {@link Character}. Throws {@link
   * ConversionException} if the String value is not of length one.
   */
  public static final Conversion<String, Character> pStringToCharacter =
      Conversion.of(
          PClassInfo.String,
          Character.class,
          (value, mapper) -> {
            if (value.length() != 1) {
              throw new ConversionException(
                  String.format(
                      "Cannot convert pkl.base#String `%s` to java.lang.Character because it is not of length 1.",
                      value));
            }
            return value.charAt(0);
          });

  /**
   * Conversion from {@code pkl.base#String} to {@link URI}. Throws {@link ConversionException} if
   * the String value is not a syntactically valid URI.
   */
  public static final Conversion<String, URI> pStringToURI =
      Conversion.of(
          PClassInfo.String,
          URI.class,
          (value, mapper) -> {
            try {
              return new URI(value);
            } catch (URISyntaxException e) {
              throw new ConversionException(
                  "Failed to convert `pkl.base#String` to `java.net.URI`.", e);
            }
          });

  /**
   * Conversion from {@code pkl.base#String} to {@link URL}. Throws {@link ConversionException} if
   * the String value is not a syntactically valid URL.
   */
  public static final Conversion<String, URL> pStringToURL =
      Conversion.of(
          PClassInfo.String,
          URL.class,
          (value, mapper) -> {
            try {
              return new URL(value);
            } catch (MalformedURLException e) {
              throw new ConversionException(
                  "Failed to convert `pkl.base#String` to `java.net.URL`.", e);
            }
          });

  /** Conversion from {@code pkl.base#String} to {@link File}. */
  public static final Conversion<String, File> pStringToFile =
      Conversion.of(PClassInfo.String, File.class, (value, mapper) -> new File(value));

  /**
   * Conversion from {@code pkl.base#String} to {@link Path}. Throws {@link ConversionException} if
   * the String value is not a syntactically valid path.
   */
  public static final Conversion<String, Path> pStringToPath =
      Conversion.of(
          PClassInfo.String,
          Path.class,
          (value, mapper) -> {
            try {
              return Path.of(value);
            } catch (InvalidPathException e) {
              throw new ConversionException(
                  "Failed to convert `pkl.base#String` to `java.nio.file.Path`.", e);
            }
          });

  /** Conversion from {@code pkl.base#String} to {@link Pattern}. */
  public static final Conversion<String, Pattern> pStringToPattern =
      Conversion.of(
          PClassInfo.String,
          Pattern.class,
          (value, mapper) -> {
            try {
              return Pattern.compile(value);
            } catch (PatternSyntaxException e) {
              throw new ConversionException(
                  "Failed to convert `pkl.base#String` to `java.util.regex.Pattern`.", e);
            }
          });

  /** Conversion from {@code pkl.base#Regex} to {@link String}. */
  public static final Conversion<Pattern, String> pRegexToString =
      Conversion.of(PClassInfo.Regex, String.class, (value, mapper) -> value.pattern());

  /** Conversion from {@code pkl.base#Duration} to {@link java.time.Duration}. */
  public static final Conversion<Duration, java.time.Duration> pDurationToDuration =
      Conversion.of(
          PClassInfo.Duration, java.time.Duration.class, (value, mapper) -> value.toJavaDuration());

  /** Conversion from {@code pkl.semver#Version} to {@link Version}. */
  // Cannot leave this to `ConverterFactories.pObjectToDataObject`
  // because `Version` is part of pkl-core and thus cannot be annotated with `@Named`.
  public static final Conversion<PObject, Version> pVersionToVersion =
      Conversion.of(
          PClassInfo.Version,
          Version.class,
          (value, mapper) -> {
            try {
              return new Version(
                  Math.toIntExact((Long) value.getProperty("major")),
                  Math.toIntExact((Long) value.getProperty("minor")),
                  Math.toIntExact((Long) value.getProperty("patch")),
                  (String) value.get("preRelease"),
                  (String) value.get("build"));
            } catch (ArithmeticException e) {
              throw new ConversionException(
                  "Failed to convert `pkl.semver#Version` to `org.pkl.core.Version`.", e);
            }
          });

  public static final Conversion<PObject, String> pVersionToString =
      Conversion.of(
          PClassInfo.Version,
          String.class,
          (value, mapper) -> {
            var builder = new StringBuilder();
            builder.append(value.get("major"));
            builder.append('.');
            builder.append(value.get("minor"));
            builder.append('.');
            builder.append(value.get("patch"));
            var preRelease = value.get("preRelease");
            if (preRelease != null) {
              builder.append('-');
              builder.append(preRelease);
            }
            var build = value.get("build");
            if (build != null) {
              builder.append('+');
              builder.append(build);
            }
            return builder.toString();
          });

  public static final Conversion<String, Version> pStringToVersion =
      Conversion.of(
          PClassInfo.String,
          Version.class,
          (value, mapper) -> {
            try {
              return Version.parse(value);
            } catch (IllegalArgumentException e) {
              throw new ConversionException(
                  "Failed to convert `pkl.base#String` to `org.pkl.core.Version`.", e);
            }
          });

  /**
   * Identity conversions used when the Java representation of the Pkl type matches the target type
   * or when the target type is {@link Object}.
   */
  public static final Collection<Conversion<?, ?>> identities =
      List.of(
          Conversion.of(PClassInfo.Boolean, boolean.class, Converter.identity()),
          Conversion.of(PClassInfo.Boolean, Object.class, Converter.identity()),
          Conversion.of(PClassInfo.String, String.class, Converter.identity()),
          Conversion.of(PClassInfo.String, Object.class, Converter.identity()),
          Conversion.of(PClassInfo.Int, long.class, Converter.identity()),
          Conversion.of(PClassInfo.Int, Number.class, Converter.identity()),
          Conversion.of(PClassInfo.Int, Object.class, Converter.identity()),
          Conversion.of(PClassInfo.Float, double.class, Converter.identity()),
          Conversion.of(PClassInfo.Float, Number.class, Converter.identity()),
          Conversion.of(PClassInfo.Float, Object.class, Converter.identity()),
          Conversion.of(PClassInfo.Duration, Duration.class, Converter.identity()),
          Conversion.of(PClassInfo.Duration, Object.class, Converter.identity()),
          Conversion.of(PClassInfo.DataSize, DataSize.class, Converter.identity()),
          Conversion.of(PClassInfo.DataSize, Object.class, Converter.identity()),
          Conversion.of(PClassInfo.Module, PModule.class, Converter.identity()),
          Conversion.of(PClassInfo.Module, Object.class, Converter.identity()),
          Conversion.of(PClassInfo.Class, PClass.class, Converter.identity()),
          Conversion.of(PClassInfo.Class, Object.class, Converter.identity()),
          Conversion.of(PClassInfo.Regex, Pattern.class, Converter.identity()),
          Conversion.of(PClassInfo.Regex, Object.class, Converter.identity()),
          Conversion.of(PClassInfo.Null, PNull.class, Converter.identity())
          // PClassInfo.Null -> Object.class is covered by PNullToAny (returns null rather than
          // PNull.getInstance())
          );

  /** Numeric conversions. Does not include identity conversions. */
  public static final Collection<Conversion<?, ?>> numeric =
      List.of(
          pIntToByte,
          pIntToShort,
          pIntToInteger,
          pIntToFloat,
          pIntToDouble,
          pIntToBigInteger,
          pIntToBigDecimal,
          pFloatToFloat,
          pFloatToBigDecimal);

  /** Conversions that don't fit any other category. */
  public static final Collection<Conversion<?, ?>> misc =
      List.of(
          pStringToCharacter,
          pStringToURI,
          pStringToURL,
          pStringToFile,
          pStringToPath,
          pStringToPattern,
          pRegexToString,
          pDurationToDuration,
          pVersionToVersion,
          pVersionToString,
          pStringToVersion);

  /** All conversions defined in this class. */
  public static final Collection<Conversion<?, ?>> all = collectAll();

  private static Collection<Conversion<?, ?>> collectAll() {
    var result = new ArrayList<Conversion<?, ?>>(identities.size() + numeric.size() + misc.size());
    result.addAll(identities);
    result.addAll(numeric);
    result.addAll(misc);
    return Collections.unmodifiableList(result);
  }
}

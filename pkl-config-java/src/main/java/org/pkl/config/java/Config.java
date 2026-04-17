/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.config.java;

import java.io.InputStream;
import java.lang.reflect.Type;
import org.jspecify.annotations.Nullable;
import org.pkl.config.java.mapper.ConversionException;
import org.pkl.config.java.mapper.ValueMapper;
import org.pkl.core.Evaluator;

/**
 * A root, intermediate, or leaf node in a configuration tree. Child nodes can be obtained by name
 * using {@link #get(String)}. To consume the node's composite or scalar value, convert the value to
 * the desired Java type, using one of the provided {@link #as} methods.
 */
@SuppressWarnings({"DeprecatedIsStillUsed"})
public interface Config {
  /**
   * The dot-separated name of this node. For example, the node reached using {@code
   * rootNode.get("foo").get("bar")} has qualified name {@code foo.bar}. Returns the empty String
   * for the root node itself.
   */
  String getQualifiedName();

  /**
   * The raw value of this node, as provided by {@link Evaluator}. Typically, a node's value is not
   * consumed directly, but converted to the desired Java type using {@link #as}.
   */
  Object getRawValue();

  /**
   * Returns the child node with the given unqualified name.
   *
   * @throws NoSuchChildException if a child with the given name does not exist
   */
  Config get(String childName);

  /**
   * Converts this node's value to the given {@link Class}.
   *
   * @throws ConversionException if the value cannot be converted to the given type
   */
  <T extends @Nullable Object> T as(Class<T> type);

  /**
   * Converts this node's value to the given {@link Type}.
   *
   * <p>Note that usages of this method are not type safe.
   *
   * @throws ConversionException if the value cannot be converted to the given type
   */
  <T extends @Nullable Object> T as(Type type);

  /**
   * Converts this node's value to the given {@link JavaType}.
   *
   * @throws ConversionException if the value cannot be converted to the given type
   */
  <T extends @Nullable Object> T as(JavaType<T> type);

  /**
   * Decodes a config from the supplied byte array.
   *
   * @return the decoded config
   * @deprecated Use {@code ConfigDecoderBuilder...build().decode(bytes)} instead. For a direct
   *     equivalent, use {@code ConfigDecoder.preconfigured().setValueMapper(mapper).decode(bytes)}.
   */
  @Deprecated(forRemoval = true)
  static Config fromPklBinary(byte[] bytes, ValueMapper mapper) {
    return ConfigDecoder.preconfigured().setValueMapper(mapper).decode(bytes);
  }

  /**
   * Decodes a config from the supplied byte array using a preconfigured {@link ValueMapper}.
   *
   * @return the decoded config
   * @deprecated Use {@code ConfigDecoder.preconfigured().decode(bytes)} instead.
   */
  @Deprecated(forRemoval = true)
  static Config fromPklBinary(byte[] bytes) {
    return ConfigDecoder.preconfigured().decode(bytes);
  }

  /**
   * Decodes a config from the supplied {@link InputStream} using a preconfigured {@link
   * ValueMapper}.
   *
   * @return the decoded config
   * @deprecated Use {@code ConfigDecoderBuilder...build().decode(inputStream)} instead. For a
   *     direct equivalent, use {@code
   *     ConfigDecoder.preconfigured().setValueMapper(mapper).decode(inputStream)}.
   */
  @Deprecated(forRemoval = true)
  static Config fromPklBinary(InputStream inputStream, ValueMapper mapper) {
    return ConfigDecoder.preconfigured().setValueMapper(mapper).decode(inputStream);
  }

  /**
   * Decodes a config from the supplied {@link InputStream}.
   *
   * @return the decoded config
   * @deprecated Use {@code ConfigDecoder.preconfigured().decode(inputStream)} instead.
   */
  @Deprecated(forRemoval = true)
  static Config fromPklBinary(InputStream inputStream) {
    return ConfigDecoder.preconfigured().decode(inputStream);
  }
}

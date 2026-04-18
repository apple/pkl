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

/**
 * A root, intermediate, or leaf node in a configuration tree.
 *
 * <p>To navigate to a child node, use {@link #get(String)} with the child's unqualified name.
 *
 * <p>To retrieve this node's value, use:
 *
 * <ul>
 *   <li>{@link #as(Class)} for non-null types.
 *   <li>{@link #asNullable(Class)} for nullable types.
 *   <li>{@link #as(JavaType)} for fully specified types, such as {@code List<@Nullable String>}.
 * </ul>
 *
 * <p>Whether a method can return null depends on the method and target type used. For example,
 * {@code asNullable(String.class)} can return {@code null}, while {@code
 * as(JavaType.listOfNullable(String.class))} can return a {@code List<String>} with nullable
 * elements. These nullness rules are for static analysis tools such as IntelliJ IDEA and NullAway
 * and are not enforced at runtime.
 */
@SuppressWarnings({"DeprecatedIsStillUsed"})
public interface Config {
  /**
   * Returns the qualified name of this node, or the empty string if this is the root node.
   *
   * <p>The qualified name is formed by joining child names using {@code '.'}. For example, {@code
   * rootNode.get("foo").get("bar").getQualifiedName()} returns {@code "foo.bar"}.
   */
  String getQualifiedName();

  /**
   * Returns the underlying value of this node.
   *
   * <p>This value is typically accessed indirectly via {@link #as(Class)}, {@link
   * #asNullable(Class)}, or {@link #as(JavaType)}.
   */
  Object getRawValue();

  /**
   * Returns the child node with the given unqualified name.
   *
   * <p>For example, {@code get("foo").get("bar")} returns the child named {@code "bar"} of the
   * child named {@code "foo"}. Passing a qualified name to this method, as in {@code
   * get("foo.bar")}, is not supported.
   *
   * @throws NoSuchChildException if a child with the given name does not exist
   */
  Config get(String childName);

  /**
   * Returns this node's value as a non-null value of the given {@link Class}.
   *
   * <p>If this node's value may be {@code null}, use {@link #asNullable(Class)} instead.
   *
   * @throws ConversionException if the value cannot be converted to the given type
   */
  <T> T as(Class<T> type);

  /**
   * Returns this node's value as a nullable value of the given {@link Class}.
   *
   * <p>If this node's value cannot be {@code null}, use {@link #as(Class)} instead.
   *
   * @throws ConversionException if the value cannot be converted to the given type
   */
  default <T> @Nullable T asNullable(Class<T> type) {
    return as(type); // currently no difference at runtime
  }

  /**
   * Returns this node's value as a non-null value of the given {@link Type}.
   *
   * <p>If this node's value may be {@code null}, use {@link #asNullable(Type)} instead.
   *
   * <p>Use this method when the target type is already available as a {@link Type}; otherwise,
   * prefer {@link #as(Class)} or {@link #as(JavaType)}.
   *
   * @throws ConversionException if the value cannot be converted to the given type
   */
  <T> T as(Type type);

  /**
   * Returns this node's value as a nullable value of the given {@link Type}.
   *
   * <p>If this node's value cannot be {@code null}, use {@link #as(Type)} instead.
   *
   * <p>Use this method when the target type is already available as a {@link Type}; otherwise,
   * prefer {@link #asNullable(Class)} or {@link #as(JavaType)}.
   *
   * @throws ConversionException if the value cannot be converted to the given type
   */
  default <T> @Nullable T asNullable(Type type) {
    return as(type); // currently no difference at runtime
  }

  /**
   * Returns this node's value as the given {@link JavaType}.
   *
   * <p>Use this method when you need a fully specified type, such as {@code List<@Nullable
   * String>}.
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

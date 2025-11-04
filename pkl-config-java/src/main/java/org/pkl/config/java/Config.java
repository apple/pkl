/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.util.Map;
import org.pkl.config.java.mapper.ConversionException;
import org.pkl.config.java.mapper.ValueMapper;
import org.pkl.core.Composite;
import org.pkl.core.Evaluator;
import org.pkl.core.PklBinaryDecoder;

/**
 * A root, intermediate, or leaf node in a configuration tree. Child nodes can be obtained by name
 * using {@link #get(String)}. To consume the node's composite or scalar value, convert the value to
 * the desired Java type, using one of the provided {@link #as} methods.
 */
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
  <T> T as(Class<T> type);

  /**
   * Converts this node's value to the given {@link Type}.
   *
   * <p>Note that usages of this method are not type safe.
   *
   * @throws ConversionException if the value cannot be converted to the given type
   */
  <T> T as(Type type);

  /**
   * Converts this node's value to the given {@link JavaType}.
   *
   * @throws ConversionException if the value cannot be converted to the given type
   */
  <T> T as(JavaType<T> type);

  /**
   * Decode a config from the supplied byte array.
   *
   * @return the encoded config
   */
  static Config fromPklBinary(byte[] bytes, ValueMapper mapper) {
    return makeConfig(PklBinaryDecoder.decode(bytes), mapper);
  }

  /**
   * Decode a config from the supplied byte array using a preconfigured {@link ValueMapper}.
   *
   * @return the encoded config
   */
  static Config fromPklBinary(byte[] bytes) {
    return fromPklBinary(bytes, ValueMapper.preconfigured());
  }

  /**
   * Decode a config from the supplied {@link InputStream} using a preconfigured {@link
   * ValueMapper}.
   *
   * @return the encoded config
   */
  static Config fromPklBinary(InputStream inputStream, ValueMapper mapper) {
    return makeConfig(PklBinaryDecoder.decode(inputStream), mapper);
  }

  /**
   * Decode a config from the supplied {@link InputStream}.
   *
   * @return the encoded config
   */
  static Config fromPklBinary(InputStream inputStream) {
    return fromPklBinary(inputStream, ValueMapper.preconfigured());
  }

  static Config makeConfig(Object decoded, ValueMapper mapper) {
    if (decoded instanceof Composite composite) {
      return new CompositeConfig("", mapper, composite);
    }
    if (decoded instanceof Map<?, ?> map) {
      return new MapConfig("", mapper, map);
    }
    return new LeafConfig("", mapper, decoded);
  }
}

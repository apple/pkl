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
package org.pkl.core.util.yaml.snake;

import java.util.*;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.events.ImplicitTuple;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;

/** Utilities for parsing YAML. */
public final class YamlUtils {
  private static final ScalarResolver YAML_COMPAT_EMITTER_RESOLVER =
      new YamlCompatEmitterResolver();
  private static final ScalarResolver YAML_COMPAT_PARSER_RESOLVER = new YamlCompatParserResolver();
  private static final ScalarResolver YAML_11_RESOLVER = new Yaml11Resolver();
  private static final ScalarResolver YAML_12_RESOLVER = new Yaml12Resolver();

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private static final Optional<String> STRING_TAG = Optional.of(Tag.STR.toString());

  private static final ImplicitTuple TUPLE = new ImplicitTuple(true, true);

  private YamlUtils() {}

  public static ScalarResolver getEmitterResolver(String mode) {
    return getScalarResolver(mode, YAML_COMPAT_EMITTER_RESOLVER);
  }

  public static ScalarResolver getParserResolver(String mode) {
    return getScalarResolver(mode, YAML_COMPAT_PARSER_RESOLVER);
  }

  /**
   * Constructs a {@link ScalarEvent} for emitting the given value as a YAML string. Uses the given
   * resolver to determine whether the string needs to be quoted.
   */
  public static ScalarEvent stringScalar(String value, ScalarResolver resolver) {
    var scalarStyle = value.contains("\n") ? ScalarStyle.LITERAL : ScalarStyle.PLAIN;
    var inferredTag = resolver.resolve(value, true);
    var tuple = new ImplicitTuple(Tag.STR.equals(inferredTag), true);
    return new ScalarEvent(Optional.empty(), STRING_TAG, tuple, value, scalarStyle);
  }

  /** Constructs a {@link ScalarEvent} for emitting the given value in plain style. */
  public static ScalarEvent plainScalar(String value, Tag tag) {
    return new ScalarEvent(
        Optional.empty(), Optional.of(tag.toString()), TUPLE, value, ScalarStyle.PLAIN);
  }

  private static ScalarResolver getScalarResolver(
      String mode, ScalarResolver yamlCompatEmitterResolver) {
    switch (mode) {
      case "compat":
        return yamlCompatEmitterResolver;
      case "1.1":
        return YAML_11_RESOLVER;
      case "1.2":
        return YAML_12_RESOLVER;
      default:
        throw new IllegalArgumentException(mode);
    }
  }
}

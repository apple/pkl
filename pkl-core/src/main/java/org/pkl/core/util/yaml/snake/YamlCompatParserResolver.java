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
package org.pkl.core.util.yaml.snake;

import java.util.regex.Pattern;
import org.snakeyaml.engine.v2.nodes.Tag;

/**
 * Modification of {@link Yaml11Resolver} tuned for <em>parsing</em> real-world YAML 1.1
 * <em>and</em> 1.2. Modified regular expressions are marked with `(*)`.
 */
final class YamlCompatParserResolver extends YamlResolver {
  // https://yaml.org/type/int.html
  // Note: base-60 is not supported because it is rarely used
  // and it's common for base-60 like YAML strings not to be quoted.
  // https://yaml.org/spec/1.2/#id2804923
  private static final Pattern INT =
      Pattern.compile(
          "[-+]?(?:"
              + "0b[0-1_]+" // base 2
              + "|0o?[0-7_]+" // base 8 (*)
              + "|(?:0|[1-9][0-9_]*)" // base 10
              + "|0x[0-9a-fA-F_]+" // base 16
              + ")");

  // https://yaml.org/type/float.html
  // Note: base-60 is not supported because it is rarely used
  // and it's common for base-60 like YAML strings not to be quoted.
  // https://yaml.org/spec/1.2/#id2804923
  private static final Pattern FLOAT =
      Pattern.compile(
          // YAML 1.2 number w/ 1.1 underscores (*)
          // note: this means that a single dot is parsed as string rather than float
          "[-+]?(?:\\.[0-9_]+|[0-9][0-9_]*(?:\\.[0-9_]*)?)(?:[eE][-+]?[0-9]+)?"
              + "|[-+]?\\.(?:inf|Inf|INF)" // infinity
              + "|\\.(?:nan|NaN|NAN)"); // not a number

  YamlCompatParserResolver() {
    addImplicitResolver(Tag.NULL, Yaml11Resolver.NULL1, "~nN");
    addImplicitResolver(Tag.NULL, Yaml11Resolver.NULL2, "\0");
    addImplicitResolver(Tag.BOOL, Yaml11Resolver.BOOL, "yYnNtTfFoO");
    addImplicitResolver(Tag.INT, INT, "-+0123456789"); // must come before float
    addImplicitResolver(Tag.FLOAT, FLOAT, "-+0123456789.");
  }
}

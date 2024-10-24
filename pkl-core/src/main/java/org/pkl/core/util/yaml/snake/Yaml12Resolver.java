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

/** Scalar resolver compliant with YAML 1.2 Core schema. */
final class Yaml12Resolver extends YamlResolver {
  // https://yaml.org/spec/1.2/#id2804923
  private static final Pattern NULL1 = Pattern.compile("~|null|Null|NULL");

  // https://yaml.org/spec/1.2/#id2804923
  private static final Pattern NULL2 = Pattern.compile("");

  // https://yaml.org/spec/1.2/#id2804923
  private static final Pattern BOOL = Pattern.compile("true|True|TRUE|false|False|FALSE");

  // https://yaml.org/spec/1.2/#id2804923
  private static final Pattern INT =
      Pattern.compile(
          "[-+]?[0-9]+" // base 10
              + "|0o[0-7]+" // base 8 (no sign)
              + "|0x[0-9a-fA-F]+"); // base 16 (no sign)

  // https://yaml.org/spec/1.2/#id2804923
  private static final Pattern FLOAT =
      Pattern.compile(
          "[-+]?(?:\\.[0-9]+|[0-9]+(?:\\.[0-9]*)?)(?:[eE][-+]?[0-9]+)?" // number
              + "|[-+]?(?:\\.inf|\\.Inf|\\.INF)" // infinity
              + "|\\.nan|\\.NaN|\\.NAN"); // not a number

  Yaml12Resolver() {
    addImplicitResolver(Tag.NULL, NULL1, "~nN");
    addImplicitResolver(Tag.NULL, NULL2, "\0");
    addImplicitResolver(Tag.BOOL, BOOL, "tTfF");
    addImplicitResolver(Tag.INT, INT, "-+0123456789"); // must come before float
    addImplicitResolver(Tag.FLOAT, FLOAT, "-+0123456789.");
  }
}

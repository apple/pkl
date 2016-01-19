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

import java.util.regex.Pattern;
import org.snakeyaml.engine.v2.nodes.Tag;

/** Scalar resolver compliant with YAML 1.1. */
final class Yaml11Resolver extends YamlResolver {
  // https://yaml.org/type/null.html
  static final Pattern NULL1 = Pattern.compile("~|null|Null|NULL");

  // https://yaml.org/type/null.html
  static final Pattern NULL2 = Pattern.compile("");

  // https://yaml.org/type/bool.html
  static final Pattern BOOL =
      Pattern.compile(
          "y|Y|yes|Yes|YES|n|N|no|No|NO"
              + "|true|True|TRUE|false|False|FALSE"
              + "|on|On|ON|off|Off|OFF");

  // https://yaml.org/type/int.html
  static final Pattern INT =
      Pattern.compile(
          "[-+]?(?:"
              + "0b[0-1_]+" // base 2
              + "|0[0-7_]+" // base 8
              + "|(?:0|[1-9][0-9_]*)" // base 10
              + "|0x[0-9a-fA-F_]+" // base 16
              + "|[1-9][0-9_]*(?::[0-5]?[0-9])+" // base 60
              + ")");

  // https://yaml.org/type/float.html
  // Assumption: `\.[0-9.]*` should be `\.[0-9_]*` (corrected below).
  // This is backed up by the example `685.230_15e+03`.
  private static final Pattern FLOAT =
      Pattern.compile(
          "[-+]?(?:[0-9][0-9_]*)?\\.[0-9_]*(?:[eE][-+][0-9]+)?" // base 10
              + "|[-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+\\.[0-9_]*" // base 60
              + "|[-+]?\\.(?:inf|Inf|INF)" // infinity
              + "|\\.(?:nan|NaN|NAN)"); // not a number

  Yaml11Resolver() {
    addImplicitResolver(Tag.NULL, NULL1, "~nN");
    addImplicitResolver(Tag.NULL, NULL2, "\0");
    addImplicitResolver(Tag.BOOL, BOOL, "yYnNtTfFoO");
    addImplicitResolver(Tag.INT, INT, "-+0123456789"); // must come before float
    addImplicitResolver(Tag.FLOAT, FLOAT, "-+0123456789.");
  }
}

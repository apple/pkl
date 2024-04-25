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
package org.pkl.core.util.xml;

public abstract class XmlValidator {
  public abstract boolean isValidName(String name);

  public static XmlValidator create(String version) {
    return switch (version) {
      case "1.0" -> new Xml10Validator();
      case "1.1" -> new Xml11Validator();
      default -> throw new IllegalArgumentException(version);
    };
  }
}

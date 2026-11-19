/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util.yaml;

import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.exceptions.Mark;

/**
 * An unchecked exception to indicate that a YAML document cannot be represented as the requested
 * Pkl value.
 *
 * <p>Unlike {@code YamlEngineException}, whose messages originate from SnakeYAML, messages carried
 * by this exception are written by Pkl and are suitable for presenting to users as-is.
 *
 * <p>SnakeYAML wraps any exception other than {@code YamlEngineException} thrown during
 * construction, so this exception is observed as the cause of a {@code YamlEngineException} rather
 * than caught directly.
 */
public final class ParseException extends RuntimeException {
  private final @Nullable Mark location;

  public ParseException(String message, @Nullable Mark location) {
    super(location == null ? message : message + location);
    this.location = location;
  }

  /**
   * Returns the location at which the error occurred.
   *
   * @return the error location
   */
  public @Nullable Mark getLocation() {
    return location;
  }
}

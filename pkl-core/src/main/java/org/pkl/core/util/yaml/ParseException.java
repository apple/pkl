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
package org.pkl.core.util.yaml;

import org.pkl.core.util.Nullable;
import org.snakeyaml.engine.v2.exceptions.Mark;

/** An unchecked exception to indicate that an input does not qualify as valid YAML. */
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

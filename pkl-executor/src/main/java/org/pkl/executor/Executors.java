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
package org.pkl.executor;

import java.nio.file.Path;
import java.util.List;

/** A factory for {@link Executor}s. */
public final class Executors {
  private Executors() {}

  /**
   * Creates an executor that evaluates Pkl modules in the caller's JVM with the given fat Jar Pkl
   * distributions (typically <em>pkl-config-java-all</em>).
   *
   * @throws IllegalArgumentException if a Jar file cannot be found or is not a valid Pkl
   *     distribution
   */
  public static Executor embedded(List<Path> pklFatJars) {
    return new EmbeddedExecutor(pklFatJars);
  }
}

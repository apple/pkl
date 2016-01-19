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
package org.pkl.executor;

import java.nio.file.Path;

/**
 * Evaluates Pkl modules in a sandbox. The modules to be evaluated must have an `amends`, `extends`,
 * or `module` clause annotated with {@code @ModuleInfo { minPklVersion = "x.y.z" }}. To avoid
 * resource leaks, an executor must be {@link #close() closed} after use.
 */
public interface Executor extends AutoCloseable {
  /**
   * Evaluates the given module with the given options, returning the module's output.
   *
   * <p>If evaluation fails, throws {@link ExecutorException} with a descriptive message.
   */
  String evaluatePath(Path modulePath, ExecutorOptions options);
}

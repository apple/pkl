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
package org.pkl.core;

import java.util.logging.Level;

/**
 * SPI for log messages emitted by the Pkl evaluator. Use {@link EvaluatorBuilder#setLogger} to set
 * a logger. See {@link Loggers} for predefined loggers.
 */
@SuppressWarnings("unused")
public interface Logger {
  /**
   * @return Whether the provided logging level is enabled; defaults to `false`
   */
  default boolean isLevelEnabled(Level level) {
    return false;
  }

  /** Logs a debug message of some kind. */
  default void debug(String message) {}

  /** Logs the given message on level TRACE. */
  default void trace(String message, StackFrame frame) {}

  /** Logs the given message on level WARN. */
  default void warn(String message, StackFrame frame) {}
}

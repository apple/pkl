/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.config.java;

import org.pkl.config.java.mapper.ValueMapper;
import org.pkl.core.ModuleSource;

/**
 * An evaluator that returns a {@link Config} tree.
 *
 * <p>Use {@link ConfigEvaluatorBuilder} to create instances of this type, configured according to
 * your needs.
 */
public interface ConfigEvaluator extends AutoCloseable {
  /** Shorthand for {@code ConfigEvaluatorBuilder.preconfigured().build()}. */
  static ConfigEvaluator preconfigured() {
    return ConfigEvaluatorBuilder.preconfigured().build();
  }

  /** Returns the underlying value mapper of this evaluator. */
  ValueMapper getValueMapper();

  /**
   * Returns a new config evaluator with the same underlying evaluator and the given value mapper.
   */
  ConfigEvaluator setValueMapper(ValueMapper mapper);

  /** Evaluates the given module source into a {@link Config} tree. */
  Config evaluate(ModuleSource moduleSource);

  /** Evaluates the given module's {@code output.value} property into a {@link Config} tree. */
  Config evaluateOutputValue(ModuleSource moduleSource);

  /** Evaluates the Pkl expression represented as {@code expression} into a {@link Config} tree. */
  Config evaluateExpression(ModuleSource moduleSource, String expression);

  /**
   * Releases all resources held by this evaluator. If an {@code evaluate} method is currently
   * executing, this method blocks until cancellation of that execution has completed.
   *
   * <p>Once an evaluator has been closed, it can no longer be used, and calling {@code evaluate}
   * methods will throw {@link IllegalStateException}. However, objects previously returned by
   * {@code evaluate} methods remain valid.
   */
  @Override
  void close();
}

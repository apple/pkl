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
package org.pkl.core;

import java.util.Map;
import org.pkl.core.runtime.VmEvalException;

/**
 * Evaluates a Pkl module through different modes of evaluation. Throws {@link VmEvalException} if
 * an error occurs during evaluation.
 *
 * <p>Evaluated modules, and modules imported by them, are cached based on their origin. This is
 * important to guarantee consistent evaluation results, for example when the same module is used by
 * multiple other modules. To reset the cache, {@link #close()} the current instance and create a
 * new one.
 *
 * <p>Construct an evaluator through {@link EvaluatorBuilder}.
 */
@SuppressWarnings("unused")
public interface Evaluator extends AutoCloseable {

  /** Shorthand for {@code EvaluatorBuilder.preconfigured().build()}. */
  static Evaluator preconfigured() {
    return EvaluatorBuilder.preconfigured().build();
  }

  /**
   * Evaluates the module, returning the Java representation of the module object.
   *
   * @throws PklException if an error occurs during evaluation
   * @throws IllegalStateException if this evaluator has already been closed
   */
  PModule evaluate(ModuleSource moduleSource);

  /**
   * Evaluates a module's {@code output.text} property.
   *
   * @throws PklException if an error occurs during evaluation
   * @throws IllegalStateException if this evaluator has already been closed
   */
  String evaluateOutputText(ModuleSource moduleSource);

  /**
   * Evaluates a module's {@code output.value} property.
   *
   * @throws PklException if an error occurs during evaluation
   * @throws IllegalStateException if this evaluator has already been closed
   */
  Object evaluateOutputValue(ModuleSource moduleSource);

  /**
   * Evaluates a module's {@code output.files} property.
   *
   * @throws PklException if an error occurs during evaluation
   * @throws IllegalStateException if this evaluator has already been closed
   */
  Map<String, FileOutput> evaluateOutputFiles(ModuleSource moduleSource);

  /**
   * Evaluates the Pkl expression represented as {@code expression}, returning the Java
   * representation of the result.
   *
   * <p>The following table describes how Pkl types are represented in Java:
   *
   * <table>
   *   <thead>
   *     <tr>
   *       <th>Pkl type</th>
   *       <th>Java type</th>
   *     </tr>
   *   </thead>
   *   <tbody>
   *     <tr>
   *       <td>Null</td>
   *       <td>{@link PNull}</td>
   *     </tr>
   *     <tr>
   *       <td>String</td>
   *       <td>{@link String}</td>
   *     </tr>
   *     <tr>
   *       <td>Boolean</td>
   *       <td>{@link Boolean}</td>
   *     </tr>
   *     <tr>
   *       <td>Int</td>
   *       <td>{@link Long}</td>
   *     </tr>
   *     <tr>
   *       <td>Float</td>
   *       <td>{@link Double}</td>
   *     </tr>
   *     <tr>
   *       <td>Typed, Dynamic</td>
   *       <td>{@link PObject} ({@link PModule} if the object is a module)</td>
   *     </tr>
   *     <tr>
   *       <td>Mapping, Map</td>
   *       <td>{@link Map}</td>
   *     </tr>
   *     <tr>
   *       <td>Listing, List</td>
   *       <td>{@link java.util.List}</td>
   *     </tr>
   *     <tr>
   *       <td>Set</td>
   *       <td>{@link java.util.Set}</td>
   *     </tr>
   *     <tr>
   *       <td>Pair</td>
   *       <td>{@link Pair}</td>
   *     </tr>
   *     <tr>
   *       <td>Regex</td>
   *       <td>{@link java.util.regex.Pattern}</td>
   *     </tr>
   *     <tr>
   *       <td>DataSize</td>
   *       <td>{@link DataSize}</td>
   *     </tr>
   *     <tr>
   *       <td>Duration</td>
   *       <td>{@link Duration}</td>
   *     </tr>
   *     <tr>
   *       <td>Class</td>
   *       <td>{@link PClass}</td>
   *     </tr>
   *     <tr>
   *       <td>TypeAlias</td>
   *       <td>{@link TypeAlias}</td>
   *     </tr>
   *   </tbody>
   * </table>
   *
   * <p>The following Pkl types have no Java representation, and an error is thrown if an expression
   * computes to a value of these types:
   *
   * <ul>
   *   <li>IntSeq
   *   <li>Function
   * </ul>
   *
   * @throws PklException if an error occurs during evaluation
   * @throws IllegalStateException if this evaluator has already been closed
   */
  Object evaluateExpression(ModuleSource moduleSource, String expression);

  /**
   * Evaluates the Pkl expression, returning the stringified result.
   *
   * <p>This is equivalent to wrapping the expression with {@code .toString()}
   *
   * @throws PklException if an error occurs during evaluation
   * @throws IllegalStateException if this evaluator has already been closed
   */
  String evaluateExpressionString(ModuleSource moduleSource, String expression);

  /**
   * Evalautes the module's schema, which describes the properties, methods, and classes of a
   * module.
   *
   * @throws PklException if an error occurs during evaluation
   * @throws IllegalStateException if this evaluator has already been closed
   */
  ModuleSchema evaluateSchema(ModuleSource moduleSource);

  /**
   * Evaluates the module's {@code output.value} property, and validates that its type matches the
   * provided class info.
   *
   * @throws PklException if an error occurs during evaluation
   * @throws IllegalStateException if this evaluator has already been closed
   */
  <T> T evaluateOutputValueAs(ModuleSource moduleSource, PClassInfo<T> classInfo);

  /**
   * Runs tests within the module, and returns the test results.
   *
   * <p>This requires that the target module be a test module; it must either amend or extend module
   * {@code "pkl:test"}. Otherwise, a type mismatch error is thrown.
   *
   * <p>This method will write possibly {@code pkl-expected.pcf} and {@code pkl-actual.pcf} files as
   * a sibling of the test module. The {@code overwrite} parameter causes the evaluator to overwrite
   * {@code pkl-expected.pkl} files if they currently exist.
   *
   * @throws PklException if an error occurs during evaluation
   * @throws IllegalStateException if this evaluator has already been closed
   */
  TestResults evaluateTest(ModuleSource moduleSource, boolean overwrite);

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

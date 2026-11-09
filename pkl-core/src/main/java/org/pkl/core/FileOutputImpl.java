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
package org.pkl.core;

import org.graalvm.polyglot.PolyglotException;
import org.pkl.core.runtime.VmTyped;

final class FileOutputImpl implements FileOutput {
  private final VmTyped fileOutput;
  private final EvaluatorImpl evaluator;

  FileOutputImpl(EvaluatorImpl evaluator, VmTyped fileOutput) {
    this.evaluator = evaluator;
    this.fileOutput = fileOutput;
  }

  /**
   * Evaluates the text output of this file.
   *
   * <p>Will throw {@link PklException} if a normal evaluator error occurs.
   *
   * <p>If the evaluator that produced this {@link FileOutput} is closed, an error will be thrown.
   */
  public String getText() {
    try {
      return evaluator.evaluateOutputText(fileOutput);
    } catch (PolyglotException e) {
      if (e.isCancelled()) {
        throw new PklException("The evaluator is no longer available", e);
      }
      throw new PklBugException(e);
    }
  }

  @Override
  public byte[] getBytes() {
    try {
      return evaluator.evaluateOutputBytes(fileOutput);
    } catch (PolyglotException e) {
      if (e.isCancelled()) {
        throw new PklException("The evaluator is no longer available", e);
      }
      throw new PklBugException(e);
    }
  }
}

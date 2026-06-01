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
package org.pkl.profiler;

import java.nio.file.Path;

abstract class ProfilerOutput {
  private final PklProfilerData data;
  private final Path outputFile;

  public ProfilerOutput(PklProfilerData data, Path outputFile) {
    this.data = data;
    this.outputFile = outputFile;
  }

  protected abstract void doWriteOutput(PklProfilerData data, Path outputFile) throws Exception;

  public final void write() {
    try {
      doWriteOutput(data, outputFile);
      System.err.printf("[pkl-profiler] Wrote profiler output to %s\n", outputFile);
    } catch (Exception e) {
      System.err.printf(
          "[pkl-profiler] WARN: failed to write profile to %s: %s\n", outputFile, e.getMessage());
    }
  }
}

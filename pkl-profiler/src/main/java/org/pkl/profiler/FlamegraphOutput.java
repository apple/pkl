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

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class FlamegraphOutput extends ProfilerOutput {
  public FlamegraphOutput(PklProfilerData data, Path outputFile) {
    super(data, outputFile);
  }

  @Override
  protected void doWriteOutput(PklProfilerData data, Path outputFile) throws Exception {
    try (var out = new PrintStream(Files.newOutputStream(outputFile))) {
      var clazz =
          FlamegraphOutput.class
              .getClassLoader()
              .loadClass("com.oracle.truffle.tools.profiler.impl.SVGSamplerOutput");
      var method =
          clazz.getDeclaredMethod("printSamplingFlameGraph", PrintStream.class, List.class);
      method.setAccessible(true);
      method.invoke(clazz, out, data.dataList());
    }
  }
}

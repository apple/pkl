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
package org.pkl.core.parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.pkl.commons.test.FileTestUtils;
import org.pkl.commons.test.FileTestUtilsKt;
import org.pkl.core.Release;
import org.pkl.core.util.IoUtils;

@SuppressWarnings("unused")
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
public class ParserBenchmark {
  @Benchmark
  public void parseStdlib() {
    for (var stdlibModule : Release.current().standardLibrary().modules()) {
      try {
        var moduleSource =
            IoUtils.readClassPathResourceAsString(
                getClass(), "/org/pkl/core/stdlib/%s.pkl".formatted(stdlibModule.substring(4)));
        new Parser().parseModule(moduleSource);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Benchmark
  public void parseSnippetTests() {
    var snippetTestDir =
        FileTestUtils.getRootProjectDir()
            .resolve("pkl-core/src/test/files/LanguageSnippetTests/input");
    for (var snippet : FileTestUtilsKt.listFilesRecursively(snippetTestDir)) {
      try {
        var moduleSource = Files.readString(snippet);
        new Parser().parseModule(moduleSource);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (ParserError ignore) {
      }
    }
  }
}

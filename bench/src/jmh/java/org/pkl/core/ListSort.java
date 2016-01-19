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

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.util.TempFile;
import org.openjdk.jmh.util.TempFileManager;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.repl.ReplRequest;
import org.pkl.core.repl.ReplResponse;
import org.pkl.core.repl.ReplServer;
import org.pkl.core.resource.ResourceReaders;
import org.pkl.core.util.IoUtils;

@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@SuppressWarnings("unused")
public class ListSort {
  private static final ReplServer repl =
      new ReplServer(
          SecurityManagers.defaultManager,
          Loggers.stdErr(),
          List.of(ModuleKeyFactories.standardLibrary),
          List.of(ResourceReaders.file()),
          Map.of(),
          Map.of(),
          null,
          null,
          null,
          IoUtils.getCurrentWorkingDir(),
          StackFrameTransformers.defaultTransformer);
  private static final List<Object> list = new ArrayList<>(100000);

  static {
    var random = new Random(2786433088656064171L);
    for (var i = 0; i < 100000; i++) {
      list.add(random.nextLong());
    }

    TempFile tempFile;
    try {
      tempFile = new TempFileManager().create("bench-nums.txt");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    try (var fw = new FileWriter(tempFile.getAbsolutePath())) {
      for (var elem : list) {
        fw.append(elem.toString()).append('\n');
      }
      fw.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    var responses =
        repl.handleRequest(
            new ReplRequest.Eval(
                "setup",
                "import \"pkl:test\"\n"
                    + "random = test.random\n"
                    + "nums = read(\"file://"
                    + tempFile.getAbsolutePath()
                    + "\").text.split(\"\\n\").dropLast(1).map((it) -> it.toInt())\n"
                    + "cmp = (x, y) -> if (x < y) -1 else if (x == y) 0 else 1",
                false,
                false));
    if (!responses.isEmpty()) {
      throw new AssertionError(responses.get(0));
    }
  }

  @Benchmark
  public String sortPkl() {
    var response =
        repl.handleRequest(
                // append `.length` to avoid rendering the list
                new ReplRequest.Eval("sort", "nums.sort().length", false, false))
            .get(0);
    if (!(response instanceof ReplResponse.EvalSuccess)) {
      throw new AssertionError(response);
    }
    return ((ReplResponse.EvalSuccess) response).getResult();
  }

  @Benchmark
  public String sortWithPkl() {
    var response =
        repl.handleRequest(
                // append `.length` to avoid rendering the list
                new ReplRequest.Eval("sort", "nums.sortWith(cmp).length", false, false))
            .get(0);
    if (!(response instanceof ReplResponse.EvalSuccess)) {
      throw new AssertionError(response);
    }
    return ((ReplResponse.EvalSuccess) response).getResult();
  }

  // note that this is an uneven comparison
  // (timsort vs. merge sort, java.util.ArrayList vs. persistent vector
  @Benchmark
  public List<Object> sortJava() {
    return sort(list);
  }

  private List<Object> sort(List<Object> self) {
    var array = self.toArray();
    Arrays.sort(array);
    return Arrays.asList(array);
  }

  // note that this is an uneven comparison
  // (timsort vs. merge sort, java.util.ArrayList vs. persistent vector
  @Benchmark
  public List<Object> sortWithJava() {
    return sortWith(list, Comparator.comparingLong(x -> (long) x));
  }

  private List<Object> sortWith(List<Object> self, Comparator<Object> comparator) {
    var array = self.toArray();
    Arrays.sort(array, comparator);
    return Arrays.asList(array);
  }
}

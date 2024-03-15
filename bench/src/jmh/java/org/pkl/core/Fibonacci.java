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

import static org.pkl.core.ModuleSource.modulePath;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
public class Fibonacci {
  @Benchmark
  public long fib_class_java() {
    return new FibJavaImpl().fib(35);
  }

  @Benchmark
  public long fib_class() {
    try (var evaluator = Evaluator.preconfigured()) {
      var module = evaluator.evaluate(modulePath("org/pkl/core/fib_class.pkl"));
      return (long) module.getProperties().get("result");
    }
  }

  @Benchmark
  public long fib_class_explicitThis() {
    try (var evaluator = Evaluator.preconfigured()) {
      var module = evaluator.evaluate(modulePath("org/pkl/core/fib_class_explicitThis.pkl"));
      return (long) module.getProperties().get("result");
    }
  }

  @Benchmark
  public long fib_class_typed() {
    try (var evaluator = Evaluator.preconfigured()) {
      var module = evaluator.evaluate(modulePath("org/pkl/core/fib_class_typed.pkl"));
      return (long) module.getProperties().get("result");
    }
  }

  @Benchmark
  public long fib_class_constrained1() {
    try (var evaluator = Evaluator.preconfigured()) {
      var module = evaluator.evaluate(modulePath("org/pkl/core/fib_class_constrained1.pkl"));
      return (long) module.getProperties().get("result");
    }
  }

  @Benchmark
  public long fib_class_constrained2() {
    try (var evaluator = Evaluator.preconfigured()) {
      var module = evaluator.evaluate(modulePath("org/pkl/core/fib_class_constrained2.pkl"));
      return (long) module.getProperties().get("result");
    }
  }

  @Benchmark
  public long fib_module() {
    try (var evaluator = Evaluator.preconfigured()) {
      var module = evaluator.evaluate(modulePath("org/pkl/core/fib_module.pkl"));
      return (long) module.getProperties().get("result");
    }
  }

  @Benchmark
  public long fib_module_explicitThis() {
    try (var evaluator = Evaluator.preconfigured()) {
      var module = evaluator.evaluate(modulePath("org/pkl/core/fib_module_explicitThis.pkl"));
      return (long) module.getProperties().get("result");
    }
  }

  @Benchmark
  public long fib_lambda() {
    try (var evaluator = Evaluator.preconfigured()) {
      var module = evaluator.evaluate(modulePath("org/pkl/core/fib_lambda.pkl"));
      return (long) module.getProperties().get("result");
    }
  }
}

// kept similar to pkl code (class, instance method, long argument)
class FibJavaImpl {
  private Map<Long, Long> memo = new HashMap<>();

  // Calculates the nth Fibonacci number using memoization to avoid redundant calculations.
  long fib(long n) {
    // Base cases: 0 and 1
    if (n < 2) {
      return n;
    }

    // If the result for n is already calculated, return it from memo
    if (memo.containsKey(n)) {
      return memo.get(n);
    }

    // Otherwise, calculate Fibonacci recursively and store the result in memo
    long result = fib(n - 1) + fib(n - 2);
    memo.put(n, result);
    return result;
  }
}

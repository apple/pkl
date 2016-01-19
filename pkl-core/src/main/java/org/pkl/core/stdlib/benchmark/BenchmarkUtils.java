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
package org.pkl.core.stdlib.benchmark;

import java.util.function.*;
import org.pkl.core.DurationUnit;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.util.Nullable;

final class BenchmarkUtils {
  private static final VmObjectFactory<BenchmarkResult> benchmarkResultFactory =
      new VmObjectFactory<BenchmarkResult>(BenchmarkModule::getBenchmarkResultClass)
          .addIntProperty("iterations", BenchmarkResult::iterations)
          .addIntProperty("repetitions", BenchmarkResult::repetitions)
          .addValueProperty("samples", BenchmarkResult::samples)
          .addDurationProperty("min", BenchmarkResult::min)
          .addDurationProperty("max", BenchmarkResult::max)
          .addDurationProperty("mean", BenchmarkResult::mean)
          // use short names of similar length in report
          .addDurationProperty("stdev", BenchmarkResult::stdDeviation)
          .addDurationProperty("error", BenchmarkResult::errorMargin);

  private BenchmarkUtils() {}

  static VmTyped runBenchmark(VmTyped benchmark, LongFunction<Object> iterationRunner) {
    // inputs
    var iterations = ((Long) VmUtils.readMember(benchmark, Identifier.ITERATIONS)).intValue();
    var iterationTime = (VmDuration) VmUtils.readMember(benchmark, Identifier.ITERATION_TIME);
    var iterationTimeNanos = Math.round(iterationTime.getValue(DurationUnit.NANOS));
    var isVerbose = (boolean) VmUtils.readMember(benchmark, Identifier.IS_VERBOSE);

    // warmup and sizing
    long repetitions = 0;
    for (int i = 0; i < iterations; i++) {
      repetitions = runWarmupIteration(iterationRunner, iterationTimeNanos);
    }

    // measurement
    var samples = new double[iterations];
    var min = Double.MAX_VALUE;
    var max = Double.MIN_VALUE;
    var mean = 0.0;
    var sumOfSquares = 0.0;

    for (int i = 0; i < iterations; i++) {
      var actualIterationTime = runMeasureIteration(iterationRunner, repetitions);
      var sample = ((double) actualIterationTime) / repetitions;
      samples[i] = sample;
      min = Math.min(min, sample);
      max = Math.max(max, sample);

      // Art of Computer Programming vol. 2, Knuth, 4.2.2, (15) and (16)
      var delta = sample - mean;
      mean += delta / (i + 1);
      sumOfSquares += delta * (sample - mean);
    }

    // result
    // https://en.wikipedia.org/wiki/Standard_error#Assumptions_and_usage
    var variance = sumOfSquares / (iterations - 1);
    var stdDeviation = Math.sqrt(variance);
    var stdError = stdDeviation / Math.sqrt(iterations);
    var errorMargin = stdError * 2.576;
    var result =
        new BenchmarkResult(
            iterations,
            repetitions,
            isVerbose ? samples : null,
            min,
            max,
            mean,
            stdDeviation,
            errorMargin);
    return benchmarkResultFactory.create(result);
  }

  private static long runWarmupIteration(LongFunction<Object> iterationRunner, long iterationTime) {
    // 1, 2, 5, 1, 2, 5, etc.
    var state = 1;
    // 1, 2, 5, 10, 20, 50, etc. (more human-friendly than 1, 2, 4, 8, 16, 32, etc.)
    var repetitions = 1L;
    // try to land on `iterationTime * 1` on average
    var minIterationTime = Math.round(iterationTime * 2 / 3.0);
    var startTime = System.nanoTime();

    do {
      iterationRunner.apply(repetitions);
      if (state == 1) {
        state = 2;
        repetitions *= 2;
      } else if (state == 2) {
        state = 5;
        repetitions = repetitions * 5 / 2;
      } else {
        state = 1;
        repetitions *= 2;
      }
    } while (System.nanoTime() - startTime < minIterationTime);

    // we ran roughly this many repetitions in total
    return repetitions;
  }

  private static long runMeasureIteration(LongFunction<Object> iterationRunner, long repetitions) {
    var startTime = System.nanoTime();
    iterationRunner.apply(repetitions);
    return System.nanoTime() - startTime;
  }

  private static final class BenchmarkResult {
    final long iterations;
    final long repetitions;
    final VmValue samples;
    final VmDuration min;
    final VmDuration max;
    final VmDuration mean;
    final VmDuration stdDeviation;
    final VmDuration errorMargin;

    BenchmarkResult(
        long iterations,
        long repetitions,
        double @Nullable [] samples,
        double min,
        double max,
        double mean,
        double stdDeviation,
        double errorMargin) {
      this.iterations = iterations;
      this.repetitions = repetitions;
      var unit = chooseUnit(mean);
      this.samples = toList(samples, unit);
      this.min = toDuration(min, unit);
      this.max = toDuration(max, unit);
      this.mean = toDuration(mean, unit);
      this.stdDeviation = toDuration(stdDeviation, unit);
      this.errorMargin = toDuration(errorMargin, unit);
    }

    long iterations() {
      return iterations;
    }

    long repetitions() {
      return repetitions;
    }

    VmValue samples() {
      return samples;
    }

    VmDuration min() {
      return min;
    }

    VmDuration max() {
      return max;
    }

    VmDuration mean() {
      return mean;
    }

    VmDuration stdDeviation() {
      return stdDeviation;
    }

    VmDuration errorMargin() {
      return errorMargin;
    }

    private static DurationUnit chooseUnit(double nanos) {
      if (nanos < 1_000) return DurationUnit.NANOS;
      if (nanos < 1_000_000) return DurationUnit.MICROS;
      if (nanos < 1_000_000_000) return DurationUnit.MILLIS;
      return DurationUnit.SECONDS;
    }

    private static VmDuration toDuration(double nanos, DurationUnit unit) {
      return new VmDuration(nanos / unit.getNanos(), unit);
    }

    private static VmValue toList(double @Nullable [] values, DurationUnit unit) {
      if (values == null) return VmNull.withoutDefault();

      var builder = VmList.EMPTY.builder();
      for (var value : values) builder.add(toDuration(value, unit));
      return builder.build();
    }
  }
}

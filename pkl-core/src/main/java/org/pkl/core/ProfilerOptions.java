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
package org.pkl.core;

import java.nio.file.Path;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public record ProfilerOptions(Cpu cpu) {
  /** Returns a new {@link ProfilerOptions} given the system settings. */
  public static ProfilerOptions fromSystemProperties() {
    var cpuOutputFile = getProperty(CPU_OUTPUT_FILE, Path::of, Cpu.DEFAULT.outputFile);
    var cpuSamplePeriod = getProperty(CPU_SAMPLE_PERIOD, Long::parseLong, Cpu.DEFAULT.samplePeriod);
    return new ProfilerOptions(new Cpu(cpuOutputFile, cpuSamplePeriod));
  }

  public static void clearSystemProperties() {
    System.clearProperty(CPU_OUTPUT_FILE);
    System.clearProperty(CPU_SAMPLE_PERIOD);
  }

  public static ProfilerOptions DEFAULT = new ProfilerOptions(Cpu.DEFAULT);

  public record Cpu(@Nullable Path outputFile, long samplePeriod) {
    public static Cpu DEFAULT = new Cpu(null, 10);

    public boolean isEnabled() {
      return outputFile != null;
    }
  }

  private static final String CPU_OUTPUT_FILE = "org.pkl.core.ProfileOptions.Cpu.outputFile";

  private static final String CPU_SAMPLE_PERIOD = "org.pkl.core.ProfileOptions.Cpu.samplePeriod";

  private static <T extends @Nullable Object> T getProperty(
      String name, Function<String, T> mapper, T defaultValue) {
    var prop = System.getProperty(name);
    if (prop == null) {
      return defaultValue;
    }
    return mapper.apply(prop);
  }

  private static <T> void writeProperty(String name, @Nullable T prop, Function<T, String> mapper) {
    if (prop == null) return;
    System.setProperty(name, mapper.apply(prop));
  }

  /** Convenience method for configuring Pkl with profiler options. */
  public void configureSystemProperties() {
    // if profiling is not enabled, don't bother writing any system properties
    if (cpu.outputFile == null) return;
    writeProperty(CPU_OUTPUT_FILE, cpu.outputFile, Path::toString);
    writeProperty(CPU_SAMPLE_PERIOD, cpu.samplePeriod, (it) -> Long.toString(it));
  }
}

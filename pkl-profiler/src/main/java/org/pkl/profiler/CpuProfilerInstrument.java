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

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.impl.CPUSamplerInstrument;
import com.oracle.truffle.tools.profiler.impl.ProfilerToolFactory;
import java.time.LocalDateTime;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ProfilerOptions;
import org.pkl.core.ProfilerOptions.Cpu;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.util.LateInit;

/**
 * A profiler for Pkl that samples the CPU and generates pprof output.
 *
 * <p>Note that this intentionally doesn't use the {@link OptionDescriptors} and {@link
 * Env#getOptions()} mechanism to fetch profiling options right now. This is because:
 *
 * <ol>
 *   <li>Pkl's polyglot engine needs to be a singleton because it treats stdlib modules as
 *       singletons; using different engines would result in errors like "Invalid sharing of AST
 *       Nodes".
 *   <li>Truffle instruments are associated with engine instances.
 * </ol>
 *
 * So, configuring this profiler via the engine options isn't meaningful, and just leads to more
 * roundabout code.
 */
@Registration(id = CpuProfilerInstrument.ID, name = "Pkl CPU Profiler")
public final class CpuProfilerInstrument extends TruffleInstrument {

  public static final String ID = "pkl-cpu-profiler";

  private @Nullable LocalDateTime startTime;
  @LateInit private Cpu cpuProfilerOptions;
  @LateInit private CPUSampler cpuSampler;
  private static final ProfilerToolFactory<CPUSampler> cpuSamplerFactory = getCpuSamplerFactory();

  // if we omit `name = ""`, we get a different option name.
  @SuppressWarnings("DefaultAnnotationParam")
  @Option(
      name = "",
      help = "Enables the CPU profiler (default: false)",
      stability = OptionStability.STABLE,
      category = OptionCategory.USER)
  public static final OptionKey<Boolean> ENABLED_OPTION_KEY = new OptionKey<>(false);

  private boolean isFlushed = false;

  /**
   * Returns the factory for {@link CPUSampler}.
   *
   * <p>For some reason, their {@code createFactory} is a private method, so reflection is needed.
   * GraalVM's {@link CPUSamplerInstrument} does the same thing.
   */
  @SuppressWarnings("unchecked")
  private static ProfilerToolFactory<CPUSampler> getCpuSamplerFactory() {
    try {
      var createFactoryMethod = CPUSampler.class.getDeclaredMethod("createFactory");
      createFactoryMethod.setAccessible(true);
      return (ProfilerToolFactory<CPUSampler>) createFactoryMethod.invoke(null);
    } catch (Exception ex) {
      throw new AssertionError(ex);
    }
  }

  @Override
  protected void onCreate(Env env) {
    cpuSampler = cpuSamplerFactory.create(env);
    cpuProfilerOptions = ProfilerOptions.fromSystemProperties().cpu();
    if (cpuProfilerOptions.isEnabled()) {
      cpuSampler.setPeriod(cpuProfilerOptions.samplePeriod());
      cpuSampler.setFilter(
          SourceSectionFilter.newBuilder().mimeTypeIs(VmLanguage.MIME_TYPE).build());
      cpuSampler.setCollecting(true);
      startTime = LocalDateTime.now();
    }
  }

  @Override
  protected void onFinalize(Env env) {
    flush();
  }

  @Override
  protected void onDispose(Env env) {
    flush();
  }

  @Override
  protected OptionDescriptors getOptionDescriptors() {
    return new CpuProfilerInstrumentOptionDescriptors();
  }

  private void flush() {
    if (!cpuProfilerOptions.isEnabled() || isFlushed) return;
    isFlushed = true;
    var outputFile = cpuProfilerOptions.outputFile();
    assert outputFile != null;
    assert startTime != null;
    // must read data before closing; `close()` clears it (see `CPUSampler#clearData()`).
    var data = new PklProfilerData(startTime, cpuSampler.getPeriod(), cpuSampler.getDataList());
    cpuSampler.close();
    new PProfOutput(data, outputFile).write();
  }
}

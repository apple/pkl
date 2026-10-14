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

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.impl.CPUSamplerInstrument;
import com.oracle.truffle.tools.profiler.impl.ProfilerToolFactory;
import org.jspecify.annotations.Nullable;
import org.pkl.profiler.impl.CpuProfilerImpl;

/**
 * A profiler for Pkl that samples the CPU and generates pprof output.
 *
 * <p>This instrument doesn't take any engine options. Pkl's polyglot engine is a singleton (stdlib
 * modules are singletons, so using different engines results in errors like "Invalid sharing of AST
 * Nodes"), and Truffle instruments are associated with engine instances, so there's no way to
 * configure the profiler per run via engine options.
 *
 * <p>Instead, the instrument exposes a {@link CpuProfiler} service. Look it up from the engine's
 * instrument and call {@link CpuProfiler#start} and {@link CpuProfiler#stop} to profile a section
 * of code.
 */
@Registration(
    id = CpuProfilerInstrument.ID,
    name = "Pkl CPU Profiler",
    services = CpuProfiler.class)
public final class CpuProfilerInstrument extends TruffleInstrument {

  public static final String ID = "pkl-cpu-profiler";

  private static final ProfilerToolFactory<CPUSampler> cpuSamplerFactory = getCpuSamplerFactory();

  private @Nullable CpuProfilerImpl profiler;

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
    var cpuSampler = cpuSamplerFactory.create(env);
    profiler = new CpuProfilerImpl(cpuSampler);
    env.registerService(new CpuProfilerImpl(cpuSampler));
  }

  @Override
  protected void onDispose(Env env) {
    if (profiler != null) {
      profiler.close();
    }
  }
}

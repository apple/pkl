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
package org.pkl.profiler.impl;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.tools.profiler.CPUSampler;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.pkl.core.ProfilerOptions.Cpu;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.profiler.CpuProfiler;

public class CpuProfilerImpl implements CpuProfiler {
  private final CPUSampler cpuSampler;
  private @Nullable LocalDateTime startTime;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean isFlushed = new AtomicBoolean(false);
  private @Nullable Cpu cpuOptions;

  public CpuProfilerImpl(CPUSampler cpuSampler) {
    this.cpuSampler = cpuSampler;
  }

  @Override
  public void start(Cpu cpuOptions) {
    this.cpuOptions = cpuOptions;
    if (!cpuOptions.isEnabled()) {
      return;
    }
    if (started.getAndSet(true)) {
      throw new IllegalStateException("CPUProfiler has already been started");
    }
    cpuSampler.setPeriod(cpuOptions.samplePeriod());
    cpuSampler.setFilter(SourceSectionFilter.newBuilder().mimeTypeIs(VmLanguage.MIME_TYPE).build());
    cpuSampler.setCollecting(true);
    startTime = LocalDateTime.now();
  }

  @Override
  public void stop() {
    if (cpuOptions == null || !cpuOptions.isEnabled()) return;
    if (isFlushed.getAndSet(true)) return;
    var outputFile = cpuOptions.outputFile();
    assert outputFile != null;
    assert startTime != null;
    // must read data before closing; `close()` clears it (see `CPUSampler#clearData()`).
    var data = new PklProfilerData(startTime, cpuSampler.getPeriod(), cpuSampler.getDataList());
    cpuSampler.close();
    new PProfOutput(data, outputFile).write();
  }
}

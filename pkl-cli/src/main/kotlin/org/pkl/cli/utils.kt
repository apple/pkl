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
package org.pkl.cli

import org.pkl.core.ProfilerOptions
import org.pkl.core.runtime.VmUtils
import org.pkl.profiler.CpuProfiler
import org.pkl.profiler.CpuProfilerInstrument

/**
 * Runs [body], sampling it with the CPU profiler if [options] enables CPU profiling.
 *
 * Profiler output is written when [body] finishes, whether normally or exceptionally.
 */
internal inline fun <T> withCpuProfiler(options: ProfilerOptions, body: () -> T): T {
  if (!options.cpu.isEnabled) return body()
  val profiler =
    VmUtils.getEngine().instruments[CpuProfilerInstrument.ID]?.lookup(CpuProfiler::class.java)!!
  profiler.start(options.cpu)
  try {
    return body()
  } finally {
    profiler.stop()
  }
}

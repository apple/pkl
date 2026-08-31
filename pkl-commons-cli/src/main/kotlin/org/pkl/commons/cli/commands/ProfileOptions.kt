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
package org.pkl.commons.cli.commands

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path
import org.pkl.core.ProfilerOptions

class ProfileOptions : OptionGroup() {
  val profileCpuOutput: Path? by
    option(
        names = arrayOf("--profile-cpu-output"),
        help =
          "The file to write CPU profiler output to. Omitting this option disables CPU profiling.",
      )
      .single()
      .path()

  val profileCpuSamplePeriod: Long by
    option(
        names = arrayOf("--profile-cpu-sample-period"),
        help = "Duration, in milliseconds, for the CPU profiler to poll for samples.",
      )
      .single()
      .long()
      .default(ProfilerOptions.DEFAULT.cpu.samplePeriod)

  fun toOptions(): ProfilerOptions =
    ProfilerOptions(ProfilerOptions.Cpu(profileCpuOutput, profileCpuSamplePeriod))
}

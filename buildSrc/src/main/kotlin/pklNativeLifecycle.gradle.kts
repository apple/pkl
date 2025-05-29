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
val assembleNativeMacOsAarch64 by tasks.registering { group = "build" }

val assembleNativeMacOsAmd64 by tasks.registering { group = "build" }

val assembleNativeLinuxAarch64 by tasks.registering { group = "build" }

val assembleNativeLinuxAmd64 by tasks.registering { group = "build" }

val assembleNativeAlpineLinuxAmd64 by tasks.registering { group = "build" }

val assembleNativeWindowsAmd64 by tasks.registering { group = "build" }

val testNativeMacOsAarch64 by tasks.registering { group = "verification" }

val testNativeMacOsAmd64 by tasks.registering { group = "verification" }

val testNativeLinuxAarch64 by tasks.registering { group = "verification" }

val testNativeLinuxAmd64 by tasks.registering { group = "verification" }

val testNativeAlpineLinuxAmd64 by tasks.registering { group = "verification" }

val testNativeWindowsAmd64 by tasks.registering { group = "verification" }

val buildInfo = project.extensions.getByType<BuildInfo>()

private fun <T : Task> Task.wraps(other: TaskProvider<T>) {
  dependsOn(other)
  outputs.files(other)
}

val assembleNative by
  tasks.registering {
    group = "build"

    if (!buildInfo.isCrossArchSupported && buildInfo.isCrossArch) {
      throw GradleException("Cross-arch builds are not supported on ${buildInfo.os.name}")
    }

    val underlyingTask =
      when (buildInfo.targetMachine) {
        Machine.MacosAarch64 -> assembleNativeMacOsAarch64
        Machine.MacosAmd64 -> assembleNativeMacOsAmd64
        Machine.LinuxAarch64 -> assembleNativeLinuxAarch64
        Machine.LinuxAmd64 -> assembleNativeLinuxAmd64
        Machine.AlpineLinuxAmd64 -> assembleNativeAlpineLinuxAmd64
        Machine.WindowsAmd64 -> assembleNativeWindowsAmd64
      }
    wraps(underlyingTask)
  }

val testNative by
  tasks.registering {
    group = "verification"

    @Suppress("DuplicatedCode")
    if (!buildInfo.isCrossArchSupported && buildInfo.isCrossArch) {
      throw GradleException("Cross-arch builds are not supported on ${buildInfo.os.name}")
    }

    val underlyingTask =
      when (buildInfo.targetMachine) {
        Machine.MacosAarch64 -> testNativeMacOsAarch64
        Machine.MacosAmd64 -> testNativeMacOsAmd64
        Machine.LinuxAarch64 -> testNativeLinuxAarch64
        Machine.LinuxAmd64 -> testNativeLinuxAmd64
        Machine.AlpineLinuxAmd64 -> testNativeAlpineLinuxAmd64
        Machine.WindowsAmd64 -> testNativeWindowsAmd64
      }
    wraps(underlyingTask)
  }

val checkNative by
  tasks.registering {
    group = "verification"
    dependsOn(testNative)
  }

val buildNative by
  tasks.registering {
    group = "build"
    dependsOn(assembleNative, checkNative)
  }

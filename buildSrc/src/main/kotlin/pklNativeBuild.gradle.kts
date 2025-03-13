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

    when {
      buildInfo.os.isMacOsX && buildInfo.targetArch == "aarch64" -> {
        wraps(assembleNativeMacOsAarch64)
      }
      buildInfo.os.isMacOsX && buildInfo.targetArch == "amd64" -> {
        wraps(assembleNativeMacOsAmd64)
      }
      buildInfo.os.isLinux && buildInfo.targetArch == "aarch64" -> {
        wraps(assembleNativeLinuxAarch64)
      }
      buildInfo.os.isLinux && buildInfo.targetArch == "amd64" -> {
        if (buildInfo.musl) wraps(assembleNativeAlpineLinuxAmd64)
        else wraps(assembleNativeLinuxAmd64)
      }
      buildInfo.os.isWindows && buildInfo.targetArch == "amd64" -> {
        wraps(assembleNativeWindowsAmd64)
      }
      buildInfo.musl -> {
        throw GradleException("Building musl on ${buildInfo.os} is not supported")
      }
      else -> {
        throw GradleException(
          "Unsupported os/arch pair: ${buildInfo.os.name}/${buildInfo.targetArch}"
        )
      }
    }
  }

val testNative by
  tasks.registering {
    group = "verification"

    if (!buildInfo.isCrossArchSupported && buildInfo.isCrossArch) {
      throw GradleException("Cross-arch builds are not supported on ${buildInfo.os.name}")
    }

    when {
      buildInfo.os.isMacOsX && buildInfo.targetArch == "aarch64" -> {
        dependsOn(testNativeMacOsAarch64)
      }
      buildInfo.os.isMacOsX && buildInfo.targetArch == "amd64" -> {
        dependsOn(testNativeMacOsAmd64)
      }
      buildInfo.os.isLinux && buildInfo.targetArch == "aarch64" -> {
        dependsOn(testNativeLinuxAarch64)
      }
      buildInfo.os.isLinux && buildInfo.targetArch == "amd64" -> {
        if (buildInfo.musl) dependsOn(testNativeAlpineLinuxAmd64)
        else dependsOn(testNativeLinuxAmd64)
      }
      buildInfo.os.isWindows && buildInfo.targetArch == "amd64" -> {
        dependsOn(testNativeWindowsAmd64)
      }
      buildInfo.musl -> {
        throw GradleException("Building musl on ${buildInfo.os} is not supported")
      }
      else -> {
        throw GradleException(
          "Unsupported os/arch pair: ${buildInfo.os.name}/${buildInfo.targetArch}"
        )
      }
    }
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

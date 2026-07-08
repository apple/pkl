/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
val assembleNativeMacOsAarch64 = tasks.register("assembleNativeMacOsAarch64") { group = "build" }

val assembleNativeMacOsAmd64 = tasks.register("assembleNativeMacOsAmd64") { group = "build" }

val assembleNativeLinuxAarch64 = tasks.register("assembleNativeLinuxAarch64") { group = "build" }

val assembleNativeLinuxAmd64 = tasks.register("assembleNativeLinuxAmd64") { group = "build" }

val assembleNativeAlpineLinuxAmd64 =
  tasks.register("assembleNativeAlpineLinuxAmd64") { group = "build" }

val assembleNativeWindowsAmd64 = tasks.register("assembleNativeWindowsAmd64") { group = "build" }

val testNativeMacOsAarch64 = tasks.register("testNativeMacOsAarch64") { group = "verification" }

val testNativeMacOsAmd64 = tasks.register("testNativeMacOsAmd64") { group = "verification" }

val testNativeLinuxAarch64 = tasks.register("testNativeLinuxAarch64") { group = "verification" }

val testNativeLinuxAmd64 = tasks.register("testNativeLinuxAmd64") { group = "verification" }

val testNativeAlpineLinuxAmd64 =
  tasks.register("testNativeAlpineLinuxAmd64") { group = "verification" }

val testNativeWindowsAmd64 = tasks.register("testNativeWindowsAmd64") { group = "verification" }

val buildInfo = project.extensions.getByType<BuildInfo>()

private fun <T : Task> Task.wraps(other: TaskProvider<T>) {
  dependsOn(other)
  outputs.files(other)
}

val assembleNative =
  tasks.register("assembleNative") {
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
      else -> {
        doLast {
          throw GradleException(
            "Cannot build targeting ${buildInfo.os.name}/${buildInfo.targetArch} with musl=${buildInfo.musl}"
          )
        }
      }
    }
  }

val testNative =
  tasks.register("testNative") {
    group = "verification"
    dependsOn(assembleNative)

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
      else -> {
        doLast {
          throw GradleException(
            "Cannot build targeting ${buildInfo.os.name}/${buildInfo.targetArch} with musl=${buildInfo.musl}"
          )
        }
      }
    }
  }

val checkNative =
  tasks.register("checkNative") {
    group = "verification"
    dependsOn(testNative)
  }

val buildNative =
  tasks.register("buildNative") {
    group = "build"
    dependsOn(checkNative)
  }

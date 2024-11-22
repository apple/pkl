/*
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
import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.Verify

plugins { id("de.undercouch.download") }

val buildInfo = project.extensions.getByType<BuildInfo>()

// tries to minimize chance of corruption by download-to-temp-file-and-move
val downloadGraalVmAarch64 by
  tasks.registering(Download::class) { configureDownloadGraalVm(buildInfo.graalVmAarch64) }

val downloadGraalVmAmd64 by
  tasks.registering(Download::class) { configureDownloadGraalVm(buildInfo.graalVmAmd64) }

fun Download.configureDownloadGraalVm(graalvm: BuildInfo.GraalVm) {
  onlyIf { !graalvm.installDir.exists() }
  doLast { println("Downloaded GraalVm to ${graalvm.downloadFile}") }

  src(graalvm.downloadUrl)
  dest(graalvm.downloadFile)
  overwrite(false)
  tempAndMove(true)
}

val verifyGraalVmAarch64 by
  tasks.registering(Verify::class) {
    configureVerifyGraalVm(buildInfo.graalVmAarch64)
    dependsOn(downloadGraalVmAarch64)
  }

val verifyGraalVmAmd64 by
  tasks.registering(Verify::class) {
    configureVerifyGraalVm(buildInfo.graalVmAmd64)
    dependsOn(downloadGraalVmAmd64)
  }

fun Verify.configureVerifyGraalVm(graalvm: BuildInfo.GraalVm) {
  onlyIf { !graalvm.installDir.exists() }

  src(graalvm.downloadFile)
  checksum(
    buildInfo.libs.findVersion("graalVmSha256-${graalvm.osName}-${graalvm.arch}").get().toString()
  )
  algorithm("SHA-256")
}

@Suppress("unused")
val installGraalVmAarch64 by
  tasks.registering(InstallGraalVm::class) {
    dependsOn(verifyGraalVmAarch64)
    graalVm = buildInfo.graalVmAarch64
  }

@Suppress("unused")
val installGraalVmAmd64 by
  tasks.registering(InstallGraalVm::class) {
    dependsOn(verifyGraalVmAmd64)
    graalVm = buildInfo.graalVmAmd64
  }

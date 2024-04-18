import java.nio.file.*
import java.util.UUID
import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.Verify

plugins {
  id("de.undercouch.download")
}

val buildInfo = project.extensions.getByType<BuildInfo>()

val BuildInfo.GraalVm.downloadFile get() = file(homeDir).resolve("${baseName}.tar.gz")

// tries to minimize chance of corruption by download-to-temp-file-and-move
val downloadGraalVmAarch64 by tasks.registering(Download::class) {
  configureDownloadGraalVm(buildInfo.graalVmAarch64)
}

val downloadGraalVmAmd64 by tasks.registering(Download::class) {
  configureDownloadGraalVm(buildInfo.graalVmAmd64)
}

fun Download.configureDownloadGraalVm(graalvm: BuildInfo.GraalVm) {
  onlyIf {
    !graalvm.installDir.exists()
  }
  doLast {
    println("Downloaded GraalVm to ${graalvm.downloadFile}")
  }

  src(graalvm.downloadUrl)
  dest(graalvm.downloadFile)
  overwrite(false)
  tempAndMove(true)
}

val verifyGraalVmAarch64 by tasks.registering(Verify::class) {
  configureVerifyGraalVm(buildInfo.graalVmAarch64)
  dependsOn(downloadGraalVmAarch64)
}

val verifyGraalVmAmd64 by tasks.registering(Verify::class) {
  configureVerifyGraalVm(buildInfo.graalVmAmd64)
  dependsOn(downloadGraalVmAmd64)
}

fun Verify.configureVerifyGraalVm(graalvm: BuildInfo.GraalVm) {
  onlyIf {
    !graalvm.installDir.exists()
  }

  src(graalvm.downloadFile)
  checksum(buildInfo.libs.findVersion("graalVmSha256-${graalvm.osName}-${graalvm.arch}").get().toString())
  algorithm("SHA-256")
}

// minimize chance of corruption by extract-to-random-dir-and-flip-symlink
val installGraalVmAarch64 by tasks.registering {
  dependsOn(verifyGraalVmAarch64)
  configureInstallGraalVm(buildInfo.graalVmAarch64)
}

// minimize chance of corruption by extract-to-random-dir-and-flip-symlink
val installGraalVmAmd64 by tasks.registering {
  dependsOn(verifyGraalVmAmd64)
  configureInstallGraalVm(buildInfo.graalVmAmd64)
}

fun Task.configureInstallGraalVm(graalVm: BuildInfo.GraalVm) {
  onlyIf {
    !graalVm.installDir.exists()
  }

  doLast {
    val distroDir = "${graalVm.homeDir}/${UUID.randomUUID()}"

    try {
      mkdir(distroDir)

      println("Extracting ${graalVm.downloadFile} into $distroDir")
      // faster and more reliable than Gradle's `copy { from tarTree() }`
      exec {
        workingDir = file(distroDir)
        executable = "tar"
        args("--strip-components=1", "-xzf", graalVm.downloadFile)
      }

      val distroBinDir = if (buildInfo.os.isMacOsX) "$distroDir/Contents/Home/bin" else "$distroDir/bin"

      println("Installing native-image into $distroDir")
      exec {
        executable = "$distroBinDir/gu"
        args("install", "--no-progress", "native-image")
      }

      println("Creating symlink ${graalVm.installDir} for $distroDir")
      val tempLink = Paths.get("${graalVm.homeDir}/${UUID.randomUUID()}")
      Files.createSymbolicLink(tempLink, Paths.get(distroDir))
      try {
        Files.move(tempLink, graalVm.installDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
      } catch (e: Exception) {
        try { delete(tempLink.toFile()) } catch (ignored: Exception) {}
        throw e
      }
    } catch (e: Exception) {
      try { delete(distroDir) } catch (ignored: Exception) {}
      throw e
    }
  }
}

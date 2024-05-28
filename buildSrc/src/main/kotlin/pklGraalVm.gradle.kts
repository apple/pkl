import java.nio.file.*
import java.util.UUID
import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.Verify
import kotlin.io.path.createDirectories

plugins {
  id("de.undercouch.download")
}

val buildInfo = project.extensions.getByType<BuildInfo>()

val BuildInfo.GraalVm.downloadFile get(): File {
  val extension = if (buildInfo.os.isWindows) "zip" else "tar.gz"
  return file(homeDir).resolve("${baseName}.$extension")
}

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
    val distroDir = Paths.get(graalVm.homeDir, UUID.randomUUID().toString())

    try {
      distroDir.createDirectories()
      println("Extracting ${graalVm.downloadFile} into $distroDir")
      // faster and more reliable than Gradle's `copy { from tarTree() }`
      exec {
        workingDir = file(distroDir)
        executable = "tar"
        args("--strip-components=1", "-xzf", graalVm.downloadFile)
      }

      val distroBinDir = if (buildInfo.os.isMacOsX) distroDir.resolve("Contents/Home/bin") else distroDir.resolve("bin")

      println("Installing native-image into $distroDir")
      exec {
        val executableName = if (buildInfo.os.isWindows) "gu.cmd" else "gu"
        executable = distroBinDir.resolve(executableName).toString()
        args("install", "--no-progress", "native-image")
      }

      println("Creating symlink ${graalVm.installDir} for $distroDir")
      val tempLink = Paths.get(graalVm.homeDir, UUID.randomUUID().toString())
      Files.createSymbolicLink(tempLink, distroDir)
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

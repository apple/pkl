import java.nio.file.*
import java.util.UUID
import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.Verify

plugins {
  id("de.undercouch.download")
}

val buildInfo = project.extensions.getByType<BuildInfo>()

val homeDir = buildInfo.graalVm.homeDir
val baseName = buildInfo.graalVm.baseName
val installDir = buildInfo.graalVm.installDir
val downloadUrl = buildInfo.graalVm.downloadUrl
val downloadFile = file(homeDir).resolve("$baseName.tar.gz")

// tries to minimize chance of corruption by download-to-temp-file-and-move
val downloadGraalVm by tasks.registering(Download::class) {
  onlyIf {
    !installDir.exists()
  }

  src(downloadUrl)
  dest(downloadFile)
  overwrite(false)
  tempAndMove(true)
}

val verifyGraalVm by tasks.registering(Verify::class) {
  onlyIf {
    !installDir.exists()
  }

  dependsOn(downloadGraalVm)
  src(downloadFile)
  checksum(buildInfo.libs.findVersion("graalVmSha256-${buildInfo.graalVm.osName}-${buildInfo.graalVm.arch}").get().toString())
  algorithm("SHA-256")
}

// minimize chances of corruption by extract-to-random-dir-and-flip-symlink
val installGraalVm by tasks.registering {
  dependsOn(verifyGraalVm)

  onlyIf {
    !installDir.exists()
  }

  doLast {
    val distroDir = "$homeDir/${UUID.randomUUID()}"

    try {
      mkdir(distroDir)

      println("Extracting $downloadFile into $distroDir")
      // faster and more reliable than Gradle's `copy { from tarTree() }`
      exec {
        workingDir = file(distroDir)
        executable = "tar"
        args("--strip-components=1", "-xzf", downloadFile)
      }

      val distroBinDir = if (buildInfo.os.isMacOsX) "$distroDir/Contents/Home/bin" else "$distroDir/bin"

      println("Installing native-image into $distroDir")
      exec {
        executable = "$distroBinDir/gu"
        args("install", "--no-progress", "native-image")
      }

      println("Creating symlink $installDir for $distroDir")
      val tempLink = Paths.get("$homeDir/${UUID.randomUUID()}")
      Files.createSymbolicLink(tempLink, Paths.get(distroDir))
      try {
        Files.move(tempLink, installDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
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

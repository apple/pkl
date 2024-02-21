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

  // set checksum
  val checksumKey = "graalVmSha256-${buildInfo.graalVm.osName}-${buildInfo.graalVm.arch}"
  (buildInfo.libs.findVersion(checksumKey)
    .orElse(null) ?: error("Failed to locate GraalVM at key: `$checksumKey`"))
    .toString().let { checksum(it) }

  algorithm("SHA-256")
}

// minimize chances of corruption by extract-to-random-dir-and-flip-symlink
val installGraalVm by tasks.registering {
  dependsOn(verifyGraalVm)
  outputs.cacheIf { installDir.exists() }
  onlyIf { !installDir.exists() }

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

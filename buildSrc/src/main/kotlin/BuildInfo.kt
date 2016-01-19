@file:Suppress("MemberVisibilityCanBePrivate")

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

// `buildInfo` in main build scripts
// `project.extensions.getByType<BuildInfo>()` in precompiled script plugins
open class BuildInfo(project: Project) {
  val self = this

  inner class GraalVm {
    val homeDir: String by lazy {
      System.getenv("GRAALVM_HOME") ?: "${System.getProperty("user.home")}/.graalvm"
    }

    val arch by lazy {
      // TODO: we can remove this once we upgrade to GraalVM 22.2
      if (os.isMacOsX) "amd64" else self.arch
    }

    val osName: String by lazy {
      // graalvm uses "darwin" and "linux" to identify macOS/linux in their release binaries.
      // Gradle's [OperatingSystem] class doesn't have an accessor to return exactly these two
      // values.
      when {
        os.isMacOsX -> "darwin"
        os.isLinux -> "linux"
        else -> throw RuntimeException("${os.familyName} is not supported.")
      }
    }

    val baseName: String by lazy {
      "graalvm-ce-java11-${osName}-${arch}-${libs.findVersion("graalVm").get()}"
    }

    val installDir: File by lazy {
      File(homeDir, baseName)
    }

    val baseDir: String by lazy {
      if (os.isMacOsX) "$installDir/Contents/Home" else installDir.toString()
    }
  }

  /**
   * Same logic as [org.gradle.internal.os.OperatingSystem#arch], which is protected.
   */
  val arch: String by lazy {
    when (val arch = System.getProperty("os.arch")) {
      "x86" -> "i386"
      "x86_64" -> "amd64"
      "powerpc" -> "ppc"
      else -> arch
    }
  }

  val graalVm: GraalVm = GraalVm()

  val isCiBuild: Boolean by lazy {
    System.getenv("CI") != null
  }

  val isReleaseBuild: Boolean by lazy {
    java.lang.Boolean.getBoolean("releaseBuild")
  }

  val os: org.gradle.internal.os.OperatingSystem by lazy {
    org.gradle.internal.os.OperatingSystem.current()
  }

  // could be `commitId: Provider<String> = project.provider { ... }`
  val commitId: String by lazy {
    // only run command once per build invocation
    if (project === project.rootProject) {
      Runtime.getRuntime()
        .exec("git rev-parse --short HEAD", arrayOf(), project.rootDir)
        .inputStream.reader().readText().trim()
    } else {
      project.rootProject.extensions.getByType(BuildInfo::class.java).commitId
    }
  }

  val commitish: String by lazy {
    if (isReleaseBuild) project.version.toString() else commitId
  }

  val pklVersion: String by lazy {
    if (isReleaseBuild) {
      project.version.toString()
    } else {
      project.version.toString().replace("-SNAPSHOT", "-dev+$commitId")
    }
  }

  val pklVersionNonUnique: String by lazy {
    if (isReleaseBuild) {
      project.version.toString()
    } else {
      project.version.toString().replace("-SNAPSHOT", "-dev")
    }
  }

  // https://melix.github.io/blog/2021/03/version-catalogs-faq.html#_but_how_can_i_use_the_catalog_in_em_plugins_em_defined_in_code_buildsrc_code
  @Suppress("UnstableApiUsage")
  val libs: VersionCatalog by lazy {
    project.extensions.getByType<VersionCatalogsExtension>().named("libs")
  }

  init {
    if (!isReleaseBuild) {
      project.version = "${project.version}-SNAPSHOT"
    }
  }
}

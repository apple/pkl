@file:Suppress("MemberVisibilityCanBePrivate")

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

// `buildInfo` in main build scripts
// `project.extensions.getByType<BuildInfo>()` in precompiled script plugins
open class BuildInfo(project: Project) {
  inner class GraalVm(val arch: String) {
    val homeDir: String by lazy {
      System.getenv("GRAALVM_HOME") ?: "${System.getProperty("user.home")}/.graalvm"
    }

    val version: String by lazy {
      libs.findVersion("graalVm").get().toString()
    }

    val graalVmJdkVersion: String by lazy {
      libs.findVersion("graalVmJdkVersion").get().toString()
    }

    val osName: String by lazy {
      when {
        os.isMacOsX -> "macos"
        os.isLinux -> "linux"
        else -> throw RuntimeException("${os.familyName} is not supported.")
      }
    }

    val baseName: String by lazy {
      "graalvm-jdk-${graalVmJdkVersion}_${osName}-${arch}_bin"
    }

    val downloadUrl: String by lazy {
      val jdkMajor = graalVmJdkVersion.takeWhile { it != '.' }
      "https://download.oracle.com/graalvm/$jdkMajor/archive/$baseName.tar.gz"
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

  val graalVmAarch64: GraalVm = GraalVm("aarch64")

  val graalVmAmd64: GraalVm = GraalVm("x64")

  val isCiBuild: Boolean by lazy {
    System.getenv("CI") != null
  }

  val isReleaseBuild: Boolean by lazy {
    java.lang.Boolean.getBoolean("releaseBuild")
  }

  val hasMuslToolchain: Boolean by lazy {
    // see "install musl" in .circleci/jobs/BuildNativeJob.pkl
    File(System.getProperty("user.home"), "staticdeps/bin/x86_64-linux-musl-gcc").exists()
  }

  val os: org.gradle.internal.os.OperatingSystem by lazy {
    org.gradle.internal.os.OperatingSystem.current()
  }

  // could be `commitId: Provider<String> = project.provider { ... }`
  val commitId: String by lazy {
    // only run command once per build invocation
    if (project === project.rootProject) {
      Runtime.getRuntime()
        .exec(arrayOf("git", "rev-parse", "--short", "HEAD"), arrayOf(), project.rootDir)
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
  val libs: VersionCatalog by lazy {
    project.extensions.getByType<VersionCatalogsExtension>().named("libs")
  }

  init {
    if (!isReleaseBuild) {
      project.version = "${project.version}-SNAPSHOT"
    }
  }
}

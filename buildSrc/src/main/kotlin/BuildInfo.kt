@file:Suppress("MemberVisibilityCanBePrivate")

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.kotlin.dsl.getByType

// `buildInfo` in main build scripts
// `project.extensions.getByType<BuildInfo>()` in precompiled script plugins
open class BuildInfo(project: Project) {
  val self = this

  inner class GraalVm {
    val homeDir: String by lazy {
      System.getenv("GRAALVM_HOME") ?: "${System.getProperty("user.home")}/.graalvm"
    }

    val version: String by lazy {
      libs.findVersion("graalVm").get().toString()
    }

    val isGraal22: Boolean by lazy {
      version.startsWith("22")
    }

    val arch by lazy {
      if (os.isMacOsX && isGraal22) {
        "amd64"
      } else {
        self.arch
      }
    }

    val osName: String by lazy {
      when {
        os.isMacOsX && isGraal22 -> "darwin"
        os.isMacOsX -> "macos"
        os.isLinux -> "linux"
        else -> throw RuntimeException("${os.familyName} is not supported.")
      }
    }

    val baseName: String by lazy {
      if (graalVm.isGraal22) {
        "graalvm-ce-java11-${osName}-${arch}-${version}"
      } else {
        "graalvm-jdk-${graalVM23JdkVersion}_${osName}-${arch}_bin"
      }
    }

    val graalVM23JdkVersion: String by lazy {
      libs.findVersion("graalVM23JdkVersion").get().requiredVersion
    }

    val downloadUrl: String by lazy {
      if (isGraal22) {
        "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-" +
          "${version}/$baseName.tar.gz"
      } else {
        val jdkMajor = graalVM23JdkVersion.takeWhile { it != '.' }
        "https://download.oracle.com/graalvm/$jdkMajor/archive/$baseName.tar.gz"
      }
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

  val hasMuslToolchain: Boolean by lazy {
    // see "install musl" in .circleci/jobs/BuildNativeJob.pkl
    File(System.getProperty("user.home"), "staticdeps/bin/musl-gcc").exists()
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
  val libs: VersionCatalog by lazy {
    project.extensions.getByType<VersionCatalogsExtension>().named("libs")
  }

  init {
    if (!isReleaseBuild) {
      project.version = "${project.version}-SNAPSHOT"
    }
  }
}

@file:Suppress("MemberVisibilityCanBePrivate")

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.getByType

// Build properties that control reporting and analysis.
private const val autofixProperty = "autofix"
private const val enableAnalysisProperty = "enableAnalysis"
private const val xmlReportingBuildProperty = "xmlReporting"
private const val sarifReportingBuildProperty = "sarifReporting"
private const val htmlReportingBuildProperty = "htmlReporting"

// JVM target, vendor, and bytecode defaults and properties.
private const val defaultJvmTarget = "11"
private const val defaultJvmToolchain = "21"
private const val defaultJvmEntrypointTarget = "21"
private const val defaultKotlinTarget = "1.9"
private const val javaTargetProperty = "javaTarget"
private const val javaEntrypointProperty = "javaEntrypointTarget"
private const val javaToolchainProperty = "javaToolchainTarget"
private const val kotlinVersionProperty = "kotlinTarget"
private val jvmVendor = JvmVendorSpec.ADOPTIUM

// `buildInfo` in main build scripts
// `project.extensions.getByType<BuildInfo>()` in precompiled script plugins
open class BuildInfo(private val project: Project) {
  val self = this

  interface JvmTargetInfo {
    val target: Int
  }

  interface JvmToolchain : JvmTargetInfo {
    val vendor: JvmVendorSpec
  }

  interface KotlinSettings : JvmTargetInfo {
    val kotlinTarget: String
  }

  interface EntrypointSettings : JvmTargetInfo, KotlinSettings

  interface JvmSettings {
    val lib: JvmTargetInfo
    val kotlin: KotlinSettings
    val entrypoint: EntrypointSettings
    val toolchain: JvmToolchain
  }

  inner class Jvm : JvmSettings {
    // JVM toolchain defaults, properties, and resolved configuration.
    private val targetVersion =
      (project.findProperty(javaTargetProperty) as? String ?: defaultJvmTarget).toInt()
    private val toolchainTarget =
      (project.findProperty(javaToolchainProperty) as? String ?: defaultJvmToolchain).toInt()
    private val entrypointVersion =
      (project.findProperty(javaEntrypointProperty) as? String ?: defaultJvmEntrypointTarget).toInt()
    private val kotlinVersion =
      (project.findProperty(kotlinVersionProperty) as? String ?: defaultKotlinTarget)

    override val kotlin: KotlinSettings get() = object : KotlinSettings {
      override val target: Int get() = targetVersion
      override val kotlinTarget: String get() = kotlinVersion
    }

    override val lib: JvmTargetInfo get() = object : JvmTargetInfo {
      override val target: Int get() = targetVersion
    }

    override val entrypoint: EntrypointSettings get() = object : EntrypointSettings {
      override val target: Int get() = entrypointVersion
      override val kotlinTarget: String get() = kotlinVersion
    }

    override val toolchain: JvmToolchain get() = object : JvmToolchain {
      override val target: Int get() = toolchainTarget
      override val vendor: JvmVendorSpec get() = jvmVendor
    }
  }

  inner class Analysis {
    val enabled =
      (project.findProperty(enableAnalysisProperty) == "true" ||
        project.gradle.taskGraph.hasTask("check") ||
        project.gradle.taskGraph.hasTask("detekt"))

    val autofix = (project.findProperty(autofixProperty) == "true")

    // Resolved build properties for reporting within this project.
    val xmlReporting: Boolean get() =
      project.findProperty(xmlReportingBuildProperty) == "true"

    val sarifReporting = project.findProperty(sarifReportingBuildProperty) == "true"
    val htmlReporting = project.findProperty(htmlReportingBuildProperty) == "true"

    // Whether to enable PMD.
    val enablePmd = enabled && (project.findProperty("enablePmd") == "true")
  }

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

    val isGraal23: Boolean by lazy {
      version.startsWith("23")
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
        val archFixed = if (isGraal23 && arch == "amd64") "x64" else arch
        "graalvm-jdk-${graalVM23JdkVersion}_${osName}-${archFixed}_bin"
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

  val jvm: Jvm = Jvm()

  val analysis: Analysis = Analysis()

  val isCiBuild: Boolean by lazy {
    System.getenv("CI") != null
  }

  val isPublishing: Boolean by lazy {
    project.gradle.startParameter.taskNames.any { "publish" in it.lowercase() }
  }

  val isTesting: Boolean by lazy {
    project.gradle.startParameter.taskNames.any {
      it.lowercase().let { lower ->
        "test" in lower || "check" in lower
      }
    }
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
      ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(project.rootDir)
        .start()
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

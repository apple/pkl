/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
@file:Suppress("MemberVisibilityCanBePrivate")

import java.io.File
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.attributes.Category
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.jvm.toolchain.*
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.testing.base.TestingExtension

/**
 * JVM bytecode target; this is pinned at a reasonable version, because downstream JVM projects
 * which consume Pkl will need a minimum Bytecode level at or above this one.
 *
 * Kotlin and Java need matching bytecode targets, so this is expressed as a build setting and
 * constant default. To override, pass `-DpklJdkToolchain=X` to the Gradle command line, where X is
 * a major Java version.
 */
const val PKL_JVM_TARGET_DEFAULT_MAXIMUM = 17

/**
 * The Pkl build requires JDK 21+ to build, because JDK 17 is no longer within the default set of
 * supported JDKs for GraalVM. This is a build-time requirement, not a runtime requirement.
 */
const val PKL_JDK_VERSION_MIN = 21

/**
 * The JDK minimum is set to match the bytecode minimum, to guarantee that fat JARs work against the
 * earliest supported bytecode target.
 */
const val PKL_TEST_JDK_MINIMUM = PKL_JVM_TARGET_DEFAULT_MAXIMUM

/**
 * Maximum JDK version which Pkl is tested with; this should be bumped when new JDK stable releases
 * are issued. At the time of this writing, JDK 23 is the latest available release.
 */
const val PKL_TEST_JDK_MAXIMUM = 23

/**
 * Test the full suite of JDKs between [PKL_TEST_JDK_MINIMUM] and [PKL_TEST_JDK_MAXIMUM]; if this is
 * set to `false` (or overridden on the command line), only LTS releases are tested by default.
 */
const val PKL_TEST_ALL_JDKS = false

// `buildInfo` in main build scripts
// `project.extensions.getByType<BuildInfo>()` in precompiled script plugins
open class BuildInfo(private val project: Project) {
  inner class GraalVm(val arch: String) {
    val homeDir: String by lazy {
      System.getenv("GRAALVM_HOME") ?: "${System.getProperty("user.home")}/.graalvm"
    }

    val version: String by lazy { libs.findVersion("graalVm").get().toString() }

    val graalVmJdkVersion: String by lazy { libs.findVersion("graalVmJdkVersion").get().toString() }

    val osName: String by lazy {
      when {
        os.isMacOsX -> "macos"
        os.isLinux -> "linux"
        os.isWindows -> "windows"
        else -> throw RuntimeException("${os.familyName} is not supported.")
      }
    }

    val baseName: String by lazy { "graalvm-jdk-${graalVmJdkVersion}_${osName}-${arch}_bin" }

    val downloadUrl: String by lazy {
      val jdkMajor = graalVmJdkVersion.takeWhile { it != '.' }
      val extension = if (os.isWindows) "zip" else "tar.gz"
      "https://download.oracle.com/graalvm/$jdkMajor/archive/$baseName.$extension"
    }

    val downloadFile: File by lazy {
      val extension = if (os.isWindows) "zip" else "tar.gz"
      File(homeDir, "${baseName}.$extension")
    }

    val installDir: File by lazy { File(homeDir, baseName) }

    val baseDir: String by lazy {
      if (os.isMacOsX) "$installDir/Contents/Home" else installDir.toString()
    }
  }

  /** Same logic as [org.gradle.internal.os.OperatingSystem#arch], which is protected. */
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

  val isCiBuild: Boolean by lazy { System.getenv("CI") != null }

  val isReleaseBuild: Boolean by lazy { java.lang.Boolean.getBoolean("releaseBuild") }

  val isNativeArch: Boolean by lazy { java.lang.Boolean.getBoolean("nativeArch") }

  val jvmTarget: Int by lazy {
    System.getProperty("pklJvmTarget")?.toInt() ?: PKL_JVM_TARGET_DEFAULT_MAXIMUM
  }

  // JPMS exports for Truffle; needed on some versions of Java, and transitively within some JARs.
  private val jpmsExports =
    arrayOf(
      "org.graalvm.truffle/com.oracle.truffle.api.exception=ALL-UNNAMED",
      "org.graalvm.truffle/com.oracle.truffle.api=ALL-UNNAMED",
      "org.graalvm.truffle/com.oracle.truffle.api.nodes=ALL-UNNAMED",
      "org.graalvm.truffle/com.oracle.truffle.api.source=ALL-UNNAMED",
    )

  // Extra JPMS modules forced onto the module path via `--add-modules` in some cases.
  private val jpmsAddModules = arrayOf("jdk.unsupported")

  // Formats `jpmsExports` for use in JAR manifest attributes.
  val jpmsExportsForJarManifest: String by lazy {
    jpmsExports.joinToString(" ") { it.substringBefore("=") }
  }

  // Formats `jpmsExports` for use on the command line with `--add-exports`.
  val jpmsExportsForAddExportsFlags: Collection<String> by lazy {
    jpmsExports.map { "--add-exports=$it" }
  }

  // Formats `jpmsAddModules` for use on the command line with `--add-modules`.
  val jpmsAddModulesFlags: Collection<String> by lazy { jpmsAddModules.map { "--add-modules=$it" } }

  // JVM properties to set during testing.
  val testProperties =
    mapOf<String, Any>(
      // @TODO: this should be removed once pkl supports JPMS as a true Java Module.
      "polyglotimpl.DisableClassPathIsolation" to true
    )

  val jdkVendor: JvmVendorSpec = JvmVendorSpec.ADOPTIUM

  val jdkToolchainVersion: JavaLanguageVersion by lazy {
    JavaLanguageVersion.of(System.getProperty("pklJdkToolchain")?.toInt() ?: PKL_JDK_VERSION_MIN)
  }

  val jdkTestFloor: JavaLanguageVersion by lazy { JavaLanguageVersion.of(PKL_TEST_JDK_MINIMUM) }

  val jdkTestCeiling: JavaLanguageVersion by lazy { JavaLanguageVersion.of(PKL_TEST_JDK_MAXIMUM) }

  val testAllJdks: Boolean by lazy {
    // By default, Pkl is tested against LTS JDK releases within the bounds of `PKL_TEST_JDK_TARGET`
    // and `PKL_TEST_JDK_MAXIMUM`. To test against the full suite of JDK versions, past and present,
    // set `-DpklTestAllJdks=true` on the Gradle command line. This results in non-LTS releases, old
    // releases, and "experimental releases" (newer than the toolchain version) being included in
    // the default `check` suite.
    System.getProperty("pklTestAllJdks")?.toBoolean() ?: PKL_TEST_ALL_JDKS
  }

  val testExperimentalJdks: Boolean by lazy {
    System.getProperty("pklTestFutureJdks")?.toBoolean() ?: false
  }

  val testJdkVendors: Sequence<JvmVendorSpec> by lazy {
    // By default, only OpenJDK is tested during multi-JDK testing. Flip `-DpklTestAllVendors=true`
    // to additionally test against a suite of JDK vendors, including Azul, Oracle, and GraalVM.
    when (System.getProperty("pklTestAllVendors")?.toBoolean()) {
      true -> sequenceOf(JvmVendorSpec.ADOPTIUM, JvmVendorSpec.GRAAL_VM, JvmVendorSpec.ORACLE)
      else -> sequenceOf(JvmVendorSpec.ADOPTIUM)
    }
  }

  // Assembles a collection of JDK versions which tests can be run against, considering ancillary
  // parameters like `testAllJdks` and `testExperimentalJdks`.
  val jdkTestRange: Collection<JavaLanguageVersion> by lazy {
    JavaVersionRange.inclusive(jdkTestFloor, jdkTestCeiling).filter { version ->
      // unless we are instructed to test all JDKs, tests only include LTS releases and
      // versions above the toolchain version.
      testAllJdks || (JavaVersionRange.isLTS(version) || version >= jdkToolchainVersion)
    }
  }

  private fun JavaToolchainSpec.pklJdkToolchain() {
    languageVersion.set(jdkToolchainVersion)
    vendor.set(jdkVendor)
  }

  private fun labelForVendor(vendor: JvmVendorSpec): String =
    when (vendor) {
      JvmVendorSpec.AZUL -> "Zulu"
      JvmVendorSpec.GRAAL_VM -> "GraalVm"
      JvmVendorSpec.ORACLE -> "Oracle"
      JvmVendorSpec.ADOPTIUM -> "Adoptium"
      else -> error("Unrecognized JDK vendor: $vendor")
    }

  private fun testNamer(baseName: () -> String): (JavaLanguageVersion, JvmVendorSpec?) -> String =
    { jdkTarget, vendor ->
      val targetToken =
        when (vendor) {
          null -> "Jdk${jdkTarget.asInt()}"
          else -> "Jdk${jdkTarget.asInt()}${labelForVendor(vendor).capitalized()}"
        }
      if (jdkTarget > jdkToolchainVersion) {
        // test targets above the toolchain target are considered "experimental".
        "${baseName()}${targetToken}Experimental"
      } else {
        "${baseName()}${targetToken}"
      }
    }

  @Suppress("UnstableApiUsage")
  fun multiJdkTestingWith(
    templateTask: TaskProvider<out Test>,
    configurator: MultiJdkTestConfigurator = {},
  ): Iterable<Provider<out Any>> =
    with(project) {
      // force the `jvm-test-suite` plugin to apply first
      project.pluginManager.apply(JvmTestSuitePlugin::class.java)

      val isMultiVendor = testJdkVendors.count() > 1
      val baseNameProvider = { templateTask.get().name }
      val namer = testNamer(baseNameProvider)
      val applyConfig: MultiJdkTestConfigurator = { (version, jdk) ->
        // 1) copy configurations from the template task
        dependsOn(templateTask)
        templateTask.get().let { template ->
          classpath = template.classpath
          testClassesDirs = template.testClassesDirs
          jvmArgs.addAll(template.jvmArgs)
          jvmArgumentProviders.addAll(template.jvmArgumentProviders)
          forkEvery = template.forkEvery
          maxParallelForks = template.maxParallelForks
          minHeapSize = template.minHeapSize
          maxHeapSize = template.maxHeapSize
          exclude(template.excludes)
          template.systemProperties.forEach { prop -> systemProperty(prop.key, prop.value) }
        }

        // 2) assign launcher
        javaLauncher = jdk

        // 3) dispatch the user's configurator
        configurator(version to jdk)
      }

      serviceOf<JavaToolchainService>().let { toolchains ->
        jdkTestRange
          .flatMap { targetVersion ->
            // multiply out by jdk vendor
            testJdkVendors.map { vendor -> (targetVersion to vendor) }
          }
          .filter { (jdkTarget, vendor) ->
            // only include experimental tasks in the return suite if the flag is set. if the task
            // is withheld from the returned list, it will not be executed by default with `gradle
            // check`.
            testExperimentalJdks ||
              (!namer(jdkTarget, vendor.takeIf { isMultiVendor }).contains("Experimental"))
          }
          .map { (jdkTarget, vendor) ->
            if (jdkToolchainVersion == jdkTarget)
              tasks.register(namer(jdkTarget, vendor)) {
                // alias to `test`
                dependsOn(templateTask)
                group = Category.VERIFICATION
                description =
                  "Alias for regular '${baseNameProvider()}' task, on JDK ${jdkTarget.asInt()}"
              }
            else
              the<TestingExtension>().suites.register(
                namer(jdkTarget, vendor.takeIf { isMultiVendor }),
                JvmTestSuite::class,
              ) {
                targets.all {
                  testTask.configure {
                    group = Category.VERIFICATION
                    description = "Run tests against JDK ${jdkTarget.asInt()}"
                    applyConfig(jdkTarget to toolchains.launcherFor { languageVersion = jdkTarget })

                    // fix: on jdk17, we must force the polyglot module on to the modulepath
                    if (jdkTarget.asInt() == 17)
                      jvmArgumentProviders.add(
                        CommandLineArgumentProvider {
                          buildList { listOf("--add-modules=org.graalvm.polyglot") }
                        }
                      )
                  }
                }
              }
          }
          .toList()
      }
    }

  val javaCompiler: Provider<JavaCompiler> by lazy {
    project.serviceOf<JavaToolchainService>().let { toolchainService ->
      toolchainService.compilerFor { pklJdkToolchain() }
    }
  }

  val javaTestLauncher: Provider<JavaLauncher> by lazy {
    project.serviceOf<JavaToolchainService>().let { toolchainService ->
      toolchainService.launcherFor { pklJdkToolchain() }
    }
  }

  val multiJdkTesting: Boolean by lazy {
    // By default, Pkl is tested against a full range of JDK versions, past and present, within the
    // supported bounds of `PKL_TEST_JDK_TARGET` and `PKL_TEST_JDK_MAXIMUM`. To opt-out of this
    // behavior, set `-DpklMultiJdkTesting=false` on the Gradle command line.
    //
    // In CI, this defaults to `true` to catch potential cross-JDK compat regressions or other bugs.
    // In local dev, this defaults to `false` to speed up the build and reduce contributor load.
    System.getProperty("pklMultiJdkTesting")?.toBoolean() ?: isCiBuild
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
    // allow -DcommitId=abc123 for build environments that don't have git.
    System.getProperty("commitId").let { if (it != null) return@lazy it }
    // only run command once per build invocation
    if (project === project.rootProject) {
      val process =
        ProcessBuilder()
          .command("git", "rev-parse", "--short", "HEAD")
          .directory(project.rootDir)
          .start()
      process.waitFor().also { exitCode ->
        if (exitCode == -1) throw RuntimeException(process.errorStream.reader().readText())
      }
      process.inputStream.reader().readText().trim()
    } else {
      project.rootProject.extensions.getByType(BuildInfo::class.java).commitId
    }
  }

  val commitish: String by lazy { if (isReleaseBuild) project.version.toString() else commitId }

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

// Shape of a function which is applied to configure multi-JDK testing.
private typealias MultiJdkTestConfigurator =
  Test.(Pair<JavaLanguageVersion, Provider<JavaLauncher>>) -> Unit

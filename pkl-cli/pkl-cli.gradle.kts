@file:Suppress("UnstableApiUsage")

import org.gradle.crypto.checksum.Checksum

plugins {
  pklAllProjects
  pklKotlinLibrary
  pklPublishLibrary
  pklNativeBuild
  `maven-publish`
  signing

  // already on build script class path (see buildSrc/build.gradle.kts),
  // hence must only specify plugin ID here
  @Suppress("DSL_SCOPE_VIOLATION")
  id(libs.plugins.shadow.get().pluginId)

  @Suppress("DSL_SCOPE_VIOLATION")
  alias(libs.plugins.checksum)
}

// make Java executable available to other subprojects
val javaExecutableConfiguration: Configuration = configurations.create("javaExecutable")

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/pkl-lang/pkl/tree/main/pkl-cli")
        description.set("Pkl CLI Java library.")
      }
    }
  }
}

val stagedMacAmd64Executable: Configuration by configurations.creating
val stagedLinuxAmd64Executable: Configuration by configurations.creating
val stagedLinuxAarch64Executable: Configuration by configurations.creating
val stagedAlpineLinuxAmd64Executable: Configuration by configurations.creating

dependencies {
  compileOnly(libs.svm)

  // CliEvaluator exposes PClass
  api(project(":pkl-core"))
  // CliEvaluatorOptions exposes CliBaseOptions
  api(project(":pkl-commons-cli"))

  implementation(project(":pkl-commons"))
  implementation(libs.jansi)
  implementation(libs.jlineReader)
  implementation(libs.jlineTerminal)
  implementation(libs.jlineTerminalJansi)
  implementation(project(":pkl-server"))
  implementation(libs.clikt) {
    // force clikt to use our version of the kotlin stdlib
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
  }

  testImplementation(project(":pkl-commons-test"))

  stagedMacAmd64Executable(files("$buildDir/executable/pkl-macos-amd64"))
  stagedLinuxAmd64Executable(files("$buildDir/executable/pkl-linux-amd64"))
  stagedLinuxAarch64Executable(files("$buildDir/executable/pkl-linux-aarch64"))
  stagedAlpineLinuxAmd64Executable(files("$buildDir/executable/pkl-alpine-linux-amd64"))
}

tasks.jar {
  manifest {
    attributes += mapOf("Main-Class" to "org.pkl.cli.Main")
  }

  // not required at runtime
  exclude("org/pkl/cli/svm/**")
}

tasks.javadoc {
  enabled = false
}

tasks.shadowJar {
  archiveFileName.set("jpkl")

  exclude("META-INF/maven/**")
  exclude("META-INF/upgrade/**")

  // org.antlr.v4.runtime.misc.RuleDependencyProcessor
  exclude("META-INF/services/javax.annotation.processing.Processor")

  exclude("module-info.*")
}

val javaExecutable by tasks.registering(ExecutableJar::class) {
  inJar.set(tasks.shadowJar.flatMap { it.archiveFile })
  outJar.set(file("$buildDir/executable/jpkl"))

  // uncomment for debugging
  //jvmArgs.addAll("-ea", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
}

val testJavaExecutable by tasks.registering(Test::class) {
  testClassesDirs = tasks.test.get().testClassesDirs
  classpath =
      // compiled test classes
      sourceSets.test.get().output +
      // java executable
      javaExecutable.get().outputs.files +
      // test-only dependencies
      // (test dependencies that are also main dependencies must already be contained in java executable;
      // to verify that, we don't want to include them here)
      (configurations.testRuntimeClasspath.get() - configurations.runtimeClasspath.get())
}

tasks.check {
  dependsOn(testJavaExecutable)
}

// 0.14 Java executable was broken because javaExecutable.jvmArgs wasn't commented out.
// To catch this and similar problems, test that Java executable starts successfully.
val testStartJavaExecutable by tasks.registering(Exec::class) {
  dependsOn(javaExecutable)
val outputFile = file("$buildDir/testStartJavaExecutable") // dummy output to satisfy up-to-date check
  outputs.file(outputFile)
  
  executable = javaExecutable.get().outputs.files.singleFile.toString()
  args("--version")
  
  doFirst { outputFile.delete() }
  
  doLast { outputFile.writeText("OK") }
}

tasks.check {
  dependsOn(testStartJavaExecutable)
}

fun Exec.configureExecutable(isEnabled: Boolean, outputFile: File, extraArgs: List<String> = listOf()) {
  enabled = isEnabled
  dependsOn(":installGraalVm")

  inputs.files(sourceSets.main.map { it.output })
  inputs.files(configurations.runtimeClasspath)
  outputs.file(outputFile)

  workingDir = outputFile.parentFile
  executable = "${buildInfo.graalVm.baseDir}/bin/native-image"

  // https://www.graalvm.org/22.0/reference-manual/native-image/Options/
  argumentProviders.add(CommandLineArgumentProvider {
    listOf(
        // currently gives a deprecation warning, but we've been told 
        // that the "initialize everything at build time" *CLI* option is likely here to stay
        "--initialize-at-build-time="
        ,"--no-fallback"
        ,"-H:IncludeResources=org/pkl/core/stdlib/.*\\.pkl"
        ,"-H:IncludeResources=org/jline/utils/.*"
        ,"-H:IncludeResources=org/pkl/commons/cli/IncludedCARoots.pem"
        //,"-H:IncludeResources=org/pkl/core/Release.properties"
        ,"-H:IncludeResourceBundles=org.pkl.core.errorMessages"
        ,"--macro:truffle"
        ,"-H:Class=org.pkl.cli.Main"
        ,"-H:Name=${outputFile.name}"
        //,"--native-image-info"
        //,"-Dpolyglot.image-build-time.PreinitializeContexts=pkl"
        // the actual limit (currently) used by native-image is this number + 1400 (idea is to compensate for Truffle's own nodes)
        ,"-H:MaxRuntimeCompileMethods=1800"
        ,"-H:+EnforceMaxRuntimeCompileMethods"
        ,"--enable-url-protocols=http,https"
        //,"--install-exit-handlers"
        ,"-H:+ReportExceptionStackTraces"
        ,"-H:-ParseRuntimeOptions" // disable automatic support for JVM CLI options (puts our main class in full control of argument parsing)
        //,"-H:+PrintAnalysisCallTree"
        //,"-H:PrintAnalysisCallTreeType=CSV"
        //,"-H:+PrintImageObjectTree"
        //,"--features=org.pkl.cli.svm.InitFeature"
        //,"-H:Dump=:2"
        //,"-H:MethodFilter=ModuleCache.getOrLoad*,VmLanguage.loadModule"
        //,"-g"
        //,"-verbose"
        //,"--debug-attach"
        //,"-H:+AllowVMInspection"
        //,"-H:+PrintHeapHistogram"
        //,"-H:+ReportDeletedElementsAtRuntime"
        //,"-H:+PrintMethodHistogram"
        //,"-H:+PrintRuntimeCompileMethods"
        //,"-H:NumberOfThreads=1"
        //,"-J-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime"
        //,"-J-Dcom.oracle.truffle.aot=true"
        //,"-J:-ea"
        //,"-J:-esa"
        // for use with https://www.graalvm.org/docs/tools/dashboard/
        //,"-H:DashboardDump=dashboard.dump", "-H:+DashboardAll"
        // native-image rejects non-existing class path entries -> filter
        ,"--class-path", ((sourceSets.main.get().output + configurations.runtimeClasspath.get()).filter { it.exists() }).asPath
        // make sure dev machine stays responsive (15% slowdown on my laptop)
        ,"-J-XX:ActiveProcessorCount=${
          Runtime.getRuntime().availableProcessors() / (if (buildInfo.os.isMacOsX && !buildInfo.isCiBuild) 4 else 1)
        }"
    ) + extraArgs
  })
}

/**
 * Builds the pkl CLI for macOS/amd64.
 */
val macExecutable: TaskProvider<Exec> by tasks.registering(Exec::class) {
  configureExecutable(buildInfo.os.isMacOsX, file("$buildDir/executable/pkl-macos-amd64"))
}

/**
 * Builds the pkl CLI for linux/amd64.
 */
val linuxExecutableAmd64: TaskProvider<Exec> by tasks.registering(Exec::class) {
  configureExecutable(buildInfo.os.isLinux && buildInfo.arch == "amd64", file("$buildDir/executable/pkl-linux-amd64"))
}

/**
 * Builds the pkl CLI for linux/aarch64.
 *
 * Right now, this is built within Docker Desktop on Mac using emulation because Rio deosn't
 * provide ARM instances.
 */
val linuxExecutableAarch64: TaskProvider<Exec> by tasks.registering(Exec::class) {
  configureExecutable(buildInfo.os.isLinux && buildInfo.arch == "aarch64", file("$buildDir/executable/pkl-linux-aarch64"))
}

/**
 * Builds a statically linked CLI for linux/amd64.
 *
 * Note: we don't publish the same for linux/aarch64 because native-image doesn't support this.
 * Details: https://www.graalvm.org/22.0/reference-manual/native-image/ARM64/
 */
val alpineExecutableAmd64: TaskProvider<Exec> by tasks.registering(Exec::class) {
  configureExecutable(
      buildInfo.os.isLinux && buildInfo.arch == "amd64",
      file("$buildDir/executable/pkl-alpine-linux-amd64"),
      listOf(
        "--static",
        "--libc=musl",
        "-H:CCompilerOption=-Wl,-z,stack-size=10485760",
        "-Dorg.pkl.compat=alpine"
      )
  )
}

tasks.assembleNative {
  dependsOn(macExecutable, linuxExecutableAmd64, linuxExecutableAarch64, alpineExecutableAmd64)
}

// make Java executable available to other subprojects
// (we don't do the same for native executables because we don't want tasks assemble/build to build them)
artifacts {
  add("javaExecutable", javaExecutable.map { it.outputs.files.singleFile }) {
    name = "pkl-cli-java"
    classifier = null
    extension = "jar"
    builtBy(javaExecutable)
  }
}

//region Maven Publishing
publishing {
  publications {
    register<MavenPublication>("javaExecutable") {
      artifactId = "pkl-cli-java"

      artifact(javaExecutable.map { it.outputs.files.singleFile }) {
        classifier = null
        extension = "jar"
        builtBy(javaExecutable)
      }

      pom {
        description.set("""
          Pkl CLI executable for Java.
          Can be executed directly on *nix (if the `java` command is found on the PATH) and with `java -jar` otherwise.
          Requires Java 11 or higher.
        """.trimIndent())
      }
    }
    create<MavenPublication>("macExecutableAmd64") {
      artifactId = "pkl-cli-macos-amd64"
      artifact(stagedMacAmd64Executable.singleFile) {
        classifier = null
        extension = "bin"
        builtBy(stagedMacAmd64Executable)
      }
      pom {
        description.set("Native Pkl CLI executable for macOS/amd64.")
      }
    }
    create<MavenPublication>("linuxExecutableAmd64") {
      artifactId = "pkl-cli-linux-amd64"
      artifact(stagedLinuxAmd64Executable.singleFile) {
        classifier = null
        extension = "bin"
        builtBy(stagedLinuxAmd64Executable)
      }
      pom {
        description.set("Native Pkl CLI executable for linux/amd64.")
      }
    }
    create<MavenPublication>("linuxExecutableAarch64") {
      artifactId = "pkl-cli-linux-aarch64"
      artifact(stagedLinuxAarch64Executable.singleFile) {
        classifier = null
        extension = "bin"
        builtBy(stagedLinuxAarch64Executable)
      }
      pom { 
        description.set("Native Pkl CLI executable for linux/aarch64.")
      }
    }
    create<MavenPublication>("alpineLinuxExecutableAmd64") {
      artifactId = "pkl-cli-alpine-linux-amd64"
      artifact(stagedAlpineLinuxAmd64Executable.singleFile) {
        classifier = null
        extension = "bin"
        builtBy(stagedAlpineLinuxAmd64Executable)
      }
      pom {
        description.set("Native Pkl CLI executable for linux/amd64 and statically linked to musl.")
      }
    }
  }
}

signing {
  sign(publishing.publications["javaExecutable"])
  sign(publishing.publications["macExecutableAmd64"])
  sign(publishing.publications["linuxExecutableAmd64"])
  sign(publishing.publications["linuxExecutableAarch64"])
  sign(publishing.publications["alpineLinuxExecutableAmd64"])
}
//endregion

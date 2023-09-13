@file:Suppress("UnstableApiUsage")

import org.gradle.crypto.checksum.Checksum

plugins {
  pklAllProjects
  pklKotlinLibrary
  pklPublishLibrary
  pklNativeBuild
  `maven-publish`

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
        url.set("https://github.com/pkl-lang/pkl/tree/dev/pkl-cli")
        description.set("Pkl CLI Java library.")
      }
    }
  }
}

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

val stageMacExecutable by tasks.registering(Exec::class) {
  configureStageUpload(
    macExecutable,
    "pkl-cli-macos"
  )
}

val stageLinuxExecutableAarch64 by tasks.registering(Exec::class) {
  configureStageUpload(
    linuxExecutableAarch64,
    "pkl-cli-linux-aarch64"
  )
}

/**
 * Publishes locally built executables to a staging repo (same repo that hosts the GraalVM distros).
 *
 * CI builds will pick up the executable from the staging repo and publish it to libs-release.
 * This is an interim solution until CI supports linux/aarch64 or darwin builds, or native-image
 * supports cross-compilation.
 * Uses curl instead of Maven publishing because the latter doesn't work for this repo
 * (at least not from a dev machine).
 * The upload path follows Maven conventions so that the artifact can be consumed as Maven
 * dependency.
 */
fun Exec.configureStageUpload(fromTask: TaskProvider<Exec>, artifactId: String) {
  // TODO: remove this in the new CI
  enabled = buildInfo.os.isMacOsX
  dependsOn(fromTask)

  executable = "curl"
  val uploadUrl = "https://artifacts.apple.com/pcl-modules-local/staging/com/apple/pkl/staging/$artifactId/${buildInfo.pklVersionNonUnique}/$artifactId-${buildInfo.pklVersionNonUnique}.bin"

  argumentProviders.add(CommandLineArgumentProvider {
    // defined in ~/.gradle/gradle.properties
    val user = project.rootProject.extra["artifactory_user"]!!.toString()
    val password = project.rootProject.extra["artifactory_password"]!!.toString()

    listOf(
      "--upload-file", fromTask.get().outputs.files.singleFile.path,
      "-u", "$user:$password",
      "-X", "PUT",
      uploadUrl
    )
  })

  doLast {
    println("")
    println("Staged ${fromTask.name} binary at: $uploadUrl")
  }
}

val stage by tasks.registering {
  dependsOn(stageMacExecutable)
  dependsOn(stageLinuxExecutableAarch64)
}

//region Homebrew Publishing

val brewPublishVersion: String = project.version.toString()

val stagedMacExecutable: Configuration by configurations.creating {
  // always use the latest snapshot
  resolutionStrategy {
    deactivateDependencyLocking()
    cacheChangingModulesFor(0, "seconds")
  }
}

val stagedLinuxExecutableAarch64: Configuration by configurations.creating {
  // always use the latest snapshot
  resolutionStrategy {
    deactivateDependencyLocking()
    cacheChangingModulesFor(0, "seconds")
  }
}

dependencies {
  stagedMacExecutable("com.apple.pkl.staging:pkl-cli-macos:${buildInfo.pklVersionNonUnique}@bin") {
    isChanging = true
  }
  stagedLinuxExecutableAarch64("com.apple.pkl.staging:pkl-cli-linux-aarch64:${buildInfo.pklVersionNonUnique}@bin") {
    isChanging = true
  }
}

fun Tar.configureTar(fromSrc: Any, os: String, arch: String) {
  destinationDirectory.set(file("$buildDir/brew${os}Tar"))
  archiveFileName.set("pkl-${brewPublishVersion}.${os}_${arch}.tar.gz")

  into("bin") {
    from(fromSrc)
    rename { "pkl" }
  }

  compression = Compression.GZIP
  fileMode = Integer.valueOf("0755", 8)
}

val brewMacTar by tasks.registering(Tar::class) {
  configureTar(
    if (buildInfo.os.isMacOsX) macExecutable else stagedMacExecutable,
    "darwin",
    "amd64"
  )
}

val brewLinuxTar by tasks.registering(Tar::class) {
  configureTar(
    linuxExecutableAmd64,
    "linux",
    "amd64"
  )
}

val brewLinuxAarch64Tar by tasks.registering(Tar::class) {
  configureTar(
    if (buildInfo.arch == "aarch64") linuxExecutableAarch64 else stagedLinuxExecutableAarch64,
    "linux",
    "aarch64"
  )
}

val brewMacChecksum by tasks.registering(Checksum::class) {
  dependsOn(brewMacTar)
  files = brewMacTar.get().outputs.files
  outputDir = file("$buildDir/brewMacChecksum")
  algorithm = Checksum.Algorithm.SHA256
}

val brewMacChecksumFile: Provider<File> = brewMacChecksum.map { checksum ->
  file("${checksum.outputDir}/${brewMacTar.get().archiveFileName.get()}.sha256")
}

val brewLinuxChecksum by tasks.registering(Checksum::class) {
  dependsOn(brewLinuxTar)
  files = brewLinuxTar.get().outputs.files
  outputDir = file("$buildDir/brewLinuxChecksum")
  algorithm = Checksum.Algorithm.SHA256
}

val brewLinuxChecksumFile: Provider<File> = brewLinuxChecksum.map { checksum ->
  file("${checksum.outputDir}/${brewLinuxTar.get().archiveFileName.get()}.sha256")
}

val brewLinuxAarch64Checksum by tasks.registering(Checksum::class) {
  dependsOn(brewLinuxAarch64Tar)
  files = brewLinuxAarch64Tar.get().outputs.files
}

val brewLinuxAarch64ChecksumFile: Provider<File> = brewLinuxAarch64Checksum.map { checksum ->
  file("${checksum.outputDir}/${brewLinuxAarch64Tar.get().archiveFileName.get()}.sha256")
}

val publishBrewTars by tasks.registering(Sync::class) {
  destinationDir = file("$buildDir/publishBrewTars")
  from(brewMacTar)
  from(brewLinuxTar)
  from(brewLinuxAarch64Tar)
}
//endregion

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
          Requires Java 8 or higher.
        """.trimIndent())
      }
    }

    register<MavenPublication>("macExecutable") {
      artifactId = "pkl-cli-macos"

      if (buildInfo.os.isMacOsX) {
        artifact(macExecutable.map { it.outputs.files.singleFile }) {
          classifier = null
          extension = "bin"
          builtBy(macExecutable)
        }
      } else {
        artifact(provider { stagedMacExecutable.singleFile }) {
          classifier = null
          extension = "bin"
          builtBy(stagedMacExecutable)
        }
      }

      pom {
        description.set("Native Pkl CLI executable for macOS.")
      }
    }

    create<MavenPublication>("linuxExecutableAmd64") {
      artifactId = "pkl-cli-linux-amd64"

      if (buildInfo.os.isLinux) {
        artifact(linuxExecutableAmd64.map { it.outputs.files.singleFile }) {
          classifier = null
          extension = "bin"
          builtBy(linuxExecutableAmd64)
        }
      }

      pom {
        description.set("Native Pkl CLI executable for linux/amd64.")
      }
    }

    create<MavenPublication>("linuxExecutableAarch64") {
      artifactId = "pkl-cli-linux-aarch64"

      if (buildInfo.os.isLinux && buildInfo.arch == "aarch64") {
        artifact(linuxExecutableAmd64.map { it.outputs.files.singleFile }) {
          classifier = null
          extension = "bin"
          builtBy(linuxExecutableAmd64)
        }
      } else {
        artifact(provider { stagedLinuxExecutableAarch64.singleFile }) {
          classifier = null
          extension = "bin"
          builtBy(stagedLinuxExecutableAarch64)
        }
      }

      pom {
        description.set("Native Pkl CLI executable for linux/aarch64.")
      }
    }

    create<MavenPublication>("alpineExecutableAmd64") {
      artifactId = "pkl-cli-alpine-amd64"

      if (buildInfo.os.isLinux) {
        artifact(alpineExecutableAmd64.map { it.outputs.files.singleFile }) {
          classifier = null
          extension = "bin"
          builtBy(alpineExecutableAmd64)
        }
      }

      pom {
        description.set("Native Pkl CLI executable for Alpine Linux on linux/amd64.")
      }
    }
  }
}

//endregion

// https://youtrack.jetbrains.com/issue/KTIJ-19369
@file:Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")

import org.gradle.configurationcache.extensions.capitalized


plugins {
  id("pklAllProjects")
  id("pklJvmEntrypoint")
  id("pklPureKotlin")
  id("pklPublishLibrary")
  id("pklNativeBuild")
  `maven-publish`
  distribution

  id(libs.plugins.shadow.get().pluginId)
  alias(libs.plugins.checksum)
}

description = "Pkl command line interface entrypoint"
private val entrypoint = "org.pkl.cli.Main"
private val module = "pkl.cli"

// make Java executable available to other subprojects
val javaExecutableConfiguration: Configuration = configurations.create("javaExecutable")

// make Java executable available to other subprojects
val nativeCliExecutableConfiguration: Configuration = configurations.create("nativeExecutable")

val enablePgo = false
val oracleGvm = false
val pgoInstrument = enablePgo
val enableExperimental = true
val profileName = "pkl-cli.iprof"
val profilesZip = layout.projectDirectory.file("pgo.zip")
val enableRelease = findProperty("nativeRelease") == "true"
val isNativeBuildEnabled = gradle.startParameter.taskNames.any { subject ->
  subject.lowercase().let {
    "native" in it || "executable" in it
  }
}
val extraJavacArgs: List<String> = listOfNotNull(
  "--add-exports=org.graalvm.truffle.runtime.svm/com.oracle.svm.truffle=$module",
  if (oracleGvm) null else "--add-reads=org.graalvm.truffle.runtime.svm=$module",
  if (!oracleGvm) null else "--add-reads=com.oracle.svm.truffle=$module",
  "--add-reads=$module=ALL-UNNAMED",
)
val extraJvmArgs: List<String> = listOf(
  "--enable-native-access=ALL-UNNAMED",
)

val devNativeImageFlags = listOfNotNull(
  "-Ob",
  if (!enablePgo) null else "--pgo-instrument",
)

val releaseCFlags: List<String> = listOf(
  "-O3",
  "-v",
)

val releaseNativeImageFlags = listOf(
  "-O3",
  "-march=native",
  if (!enablePgo) null else "--pgo=../profiles/$profileName",
).plus(releaseCFlags.flatMap {
  listOf(
    "-H:NativeLinkerOption=$it",
    "--native-compiler-options=$it",
  )
})

val experimentalGvmNativeFlags: List<String> = listOf(
  "--enable-preview",
  "--add-modules=jdk.incubator.vector",
  "--enable-native-access=pkl.core,pkl.cli,ALL-UNNAMED",

  // Native Image Runtime Options
  "-J-Dpolyglot.image-build-time.PreinitializeContextsWithNative=true",

  // Hosted Runtime Options
  "-H:CStandard=C11",
  "-H:+UnlockExperimentalVMOptions",
).plus(
  if (!oracleGvm) emptyList() else listOf(
    "-H:+AOTInline",
    "-H:+VectorizeSIMD",
    "-H:+LSRAOptimization",
    "-H:+MLProfileInference",
    "-H:+VectorPolynomialIntrinsics",
    "-H:+UseCompressedReferences",
  )
)

val nativeImageExclusions = listOf(
  libs.graalSdk,
)

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url = "https://github.com/apple/pkl/tree/main/pkl-cli"
        description = "Pkl CLI Java library."
      }
    }
  }
}

val stagedMacAmd64Executable: Configuration by configurations.creating
val stagedMacAarch64Executable: Configuration by configurations.creating
val stagedLinuxAmd64Executable: Configuration by configurations.creating
val stagedLinuxAarch64Executable: Configuration by configurations.creating
val stagedAlpineLinuxAmd64Executable: Configuration by configurations.creating
val modulepath: Configuration by configurations.creating
val compileClasspath: Configuration by configurations.getting {
  extendsFrom(modulepath)
}

private fun stagedDir(dir: String): File = layout.buildDirectory.dir(dir).get().asFile

dependencies {
  compileOnly(libs.kotlinStdlib)

  // JPMS module path
  modulepath(libs.kotlinStdlib)
  modulepath(libs.jlineReader)
  modulepath(libs.svmTruffle)
  modulepath(libs.truffleApi)
  modulepath(libs.truffleRuntime)
  modulepath(projects.pklCore)

  // CliEvaluator exposes PClass
  api(projects.pklCore)
  // CliEvaluatorOptions exposes CliBaseOptions
  api(projects.pklCommonsCli)

  implementation(projects.pklCommons)
  implementation(libs.jansi)
  implementation(libs.jlineTerminal)
  implementation(libs.jlineTerminalJansi)
  implementation(projects.pklServer)
  implementation(libs.clikt) {
    // force clikt to use our version of the kotlin stdlib
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
  }

  testImplementation(projects.pklCommonsTest)

  compileOnly(libs.svm)
  if (oracleGvm) modulepath(libs.truffleEnterprise)

  stagedMacAmd64Executable(files(stagedDir("executable/pkl-macos-amd64")))
  stagedMacAarch64Executable(files(stagedDir("executable/pkl-macos-aarch64")))
  stagedLinuxAmd64Executable(files(stagedDir("executable/pkl-linux-amd64")))
  stagedLinuxAarch64Executable(files(stagedDir("executable/pkl-linux-aarch64")))
  stagedAlpineLinuxAmd64Executable(files(stagedDir("executable/pkl-alpine-linux-amd64")))
}

application {
  mainClass = entrypoint
}

tasks.jar {
  manifest {
    attributes += mapOf("Main-Class" to "org.pkl.cli.Main")
  }

  // not required at runtime
  exclude("org/pkl/cli/svm/**")
}

tasks.withType(JavaExec::class).configureEach {
  jvmArgs = extraJvmArgs.plus(jvmArgs ?: emptyList())
}

tasks.shadowJar {
  archiveFileName = "jpkl"

  exclude("META-INF/maven/**")
  exclude("META-INF/upgrade/**")

  // org.antlr.v4.runtime.misc.RuleDependencyProcessor
  exclude("META-INF/services/javax.annotation.processing.Processor")

  exclude("module-info.*")
}

val inflateProfiles by tasks.registering(Copy::class) {
  from(zipTree(profilesZip))
  into(layout.buildDirectory.dir("profiles"))
  outputs.files(layout.buildDirectory.files("profiles/$profileName"))
}

val releasePrep by tasks.registering {
  onlyIf { enableRelease }
  dependsOn(inflateProfiles)
  outputs.cacheIf { true }
}

fun selectNativeHostExecutable(): TaskProvider<Exec> {
  return when {
    buildInfo.os.isMacOsX && (buildInfo.arch == "amd64") -> macExecutableAmd64
    buildInfo.os.isMacOsX && (buildInfo.arch == "aarch64") -> macExecutableAarch64
    buildInfo.os.isLinux && (buildInfo.arch == "amd64") -> linuxExecutableAmd64
    buildInfo.os.isLinux && (buildInfo.arch == "aarch64") -> linuxExecutableAarch64
    buildInfo.os.isWindows -> windowsAmd64
    else -> error("No host binary could be selected; please check your OS for Pkl support")
  }
}

val nativeHostExecutable by tasks.registering {
  selectNativeHostExecutable().get().let {
    dependsOn(it)
    inputs.files(it.outputs.files)
    outputs.files(it.outputs.files)
    outputs.cacheIf { true }
  }
}

val javaExecutable by tasks.registering(ExecutableJar::class) {
  inJar = tasks.shadowJar.flatMap { it.archiveFile }
  outJar = layout.buildDirectory.dir("executable/jpkl").get().asFile

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
  // dummy output to satisfy up-to-date check
  val outputFile = layout.buildDirectory.dir("testStartJavaExecutable").get().asFile
  outputs.file(outputFile)

  executable = javaExecutable.get().outputs.files.singleFile.toString()
  args("--version")

  doFirst { outputFile.delete() }
  doLast { outputFile.writeText("OK") }
}

tasks.check {
  dependsOn(testStartJavaExecutable)
}

val kernel32Init = listOf(
  "org.msgpack.core.buffer.DirectBufferAccess",
  "org.jline.nativ.Kernel32",
  "org.jline.nativ.Kernel32${'$'}CHAR_INFO",
  "org.jline.nativ.Kernel32${'$'}CONSOLE_SCREEN_BUFFER_INFO",
  "org.jline.nativ.Kernel32${'$'}COORD",
  "org.jline.nativ.Kernel32${'$'}FOCUS_EVENT_RECORD",
  "org.jline.nativ.Kernel32${'$'}INPUT_EVENT_RECORD",
  "org.jline.nativ.Kernel32${'$'}INPUT_RECORD",
  "org.jline.nativ.Kernel32${'$'}KEY_EVENT_RECORD",
  "org.jline.nativ.Kernel32${'$'}MENU_EVENT_RECORD",
  "org.jline.nativ.Kernel32${'$'}MOUSE_EVENT_RECORD",
  "org.jline.nativ.Kernel32${'$'}SMALL_RECT",
  "org.jline.nativ.Kernel32${'$'}WINDOW_BUFFER_SIZE_RECORD",
).joinToString(",")

fun createArchiveTasks(
  exec: Provider<Exec>,
  outputFile: File,
  release: Boolean = enableRelease,
) {
  val filenameBase = outputFile.nameWithoutExtension  // accounts for `.exe`
  val filenameSplit = filenameBase.split("-")

  // `pkl-macos-<arch>` -> `macos-<arch>-<release>`
  val releaseTag = filenameSplit.drop(1).plus(listOf(
    if (release) "opt" else "dev"
  )).joinToString("-")

  // `macos-<arch>` -> `macos<Arch>`
  val taskPostfix = "${filenameSplit[1]}${filenameSplit[2].capitalized()}"

  // `macos<Arch>` -> `macos<Arch>DistZip
  val zipTaskName = "${taskPostfix}DistZip"  // `macosAmd64DistZip`
  val tarTaskName = "${taskPostfix}DistTar"  // `macosAmd64DistTar`

  distributions.create(taskPostfix) {
    distributionBaseName = "pkl-cli"
    distributionClassifier = releaseTag
    contents {
      from(layout.projectDirectory.dir("src/dist"), exec.get().outputs.files)
      include("*")
      rename {
        if (!it.startsWith("pkl-")) it else "pkl"
      }
    }
  }
  exec.get().apply {
    finalizedBy(zipTaskName, tarTaskName)
  }
}

fun Exec.configureExecutable(isEnabled: Boolean, outputFile: File, extraArgs: List<String> = listOf()) {
  enabled = isEnabled && isNativeBuildEnabled
  dependsOn(":installGraalVm", releasePrep)

  inputs.files(sourceSets.main.map { it.output })
  inputs.files(configurations.runtimeClasspath)
  outputs.file(outputFile)

  workingDir = outputFile.parentFile
  executable = "${buildInfo.graalVm.baseDir}/bin/native-image"
  outputs.cacheIf { true }

  // JARs to exclude from the class path for the native-image build.
  val exclusions =
    if (buildInfo.graalVm.isGraal22) emptyList()
    else nativeImageExclusions.map { it.get().module.name }
  // https://www.graalvm.org/22.0/reference-manual/native-image/Options/
  argumentProviders.add(CommandLineArgumentProvider {
    listOf(
        "--strict-image-heap"
        // currently gives a deprecation warning, but we've been told 
        // that the "initialize everything at build time" *CLI* option is likely here to stay
        ,"--initialize-at-build-time="
        ,"--no-fallback"
        ,"-H:IncludeResources=org/pkl/core/stdlib/.*\\.pkl"
        ,"-H:IncludeResources=org/jline/utils/.*"
        ,"-H:IncludeResources=org/pkl/commons/cli/commands/IncludedCARoots.pem"
        //,"-H:IncludeResources=org/pkl/core/Release.properties"
        ,"-H:IncludeResourceBundles=org.pkl.core.errorMessages"
        ,"--macro:truffle-svm"
        ,"-H:Module=$module"
        ,"-H:Class=org.pkl.cli.Main"
        ,"-H:Name=${outputFile.name}"
        //,"--native-image-info"
        ,"-Dpolyglot.image-build-time.PreinitializeContexts=pkl"
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
//        ,"--features=org.pkl.cli.svm.InitFeature"
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
        ,"-J-Dcom.oracle.truffle.aot=true"
        //,"-J:-ea"
        //,"-J:-esa"
        // for use with https://www.graalvm.org/docs/tools/dashboard/
        //,"-H:DashboardDump=dashboard.dump", "-H:+DashboardAll"
        // native-image rejects non-existing class path entries -> filter
        ,"--module-path"
        ,(sourceSets.main.get().output + modulepath
            .filter { it.exists() && !exclusions.any { exclude -> it.name.contains(exclude) }})
            .asPath
        ,"--class-path"
        ,(configurations.runtimeClasspath.get()
          .filter { it.exists() && !exclusions.any { exclude -> it.name.contains(exclude) }})
          .asPath
        // make sure dev machine stays responsive (15% slowdown on my laptop)
        ,"-J-XX:ActiveProcessorCount=${
          Runtime.getRuntime().availableProcessors() / (if (buildInfo.os.isMacOsX && !buildInfo.isCiBuild) 4 else 1)
        }"
    ) + extraArgs + extraJavacArgs + extraJvmArgs + (
      if (enableExperimental) experimentalGvmNativeFlags else emptyList()
    ) + (
      if (enableRelease) releaseNativeImageFlags else devNativeImageFlags
    )
  })
}

/**
 * Builds the pkl CLI for macOS/amd64.
 */
val macExecutableAmd64: TaskProvider<Exec> by tasks.registering(Exec::class) {
  configureExecutable(
    buildInfo.os.isMacOsX,
    layout.buildDirectory.file("executable/pkl-macos-amd64").get().asFile,
    listOf(
      "--initialize-at-run-time=$kernel32Init",
    )
  )
}

createArchiveTasks(
  macExecutableAmd64,
  layout.buildDirectory.file("executable/pkl-macos-amd64").get().asFile,
)

/**
 * Builds the pkl CLI for macOS/aarch64.
 *
 * This requires that GraalVM be set to version 23.0 or greater, because 22.x does not support this
 * os/arch pair.
 */
val macExecutableAarch64: TaskProvider<Exec> by tasks.registering(Exec::class) {
  dependsOn(tasks.compileJava, tasks.compileKotlin)

  configureExecutable(
    buildInfo.os.isMacOsX,
    layout.buildDirectory.dir("executable/pkl-macos-aarch64").get().asFile,
    listOf(
      "-H:+AllowDeprecatedBuilderClassesOnImageClasspath",
      "--initialize-at-run-time=$kernel32Init",
    )
  )
}

createArchiveTasks(
  macExecutableAarch64,
  layout.buildDirectory.dir("executable/pkl-macos-aarch64").get().asFile,
)

/**
 * Builds the pkl CLI for linux/amd64.
 */
val linuxExecutableAmd64: TaskProvider<Exec> by tasks.registering(Exec::class) {
  configureExecutable(
    buildInfo.os.isLinux && buildInfo.arch == "amd64",
    layout.buildDirectory.file("executable/pkl-linux-amd64").get().asFile,
    listOf(
      "--initialize-at-run-time=$kernel32Init",
    )
  )
}

createArchiveTasks(
  linuxExecutableAmd64,
  layout.buildDirectory.file("executable/pkl-linux-amd64").get().asFile,
)

/**
 * Builds the pkl CLI for linux/aarch64.
 *
 * Right now, this is built within a container on Mac using emulation because CI does not have
 * ARM instances.
 */
val linuxExecutableAarch64: TaskProvider<Exec> by tasks.registering(Exec::class) {
  configureExecutable(
    buildInfo.os.isLinux && buildInfo.arch == "aarch64",
    layout.buildDirectory.file("executable/pkl-linux-aarch64").get().asFile,
    listOf(
      "--initialize-at-run-time=$kernel32Init",
    )
  )
}

createArchiveTasks(
  linuxExecutableAarch64,
  layout.buildDirectory.file("executable/pkl-linux-aarch64").get().asFile,
)

/**
 * Builds a statically linked CLI for linux/amd64.
 *
 * Note: we don't publish the same for linux/aarch64 because native-image doesn't support this.
 * Details: https://www.graalvm.org/22.0/reference-manual/native-image/ARM64/
 */
val alpineExecutableAmd64: TaskProvider<Exec> by tasks.registering(Exec::class) {
  configureExecutable(
      buildInfo.os.isLinux && buildInfo.arch == "amd64" && buildInfo.hasMuslToolchain,
    layout.buildDirectory.file("executable/pkl-alpine-linux-amd64").get().asFile,
      listOf(
        "--static",
        "--libc=musl",
        "-H:CCompilerOption=-Wl,-z,stack-size=10485760",
        "-Dorg.pkl.compat=alpine",
        "--initialize-at-run-time=$kernel32Init",
      )
  )
}

createArchiveTasks(
  alpineExecutableAmd64,
  layout.buildDirectory.file("executable/pkl-alpine-linux-amd64").get().asFile,
)

/**
 * Builds a statically linked CLI for Windows.
 *
 * Experimental.
 */
val windowsAmd64: TaskProvider<Exec> by tasks.registering(Exec::class) {
  configureExecutable(
    buildInfo.os.isWindows,
    layout.buildDirectory.file("executable/pkl-windows-amd64").get().asFile,
    listOf(
      "--static",
      "--libc=musl",
      "-H:CCompilerOption=-Wl,-z,stack-size=10485760",
      "-Dorg.pkl.compat=windows",
    )
  )
}
createArchiveTasks(
  windowsAmd64,
  layout.buildDirectory.file("executable/pkl-windows-amd64").get().asFile,
)

val nativeTargets = listOf(
  macExecutableAmd64,
  macExecutableAarch64,
  linuxExecutableAmd64,
  linuxExecutableAarch64,
  alpineExecutableAmd64,
  windowsAmd64,
)

tasks.assembleNative {
  dependsOn(nativeTargets)
}

artifacts {
  // make Java executable available to other subprojects
  add(javaExecutableConfiguration.name, javaExecutable.map { it.outputs.files.singleFile }) {
    name = "pkl-cli-java"
    classifier = null
    extension = "jar"
    builtBy(javaExecutable)
  }

  // add native CLI for downstream smoke tests
  add(nativeCliExecutableConfiguration.name, nativeHostExecutable.map { it.outputs.files.singleFile }) {
    name = "pkl-cli"
    classifier = null
    builtBy(nativeHostExecutable)
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
        url = "https://github.com/apple/pkl/tree/main/pkl-cli"
        description = """
          Pkl CLI executable for Java.
          Can be executed directly on *nix (if the `java` command is found on the PATH) and with `java -jar` otherwise.
          Requires Java 11 or higher.
        """.trimIndent()
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
        name = "pkl-cli-macos-amd64"
        url = "https://github.com/apple/pkl/tree/main/pkl-cli"
        description = "Native Pkl CLI executable for macOS/amd64."
      }
    }
    create<MavenPublication>("macExecutableAarch64") {
      artifactId = "pkl-cli-macos-aarch64"
      artifact(stagedMacAarch64Executable.singleFile) {
        classifier = null
        extension = "bin"
        builtBy(stagedMacAarch64Executable)
      }
      pom {
        name = "pkl-cli-macos-aarch64"
        url = "https://github.com/apple/pkl/tree/main/pkl-cli"
        description = "Native Pkl CLI executable for macOS/aarch64."
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
        name = "pkl-cli-linux-amd64"
        url = "https://github.com/apple/pkl/tree/main/pkl-cli"
        description = "Native Pkl CLI executable for linux/amd64."
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
        name = "pkl-cli-linux-aarch64"
        url = "https://github.com/apple/pkl/tree/main/pkl-cli"
        description = "Native Pkl CLI executable for linux/aarch64."
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
        name = "pkl-cli-alpine-linux-amd64"
        url = "https://github.com/apple/pkl/tree/main/pkl-cli"
        description = "Native Pkl CLI executable for linux/amd64 and statically linked to musl."
      }
    }
  }
}

signing {
  sign(publishing.publications["javaExecutable"])
  sign(publishing.publications["linuxExecutableAarch64"])
  sign(publishing.publications["linuxExecutableAmd64"])
  sign(publishing.publications["macExecutableAarch64"])
  sign(publishing.publications["macExecutableAmd64"])
  sign(publishing.publications["alpineLinuxExecutableAmd64"])
}
//endregion

val javac: JavaCompile by tasks.named("compileJava", JavaCompile::class)
javac.apply {
  options.compilerArgumentProviders.add(CommandLineArgumentProvider {
    extraJavacArgs
  })
}

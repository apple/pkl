import com.diffplug.gradle.spotless.SpotlessExtension
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
  kotlin("jvm") // for `src/generator/kotlin`
  id("pklAllProjects")
  id("pklJavaLibrary")
  id("pklPublishLibrary")
  id("pklNativeBuild")
  antlr
  idea
}

description = "Pkl core language"

val moduleName = "pkl.core"
val generatorSourceSet = sourceSets.register("generator")
val extraJavacArgs = listOf(
  "--add-reads=$moduleName=ALL-UNNAMED",
)

sourceSets {
  main {
    java {
      srcDir(file("generated/antlr"))
    }
  }
}

idea {
  module {
    // mark src/main/antlr as source dir
    // mark generated/antlr as generated source dir
    // mark generated/truffle as generated source dir
    sourceDirs = sourceDirs + files("src/main/antlr", "generated/antlr", "generated/truffle")
    generatedSourceDirs = generatedSourceDirs + files("generated/antlr", "generated/truffle")
  }
}

val javaExecutableConfiguration: Configuration = configurations.create("javaExecutable")

// workaround for https://github.com/gradle/gradle/issues/820
configurations.api.get().let { apiConfig ->
  apiConfig.setExtendsFrom(apiConfig.extendsFrom.filter { it.name != "antlr" })
}

dependencies {
  annotationProcessor(libs.truffleDslProcessor)
  annotationProcessor(generatorSourceSet.get().runtimeClasspath)

  antlr(libs.antlr)

  compileOnly(libs.jsr305)
  // pkl-core implements pkl-executor's ExecutorSpi, but the SPI doesn't ship with pkl-core
  compileOnly(projects.pklExecutor)

  implementation(libs.antlrRuntime)
  implementation(libs.truffleApi)
  implementation(libs.graalSdk)

  implementation(libs.paguro) {
    exclude(group = "org.jetbrains", module = "annotations")
  }

  implementation(libs.snakeYaml)

  testImplementation(projects.pklCommonsTest)

  add("generatorImplementation", libs.javaPoet)
  add("generatorImplementation", libs.truffleApi)
  add("generatorImplementation", libs.kotlinStdlib)

  javaExecutableConfiguration(project(":pkl-cli", "javaExecutable"))
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url = "https://github.com/apple/pkl/tree/main/pkl-core"
        description = """
          Core implementation of the Pkl configuration language.
          Includes Java APIs for embedding the language into JVM applications,
          and for building libraries and tools on top of the language.
        """.trimIndent()
      }
    }
  }
}

tasks.generateGrammarSource {
  maxHeapSize = "64m"

  // generate only visitor
  arguments = arguments + listOf("-visitor", "-no-listener")

  // Due to https://github.com/antlr/antlr4/issues/2260,
  // we can't put .g4 files into src/main/antlr/org/pkl/core/parser/antlr.
  // Instead, we put .g4 files into src/main/antlr, adapt output dir below,
  // and use @header directives in .g4 files (instead of setting `-package` argument here)
  // and task makeIntelliJAntlrPluginHappy to fix up the IDE story.
  outputDirectory = file("generated/antlr/org/pkl/core/parser/antlr")
}

tasks.compileJava {
  dependsOn(tasks.generateGrammarSource)

  options.compilerArgumentProviders.add(CommandLineArgumentProvider {
    extraJavacArgs
  })
}

tasks.sourcesJar {
  dependsOn(tasks.generateGrammarSource)
}

listOf(
  tasks.jar,
  tasks.javadocJar,
  tasks.sourcesJar,
).forEach {
  it {
    outputs.cacheIf { true }
  }
}

tasks.generateTestGrammarSource {
  enabled = false
}
tasks.named("generateGeneratorGrammarSource") {
  enabled = false
}

// Satisfy expectations of IntelliJ ANTLR plugin,
// which can't otherwise cope with our ANTLR setup.
val makeIntelliJAntlrPluginHappy by tasks.registering(Copy::class) {
  dependsOn(tasks.generateGrammarSource)
  into("src/main/antlr")
  from("generated/antlr/org/pkl/core/parser/antlr") {
    include("PklLexer.tokens")
  }
}

tasks.processResources {
  inputs.property("version", buildInfo.pklVersion)
  inputs.property("commitId", buildInfo.commitId)

  filesMatching("org/pkl/core/Release.properties") {
    val stdlibModules = fileTree("$rootDir/stdlib") { 
      include("*.pkl") 
      exclude("doc-package-info.pkl")
    }.map { "pkl:" + it.nameWithoutExtension } 
      .sortedBy { it.lowercase() }
    
    filter<ReplaceTokens>("tokens" to mapOf(
        "version" to buildInfo.pklVersion,
        "commitId" to buildInfo.commitId,
        "stdlibModules" to stdlibModules.joinToString(",")
    ))
  }

  into("org/pkl/core/stdlib") {
    from("$rootDir/stdlib") {
      include("*.pkl")
    }
  }
}

tasks.compileJava {
  options.generatedSourceOutputDirectory = file("generated/truffle")
}

tasks.compileKotlin {
  enabled = false
}

tasks.test {
  jvmArgs(extraJavacArgs)

  inputs.dir("src/test/files/LanguageSnippetTests/input")
  inputs.dir("src/test/files/LanguageSnippetTests/input-helper")
  inputs.dir("src/test/files/LanguageSnippetTests/output")

  useJUnitPlatform {
    excludeEngines("MacAmd64LanguageSnippetTestsEngine")
    excludeEngines("MacAarch64LanguageSnippetTestsEngine")
    excludeEngines("LinuxAmd64LanguageSnippetTestsEngine")
    excludeEngines("LinuxAarch64LanguageSnippetTestsEngine")
    excludeEngines("AlpineLanguageSnippetTestsEngine")
  }
}

val testJavaExecutable by tasks.registering(Test::class) {
  inputs.dir("src/test/files/LanguageSnippetTests/input")
  inputs.dir("src/test/files/LanguageSnippetTests/input-helper")
  inputs.dir("src/test/files/LanguageSnippetTests/output")

  testClassesDirs = files(tasks.test.get().testClassesDirs)
  classpath =
    // compiled test classes
    sourceSets.test.get().output +
    // java executable
    javaExecutableConfiguration +
    // test-only dependencies
    // (test dependencies that are also main dependencies must already be contained in java executable;
    // to verify that we don't want to include them here)
    (configurations.testRuntimeClasspath.get() - configurations.runtimeClasspath.get())

  useJUnitPlatform {
    includeEngines("LanguageSnippetTestsEngine")
  }
}

tasks.check {
  dependsOn(testJavaExecutable)
}

val testMacExecutableAmd64 by tasks.registering(Test::class) {
  enabled = buildInfo.os.isMacOsX && buildInfo.graalVm.isGraal22
  dependsOn(":pkl-cli:macExecutableAmd64")

  inputs.dir("src/test/files/LanguageSnippetTests/input")
  inputs.dir("src/test/files/LanguageSnippetTests/input-helper")
  inputs.dir("src/test/files/LanguageSnippetTests/output")

  testClassesDirs = files(tasks.test.get().testClassesDirs)
  classpath = tasks.test.get().classpath

  useJUnitPlatform {
    includeEngines("MacAmd64LanguageSnippetTestsEngine")
  }
}

val testMacExecutableAarch64 by tasks.registering(Test::class) {
  enabled = buildInfo.os.isMacOsX && !buildInfo.graalVm.isGraal22
  dependsOn(":pkl-cli:macExecutableAarch64")

  inputs.dir("src/test/files/LanguageSnippetTests/input")
  inputs.dir("src/test/files/LanguageSnippetTests/input-helper")
  inputs.dir("src/test/files/LanguageSnippetTests/output")

  testClassesDirs = files(tasks.test.get().testClassesDirs)
  classpath = tasks.test.get().classpath

  useJUnitPlatform {
    includeEngines("MacAarch64LanguageSnippetTestsEngine")
  }
}

val testLinuxExecutableAmd64 by tasks.registering(Test::class) {
  enabled = buildInfo.os.isLinux && buildInfo.arch == "amd64"
  dependsOn(":pkl-cli:linuxExecutableAmd64")

  inputs.dir("src/test/files/LanguageSnippetTests/input")
  inputs.dir("src/test/files/LanguageSnippetTests/input-helper")
  inputs.dir("src/test/files/LanguageSnippetTests/output")

  testClassesDirs = files(tasks.test.get().testClassesDirs)
  classpath = tasks.test.get().classpath

  useJUnitPlatform {
    includeEngines("LinuxAmd64LanguageSnippetTestsEngine")
  }
}

val testLinuxExecutableAarch64 by tasks.registering(Test::class) {
  enabled = buildInfo.os.isLinux && buildInfo.arch == "aarch64"
  dependsOn(":pkl-cli:linuxExecutableAarch64")

  inputs.dir("src/test/files/LanguageSnippetTests/input")
  inputs.dir("src/test/files/LanguageSnippetTests/input-helper")
  inputs.dir("src/test/files/LanguageSnippetTests/output")

  testClassesDirs = files(tasks.test.get().testClassesDirs)
  classpath = tasks.test.get().classpath

  useJUnitPlatform {
    includeEngines("LinuxAarch64LanguageSnippetTestsEngine")
  }
}

val testAlpineExecutableAmd64 by tasks.registering(Test::class) {
  enabled = buildInfo.os.isLinux && buildInfo.arch == "amd64" && buildInfo.hasMuslToolchain
  dependsOn(":pkl-cli:alpineExecutableAmd64")

  inputs.dir("src/test/files/LanguageSnippetTests/input")
  inputs.dir("src/test/files/LanguageSnippetTests/input-helper")
  inputs.dir("src/test/files/LanguageSnippetTests/output")

  testClassesDirs = files(tasks.test.get().testClassesDirs)
  classpath = tasks.test.get().classpath

  useJUnitPlatform {
    includeEngines("AlpineLanguageSnippetTestsEngine")
  }
}

tasks.testNative {
  dependsOn(testLinuxExecutableAmd64)
  dependsOn(testLinuxExecutableAarch64)
  dependsOn(testMacExecutableAmd64)
  dependsOn(testMacExecutableAarch64)
  dependsOn(testAlpineExecutableAmd64)
}

tasks.clean {
  delete("generated/")
  delete(layout.buildDirectory.dir("test-packages"))
}

spotless {
  antlr4 {
    licenseHeaderFile(rootProject.file("build-logic/src/main/resources/license-header.star-block.txt"))
    target(files("src/main/antlr/PklParser.g4", "src/main/antlr/PklLexer.g4"))
  }
}

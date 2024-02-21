@file:Suppress("HttpUrlsUsage")

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  id("pklJvmLibrary")
  id("com.diffplug.spotless")
}

// Main build info extension.
val info = the<BuildInfo>()

// Version catalog library symbols.
val libs = the<LibrariesForLibs>()

// Conditional plugin application.
if (info.analysis.enablePmd) apply(plugin = "pmd").also {
  configure<PmdExtension> {
    isConsoleOutput = false
    toolVersion = libs.versions.pmd.get()
    threads = Runtime.getRuntime().availableProcessors()
    isIgnoreFailures = true
    incrementalAnalysis = true
  }
}

configure<SpotlessExtension> {
  java {
    googleJavaFormat(libs.versions.googleJavaFormat.get())
    targetExclude("**/generated/**", "**/build/**")
    licenseHeaderFile(rootProject.file("build-logic/src/main/resources/license-header.star-block.txt"))
  }
}

tasks {
  // No need to run PMD or Detekt on tests.
  listOf("pmdTest", "detektTest").forEach {
    findByName(it)?.configure<Task> {
      enabled = false
    }
  }

  // This is a pure-Java target convention; see `pklKotlinLibrary` for Kotlin support.
  compileKotlin {
    enabled = false
  }

  val workAroundKotlinGradlePluginBug by registering {
    doLast {
      // Works around this problem, which sporadically appears and disappears in different subprojects:
      // A problem was found with the configuration of task ':pkl-executor:compileJava' (type 'JavaCompile').
      // > Directory '[...]/pkl/pkl-executor/build/classes/kotlin/main'
      // specified for property 'compileKotlinOutputClasses' does not exist.
      project.layout.buildDirectory.dir("classes/kotlin/main").get().asFile.mkdirs()
    }
  }

  compileJava {
    dependsOn(workAroundKotlinGradlePluginBug)
  }

  // Signing is only needed if we are publishing.
  withType(Sign::class.java).configureEach {
    onlyIf { info.isPublishing }
  }

  // Javadoc JARs are only needed if we are publishing.
  javadoc {
    classpath = sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    source = sourceSets.main.get().allJava
    title = "${project.name} ${project.version} API"
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    onlyIf { info.isPublishing }
  }
}

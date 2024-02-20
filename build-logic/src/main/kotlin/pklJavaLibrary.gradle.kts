@file:Suppress("HttpUrlsUsage")

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  pmd
  id("pklJvmLibrary")
  id("com.diffplug.spotless")
}

// Version catalog library symbols.
val libs = the<LibrariesForLibs>()

pmd {
  isConsoleOutput = true
  toolVersion = libs.versions.pmd.get()
  threads = 4
  isIgnoreFailures = true
  incrementalAnalysis = true
}

spotless {
  java {
    googleJavaFormat(libs.versions.googleJavaFormat.get())
    targetExclude("**/generated/**", "**/build/**")
    licenseHeaderFile(rootProject.file("build-logic/src/main/resources/license-header.star-block.txt"))
  }
}

tasks.compileKotlin {
  enabled = false
}

tasks.javadoc {
  classpath = sourceSets.main.get().output + sourceSets.main.get().compileClasspath
  source = sourceSets.main.get().allJava
  title = "${project.name} ${project.version} API"
  (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

val workAroundKotlinGradlePluginBug by tasks.registering {
  doLast {
    // Works around this problem, which sporadically appears and disappears in different subprojects:
    // A problem was found with the configuration of task ':pkl-executor:compileJava' (type 'JavaCompile').
    // > Directory '[...]/pkl/pkl-executor/build/classes/kotlin/main'
    // specified for property 'compileKotlinOutputClasses' does not exist.
    project.layout.buildDirectory.dir("classes/kotlin/main").get().asFile.mkdirs()
  }
}

tasks.compileJava {
  dependsOn(workAroundKotlinGradlePluginBug)
}

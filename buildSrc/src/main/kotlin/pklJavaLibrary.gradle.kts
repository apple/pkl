@file:Suppress("HttpUrlsUsage")

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  pmd
  `java-library`
  id("pklKotlinTest")
  id("com.diffplug.spotless")
}

// JVM toolchain defaults, properties, and resolved configuration.
private val defaultJvmTarget = "11"
private val jvmVendor = JvmVendorSpec.GRAAL_VM
private val jvmTargetVersion =
  (findProperty("javaTarget") as? String ?: defaultJvmTarget)

// Version catalog library symbols.
val libs = the<LibrariesForLibs>()

// make source jar available to other subprojects
val sourcesJarConfiguration: Provider<Configuration> = configurations.register("sourcesJar")

pmd {
  isConsoleOutput = true
  toolVersion = libs.versions.pmd.get()
  threads = 4
  isIgnoreFailures = true
  incrementalAnalysis = true
}

java {
  // obtain and use a Java toolchain from GraalVM, at the version specified for the project.
  sourceCompatibility = JavaVersion.toVersion(jvmTargetVersion)
  targetCompatibility = JavaVersion.toVersion(jvmTargetVersion)

  withSourcesJar() // creates `sourcesJar` task
  withJavadocJar()
}

artifacts {
  // make source jar available to other subprojects
  add("sourcesJar", tasks["sourcesJar"])
}

spotless {
  java {
    googleJavaFormat(libs.versions.googleJavaFormat.get())
    targetExclude("**/generated/**", "**/build/**")
    licenseHeaderFile(rootProject.file("buildSrc/src/main/resources/license-header.star-block.txt"))
  }
}

tasks.compileKotlin {
  enabled = false
}

tasks.jar {
  manifest {
    attributes += mapOf("Automatic-Module-Name" to "org.${project.name.replace("-", ".")}")
  }
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

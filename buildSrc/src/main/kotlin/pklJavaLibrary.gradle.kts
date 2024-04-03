@file:Suppress("HttpUrlsUsage")

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  `java-library`
  id("pklKotlinTest")
  id("com.diffplug.spotless")
}

// make sources Jar available to other subprojects
val sourcesJarConfiguration = configurations.register("sourcesJar")

// Version Catalog library symbols.
val libs = the<LibrariesForLibs>()

java {
  withSourcesJar() // creates `sourcesJar` task
  withJavadocJar()

  toolchain {
    languageVersion.set(jvmToolchainVersion)
    vendor.set(jvmToolchainVendor)
  }
}

tasks.withType<JavaExec>().configureEach {
  javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}

artifacts {
  // make sources Jar available to other subprojects
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
    layout.buildDirectory.dir("classes/kotlin/main").get().asFile.mkdirs()
  }
}

tasks.compileJava {
  dependsOn(workAroundKotlinGradlePluginBug)
}

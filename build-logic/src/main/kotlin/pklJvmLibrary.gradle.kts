import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  `java-library`
  id("pklKotlinTest")
}

// Main build info extension.
val build = the<BuildInfo>()

// Version catalog library symbols.
val libs = the<LibrariesForLibs>()

// make source jar available to other subprojects
val sourceJarConfiguration: Provider<Configuration> = configurations.register("sourcesJar")

java {
  withSourcesJar() // creates `sourcesJar` task
  withJavadocJar()

  // obtain and use a Java toolchain from GraalVM, at the version specified for the project.
  JavaVersion.toVersion(build.jvm.lib.target).let {
    sourceCompatibility = it
    targetCompatibility = it
  }

  toolchain {
    languageVersion.set(JavaLanguageVersion.of(build.jvm.toolchain.target))
    vendor.set(build.jvm.toolchain.vendor)
  }
}

artifacts {
  // make source jar available to other subprojects
  add("sourcesJar", tasks["sourcesJar"])
}

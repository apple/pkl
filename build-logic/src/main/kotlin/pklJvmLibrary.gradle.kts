import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  `java-library`
  id("pklKotlinTest")
}

// JVM toolchain defaults, properties, and resolved configuration.
private val defaultJvmTarget = "11"
private val jvmVendor = JvmVendorSpec.ADOPTIUM
private val jvmTargetVersion =
  (findProperty("javaTarget") as? String ?: defaultJvmTarget)

// Version catalog library symbols.
val libs = the<LibrariesForLibs>()

// make source jar available to other subprojects
val sourcesJarConfiguration: Provider<Configuration> = configurations.register("sourcesJar")

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

tasks.jar {
  manifest {
    attributes += mapOf("Automatic-Module-Name" to "org.${project.name.replace("-", ".")}")
  }
}

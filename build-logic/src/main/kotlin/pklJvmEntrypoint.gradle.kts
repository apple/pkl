// https://youtrack.jetbrains.com/issue/KTIJ-19369
@file:Suppress("DSL_SCOPE_VIOLATION")

plugins {
  java
  application
  kotlin("jvm")
}

// Properties and defaults for JVM entrypoints.
private val defaultJvmEntrypointTarget = "11"
private val javaEntrypointTargetProperty = "javaEntrypointTarget"
private val javaVersion =
  (findProperty(javaEntrypointTargetProperty) as? String ?: defaultJvmEntrypointTarget)

java {
  sourceCompatibility = JavaVersion.toVersion(javaVersion)
  targetCompatibility = JavaVersion.toVersion(javaVersion)
}

// https://youtrack.jetbrains.com/issue/KTIJ-19369
@file:Suppress("DSL_SCOPE_VIOLATION")

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  id("pklJvmLibrary")
  id("io.gitlab.arturbosch.detekt")
  id("org.jetbrains.kotlinx.kover")
  id("com.diffplug.spotless")
  kotlin("jvm")
  kotlin("plugin.serialization")
}

// Build properties that control reporting.
private val xmlReportingBuildProperty = "xmlReporting"
private val sarifReportingBuildProperty = "sarifReporting"
private val htmlReportingBuildProperty = "htmlReporting"

// Resolved build properties for reporting within this project.
val xmlReportingEnabled = findProperty(xmlReportingBuildProperty) == "true"
val sarifReportingEnabled = findProperty(sarifReportingBuildProperty) == "true"
val htmlReportingEnabled = findProperty(htmlReportingBuildProperty) == "true"

// Version Catalog libraries, and build info.
val libs = the<LibrariesForLibs>()
val buildInfo = project.extensions.getByType<BuildInfo>()

dependencies {
  // At least some of our kotlin APIs contain Kotlin stdlib types
  // that aren't compiled away by kotlinc (e.g., `kotlin.Function`).
  // So let's be conservative and default to `api` for now.
  // For Kotlin APIs that only target Kotlin users (e.g., pkl-config-kotlin),
  // it won't make a difference.
  api(buildInfo.libs.findLibrary("kotlinStdLib").get())
}

spotless {
  kotlin {
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
    targetExclude("**/generated/**", "**/build/**")
    licenseHeaderFile(rootProject.file("build-logic/src/main/resources/license-header.star-block.txt"))
  }
}

detekt {
  toolVersion = libs.versions.detekt.get()
  config.from(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
  baseline = project.layout.projectDirectory.file("detekt-baseline.xml").asFile
  buildUponDefaultConfig = true
  enableCompilerPlugin = true
}

tasks.withType<Detekt>().configureEach {
  autoCorrect =
    findProperty("autofixDetekt") as? String == "true"

  jvmTarget =
    findProperty("javaTarget") as? String ?: error("Please set `javaTarget` property")

  reports {
    xml.required = xmlReportingEnabled
    sarif.required = sarifReportingEnabled
    html.required = htmlReportingEnabled
  }

  if (xmlReportingEnabled) finalizedBy(reportMergeXml)
  if (sarifReportingEnabled) finalizedBy(reportMergeSarif)
}

val reportMergeXml by tasks.registering(ReportMergeTask::class) {
  output.set(rootProject.layout.buildDirectory.file("reports/detekt/detekt.xml"))
}

val reportMergeSarif by tasks.registering(ReportMergeTask::class) {
  output.set(rootProject.layout.buildDirectory.file("reports/detekt/detekt.sarif"))
}

reportMergeXml {
  onlyIf { xmlReportingEnabled }
  input.from(tasks.withType<Detekt>().map { it.xmlReportFile })
}

reportMergeSarif {
  onlyIf { sarifReportingEnabled }
  input.from(tasks.withType<Detekt>().map { it.sarifReportFile })
}

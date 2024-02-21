// https://youtrack.jetbrains.com/issue/KTIJ-19369
@file:Suppress("DSL_SCOPE_VIOLATION")

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  id("org.jetbrains.kotlinx.kover")
  kotlin("jvm")
  kotlin("plugin.serialization")
}

// Main build info extension.
private val build = the<BuildInfo>()

// Version Catalog libraries, and build info.
private val libs = the<LibrariesForLibs>()

dependencies {
  // At least some of our kotlin APIs contain Kotlin stdlib types
  // that aren't compiled away by kotlinc (e.g., `kotlin.Function`).
  // So let's be conservative and default to `api` for now.
  // For Kotlin APIs that only target Kotlin users (e.g., pkl-config-kotlin),
  // it won't make a difference.
  api(libs.bundles.kotlin.stdlib)
}

if (build.analysis.enabled) apply(plugin = "io.gitlab.arturbosch.detekt").also {
  configure<DetektExtension> {
    toolVersion = libs.versions.detekt.get()
    config.from(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
    baseline = project.layout.projectDirectory.file("detekt-baseline.xml").asFile
    buildUponDefaultConfig = true
    enableCompilerPlugin = true
  }
}
if (build.analysis.enabled) apply(plugin = "com.diffplug.spotless").also {
  configure<SpotlessExtension> {
    kotlin {
      ktfmt(libs.versions.ktfmt.get()).googleStyle()
      targetExclude("**/generated/**", "**/build/**")
      licenseHeaderFile(rootProject.file("build-logic/src/main/resources/license-header.star-block.txt"))
    }
    kotlinGradle {
      ktfmt(libs.versions.ktfmt.get()).googleStyle()
    }
  }
}

tasks.withType<Detekt>().configureEach {
  autoCorrect = findProperty("autofixDetekt") as? String == "true"
  jvmTarget = build.jvm.lib.target.toString()

  reports {
    xml.required = build.analysis.xmlReporting
    sarif.required = build.analysis.sarifReporting
    html.required = build.analysis.htmlReporting
  }

  if (build.analysis.xmlReporting) finalizedBy(reportMergeXml)
  if (build.analysis.htmlReporting) finalizedBy(reportMergeSarif)
}

val reportMergeXml by tasks.registering(ReportMergeTask::class) {
  output.set(rootProject.layout.buildDirectory.file("reports/detekt/detekt.xml"))
}

val reportMergeSarif by tasks.registering(ReportMergeTask::class) {
  output.set(rootProject.layout.buildDirectory.file("reports/detekt/detekt.sarif"))
}

reportMergeXml {
  onlyIf { build.analysis.xmlReporting }
  input.from(tasks.withType<Detekt>().map { it.xmlReportFile })
}

reportMergeSarif {
  onlyIf { build.analysis.sarifReporting }
  input.from(tasks.withType<Detekt>().map { it.sarifReportFile })
}

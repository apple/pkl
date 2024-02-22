// https://youtrack.jetbrains.com/issue/KTIJ-19369
@file:Suppress("DSL_SCOPE_VIOLATION")

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  id("com.diffplug.spotless")
  id("org.jetbrains.kotlinx.kover")
  id("io.gitlab.arturbosch.detekt")
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

configure<SpotlessExtension> {
  kotlin {
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
    targetExclude("**/generated/**", "**/build/**")
    licenseHeaderFile(rootProject.file("build-logic/src/main/resources/license-header.star-block.txt"))
  }
}

configure<DetektExtension> {
  ignoreFailures = true
  parallel = true
  autoCorrect = build.analysis.autofix
  toolVersion = libs.versions.detekt.get()
  config.from(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
  baseline = project.layout.projectDirectory.file("detekt-baseline.xml").asFile
  buildUponDefaultConfig = true
  enableCompilerPlugin = true
}

tasks.withType<Detekt>().configureEach {
  isEnabled = build.analysis.enabled
  autoCorrect = build.analysis.autofix
  jvmTarget = build.jvm.lib.target.toString()

  reports {
    xml.required = build.analysis.xmlReporting
    sarif.required = build.analysis.sarifReporting
    html.required = build.analysis.htmlReporting
  }

  finalizedBy(reportMergeXml, reportMergeSarif)
}

val reportMergeXml by tasks.registering(ReportMergeTask::class) {
  onlyIf { build.analysis.xmlReporting }
  output.set(rootProject.layout.buildDirectory.file("reports/detekt/detekt.xml"))
  val detektTasks = tasks.withType<Detekt>()
  dependsOn(detektTasks)
  input.from(detektTasks.map { it.sarifReportFile })
}

val reportMergeSarif by tasks.registering(ReportMergeTask::class) {
  onlyIf { build.analysis.sarifReporting }
  output.set(rootProject.layout.buildDirectory.file("reports/detekt/detekt.sarif"))
  val detektTasks = tasks.withType<Detekt>()
  dependsOn(detektTasks)
  input.from(detektTasks.map { it.sarifReportFile })
}

tasks.compileTestJava {
  mustRunAfter(tasks.compileTestKotlin)
}

val javac: JavaCompile by tasks.named("compileJava", JavaCompile::class)
javac.apply {
  dependsOn(tasks.compileKotlin)
  mustRunAfter(tasks.compileKotlin)

  doFirst {
    Thread.sleep(1000)  // fix: wait for outputs to settle
  }
  options.compilerArgumentProviders.add(object : CommandLineArgumentProvider {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    val kotlinClasses = tasks.compileKotlin.get().destinationDirectory

    override fun asArguments() = listOf(
      "--patch-module", "${project.name.replace("-", ".")}=${kotlinClasses.get().asFile.absolutePath}"
    )
  })
}

tasks.jar.configure {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

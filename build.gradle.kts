// https://youtrack.jetbrains.com/issue/KTIJ-19369
@file:Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")

import org.jetbrains.gradle.ext.ActionDelegationConfig
import org.jetbrains.gradle.ext.ActionDelegationConfig.TestRunner.PLATFORM
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.TaskTriggersConfig

plugins {
  id("pklAllProjects")
  id("pklGraalVm")
  `project-reports`
  `test-report-aggregation`

  alias(libs.plugins.ideaExt)
  alias(libs.plugins.gradleDoctor) apply false
  alias(libs.plugins.jmh) apply false
  alias(libs.plugins.nexusPublish)
  alias(libs.plugins.versionCheck)
  alias(libs.plugins.owasp)
  alias(libs.plugins.sbom)
  alias(libs.plugins.kotlinValidator)
  alias(libs.plugins.kover)
  alias(libs.plugins.detekt)
}

group = "org.pkl-lang"
description = "Configuration that is Programmable, Scalable, and Safe"

nexusPublishing {
  repositories {
    sonatype {
      nexusUrl = uri("https://s01.oss.sonatype.org/service/local/")
      snapshotRepositoryUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
  }
}

val apiLockedProjects = listOf(
  projects.pklCli,
  projects.pklConfigKotlin,
  projects.pklGradle,
).map { it.name }

apiValidation {
  ignoredProjects = ignoredProjects.plus(allprojects.filter {
    it.name !in apiLockedProjects
  }.map { it.name }).toMutableSet()
}

idea {
  project {
    this as ExtensionAware
    configure<ProjectSettings> {
      this as ExtensionAware
      configure<ActionDelegationConfig> {
        delegateBuildRunToGradle = true
        testRunner = PLATFORM
      }
      configure<TaskTriggersConfig> {
        afterSync(provider { project(":pkl-core").tasks.named("makeIntelliJAntlrPluginHappy") })
      }
    }
  }
}

dependencies {
  listOf(
    projects.pklCli,
    projects.pklCodegenJava,
    projects.pklCodegenKotlin,
    projects.pklCommons,
    projects.pklCommonsCli,
    projects.pklConfigJava,
    projects.pklConfigKotlin,
    projects.pklDoc,
    projects.pklExecutor,
    projects.pklGradle,
    projects.pklServer,
    projects.pklTools,
  ).forEach {
    kover(it)
    testReportAggregation(it)
  }
}

val clean by tasks.registering(Delete::class) {
  delete(layout.buildDirectory)
}

val printVersion by tasks.registering {
  doFirst { println(buildInfo.pklVersion) }
}

val message = """
====
Gradle version : ${gradle.gradleVersion}
Java version   : ${System.getProperty("java.version")}
isParallel     : ${gradle.startParameter.isParallelProjectExecutionEnabled}
maxWorkerCount : ${gradle.startParameter.maxWorkerCount}
Architecture   : ${buildInfo.arch}

Project Version        : ${project.version}
Pkl Version            : ${buildInfo.pklVersion}
Pkl Non-Unique Version : ${buildInfo.pklVersionNonUnique}
Git Commit ID          : ${buildInfo.commitId}
====
"""

val formattedMessage = message.replace("\n====", "\n" + "=".repeat(message.lines().maxByOrNull { it.length }!!.length))
logger.info(formattedMessage)

detekt {
  toolVersion = libs.versions.detekt.get()
  config.from(layout.projectDirectory.file("config/detekt/detekt.yml"))
  buildUponDefaultConfig = true
  enableCompilerPlugin = true
}

dependencyCheck {
  nvd.apiKey = System.getenv("NVD_API_KEY")
}

val allTestsReport by reporting.reports.creating(AggregateTestReport::class) {
  testType = TestSuiteType.UNIT_TEST
}

val coverageReports by tasks.registering {
  dependsOn(tasks.koverBinaryReport, tasks.koverXmlReport, tasks.koverHtmlReport)
}

val reports by tasks.registering {
  description = "Generates all reports"
  group = "Reporting"

  dependsOn(tasks.named("allTestsReport"))
}

val check by tasks.registering {
  description = "Runs all checks"
  group = "Verification"

  finalizedBy(
    reports,
    coverageReports,
  )
}

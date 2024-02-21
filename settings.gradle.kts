@file:Suppress("UnstableApiUsage")

pluginManagement {
  includeBuild("build-logic")

  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

rootProject.name = "pkl"

include("bench")

include("docs")

include("stdlib")

include("pkl-cli")

include("pkl-codegen-java")

include("pkl-codegen-kotlin")

include("pkl-commons")

include("pkl-commons-cli")

include("pkl-commons-test")

include("pkl-config-java")

include("pkl-config-kotlin")

include("pkl-core")

include("pkl-doc")

include("pkl-gradle")

include("pkl-executor")

include("pkl-tools")

include("pkl-server")

plugins {
  id("com.gradle.enterprise") version "3.16.2"
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
  id("com.gradle.common-custom-user-data-gradle-plugin") version "1.12.1"
}

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  rulesMode = RulesMode.FAIL_ON_PROJECT_RULES

  repositories { mavenCentral() }
}

val javaVersion = JavaVersion.current()

require(javaVersion.isJava11Compatible) {
  "Project requires Java 11 or higher, but found ${javaVersion.majorVersion}."
}

if (
  gradle.startParameter.taskNames.contains("updateDependencyLocks") ||
    gradle.startParameter.taskNames.contains("uDL")
) {
  gradle.startParameter.isWriteDependencyLocks = true
}

for (prj in rootProject.children) {
  prj.buildFileName = "${prj.name}.gradle.kts"
}

buildCache {
  local {
    isEnabled = true
    removeUnusedEntriesAfterDays = 14
    directory = file("../.codebase/build-cache")
  }

  System.getenv("BUILDLESS_APIKEY")
    ?.ifBlank { null }
    ?.let { apiKey ->
      remote<HttpBuildCache> {
        isEnabled =
          extra.properties["remoteCache"] == "true" || !System.getenv("CI").isNullOrBlank()
        isPush = extra.properties["cachePush"] != "false" || !System.getenv("CI").isNullOrBlank()
        isUseExpectContinue = true
        url = uri("https://gradle.less.build/cache/generic")
        credentials {
          username = "apikey"
          password = apiKey
        }
      }
    }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

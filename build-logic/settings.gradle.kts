@file:Suppress("UnstableApiUsage")

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

// makes ~/.gradle/init.gradle unnecessary and ~/.gradle/gradle.properties optional
dependencyResolutionManagement {
  // use the same version catalog as the main build
  versionCatalogs {
    register("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }

  repositories {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    rulesMode = RulesMode.FAIL_ON_PROJECT_RULES

    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("com.gradle.enterprise") version "3.16.2"
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
  id("com.gradle.common-custom-user-data-gradle-plugin") version "1.12.1"
}

rootProject.name = "build-logic"

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

buildCache {
  local {
    isEnabled = true
    removeUnusedEntriesAfterDays = 14
    directory = file("../.codebase/build-cache")
  }

  System.getenv("BUILDLESS_API_KEY")?.ifBlank { null }?.let { apiKey ->
    remote<HttpBuildCache> {
      isEnabled = extra.properties["remoteCache"] == "true" || !System.getenv("CI").isNullOrBlank()
      isPush = extra.properties["cachePush"] != "false" || !System.getenv("CI").isNullOrBlank()
      url = uri("https://gradle.less.build/cache/generic")
      credentials {
        username = "apikey"
        password = apiKey
      }
    }
  }
}

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
  id("build.less") version "1.0.0-rc2"
  id("com.gradle.enterprise") version "3.16.2"
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
  id("com.gradle.common-custom-user-data-gradle-plugin") version "1.12.1"
}

rootProject.name = "build-logic"

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

buildless {
  localCache {
    enabled = true
  }

  remoteCache {
    enabled = extra.properties["remoteCache"] != "false"
    push.set(extra.properties["cachePush"] != "false")
  }
}

buildCache {
  local {
    isEnabled = true
    removeUnusedEntriesAfterDays = 14
    directory = file("../.codebase/build-cache")
  }
}

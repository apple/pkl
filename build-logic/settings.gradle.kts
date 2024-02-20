@file:Suppress("UnstableApiUsage")

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

// makes ~/.gradle/init.gradle unnecessary and ~/.gradle/gradle.properties optional
dependencyResolutionManagement {
  // use same version catalog as main build
  versionCatalogs {
    register("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }

  repositories {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    mavenCentral()
    gradlePluginPortal()
  }
}

rootProject.name = "build-logic"

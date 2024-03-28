rootProject.name = "pkl"

include("bench")
include("docs")
include("stdlib")

include("pkl-certs")
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

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositories {
    mavenCentral()
  }
}

if (gradle.startParameter.taskNames.contains("updateDependencyLocks") ||
  gradle.startParameter.taskNames.contains("uDL")
) {
  gradle.startParameter.isWriteDependencyLocks = true
}

for (prj in rootProject.children) {
  prj.buildFileName = "${prj.name}.gradle.kts"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

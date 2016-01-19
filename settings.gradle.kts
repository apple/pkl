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

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

// TODO: remove this when moving to the new CI
// makes ~/.gradle/init.gradle unnecessary and ~/.gradle/gradle.properties optional
dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
    // only use repositories specified here
    // https://github.com/gradle/gradle/issues/15732
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    maven {
      url = uri("https://artifacts.apple.com/libs-release")
      if (extra.has("artifactory_user")) {
        credentials {
          username = extra["artifactory_user"] as String
          password = extra["artifactory_password"] as String
        }
      }
      content {
        excludeGroup("org.pkl.staging")
      }
    }
    maven {
      name = "stagedMacExecutable"
      url = uri("https://artifacts.apple.com/pcl-modules-local/staging")
      // look for artifact but not POM (required configuration since Gradle 6.0)
      metadataSources {
        artifact()
      }
      content {
        includeGroup("org.pkl.staging")
      }
    }

    maven {
      name = "stagedLinuxExecutableAarch64"
      url = uri("https://artifacts.apple.com/pcl-modules-local/staging")
      // look for artifact but not POM (required configuration since Gradle 6.0)
      metadataSources {
        artifact()
      }
      content {
        includeGroup("com.apple.pkl.staging")
      }
    }
  }
}

val javaVersion = JavaVersion.current()
require(javaVersion.isJava11Compatible) {
  "Project requires Java 11 or higher, but found ${javaVersion.majorVersion}."
}

if (gradle.startParameter.taskNames.contains("updateDependencyLocks") ||
  gradle.startParameter.taskNames.contains("uDL")
) {
  gradle.startParameter.isWriteDependencyLocks = true
}

for (prj in rootProject.children) {
  prj.buildFileName = "${prj.name}.gradle.kts"
}

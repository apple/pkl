plugins {
  pklAllProjects
  pklJavaLibrary
  pklGradlePluginTest

  `java-gradle-plugin`
  `maven-publish`
}

dependencies {
  // Declare a `compileOnly` dependency on `project(":pkl-tools")`
  // to ensure correct code navigation in IntelliJ.
  compileOnly(project(":pkl-tools"))

  // Declare a `runtimeOnly` dependency on `project(":pkl-tools", "fatJar")`
  // to ensure that the published plugin 
  // (and also plugin tests, see the generated `plugin-under-test-metadata.properties`) 
  // only depends on the pkl-tools shaded fat JAR.
  // This avoids dependency version conflicts with other Gradle plugins.
  //
  // Hide this dependency from IntelliJ 
  // to prevent IntelliJ from reindexing the pkl-tools fat JAR after every build.
  // (IntelliJ gets everything it needs from the `compileOnly` dependency.)
  //
  // To debug shaded code in IntelliJ, temporarily remove the conditional.
  if (System.getProperty("idea.sync.active") == null) {
    runtimeOnly(project(":pkl-tools", "fatJar"))
  }

  testImplementation(project(":pkl-commons-test"))
}

publishing {
  publications {
    withType<MavenPublication>().configureEach {
      pom {
        url.set("https://github.com/pkl-lang/pkl/tree/dev/pkl-gradle")
        description.set("Gradle plugin for the Pkl configuration language.")
      }
    }
  }
}

gradlePlugin {
  plugins {
    create("pkl") {
      id = "org.pkl"
      implementationClass = "org.pkl.gradle.PklPlugin"
    }
  }
}

gradlePluginTests {
  // keep in sync with `PklPlugin.MIN_GRADLE_VERSION`
  minGradleVersion = GradleVersion.version("7.2")
  maxGradleVersion = GradleVersion.version("8.99")
  skippedGradleVersions = listOf()
}

/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
  pklAllProjects
  pklJavaLibrary
  pklGradlePluginTest

  `java-gradle-plugin`
  `maven-publish`
  pklPublishLibrary
  signing
}

dependencies {
  // Declare a `compileOnly` dependency on `projects.pklTools`
  // to ensure correct code navigation in IntelliJ.
  compileOnly(projects.pklTools)

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
    runtimeOnly(projects.pklTools) {
      attributes { attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED)) }
    }
  }

  testImplementation(projects.pklCommonsTest)
}

sourceSets {
  test {
    // Remove Gradle distribution JARs from test compile classpath.
    // This prevents a conflict between Gradle's and Pkl's Kotlin versions.
    compileClasspath = compileClasspath.filter { !(it.path.contains("dists")) }
  }
}

publishing {
  publications {
    withType<MavenPublication>().configureEach {
      pom {
        name.set("pkl-gradle plugin")
        url.set("https://github.com/apple/pkl/tree/main/pkl-gradle")
        description.set("Gradle plugin for the Pkl configuration language.")
      }
    }
  }
}

gradlePlugin {
  plugins {
    create("pkl") {
      id = "org.pkl-lang"
      implementationClass = "org.pkl.gradle.PklPlugin"
      displayName = "pkl-gradle"
      description = "Gradle plugin for interacting with Pkl"
    }
  }
}

gradlePluginTests {
  // keep in sync with `PklPlugin.MIN_GRADLE_VERSION`
  minGradleVersion = GradleVersion.version("8.1")
  maxGradleVersion = GradleVersion.version("8.99")
  skippedGradleVersions = listOf()
}

tasks.withType<Test>().configureEach {
  // Disable colour output in tests
  // Need additional disablement here because of how Jansi is packaged in the plugin
  systemProperty("org.pkl.thirdparty.jansiAnsi.disable", "true")
}

signing {
  publishing.publications.withType(MavenPublication::class.java).configureEach {
    if (name != "library") {
      sign(this)
    }
  }
}

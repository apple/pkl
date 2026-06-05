/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.gradle.api.file.ArchiveOperations
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
  id("pklAllProjects")
  id("pklJavaLibrary")
  id("pklGradlePluginTest")
  id("pklJSpecify")
  `java-gradle-plugin`
  `maven-publish`
  id("pklPublishLibrary")
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
  testImplementation(libs.wiremock)
}

sourceSets {
  test {
    // Remove Gradle distribution JARs from test compile classpath.
    // This prevents a conflict between Gradle's and Pkl's Kotlin versions.
    //
    // For some reason, IntelliJ import turns pklCommonsTest into a runtime dependency
    // if `compileClasspath` is filtered, causing "unresolved reference" errors in IntelliJ.
    // As a workaround, don't perform filtering for IntelliJ (import).
    if (System.getProperty("idea.sync.active") == null) {
      compileClasspath = compileClasspath.filter { !(it.path.contains("dists")) }
    }
  }
}

// Support for testing with a real external reader in tests - this builds an additional source set
// into a jar with a main class which provides a simple external reader implementation.
// Then the path to the jar file and the toolchain's `java` binary
// are injected into tests as properties.

val externalReader by sourceSets.creating {}

dependencies { "externalReaderImplementation"(libs.msgpack) }

val externalReaderJar by
  tasks.registering(Jar::class) {
    description = "Builds an external reader executable jar file"
    archiveBaseName = "external-reader"
    archiveVersion = ""

    // Package all dependencies into the jar (shadow plugin lite).
    val archiveOps = serviceOf<ArchiveOperations>()
    from(
      externalReader.runtimeClasspath.elements.map { locations ->
        locations.mapNotNull { location ->
          val f = location.asFile
          when {
            f.isDirectory -> f
            f.isFile -> archiveOps.zipTree(f)
            else -> null
          }
        }
      }
    )

    manifest { attributes("Main-Class" to "org.pkl.gradle.test.extreader.Main") }
  }

val externalReaderJarFile = externalReaderJar.flatMap { it.archiveFile }

val javaExecutablePath =
  javaToolchains.launcherFor(java.toolchain).map { it.executablePath.asFile.absolutePath }

tasks.test {
  dependsOn(externalReaderJar)
  // Currently the only way to inject system properties from lazy values in Gradle
  // is via `jvmArgumentProviders`.
  jvmArgumentProviders += CommandLineArgumentProvider {
    listOf(
      "-DpklGradle.externalReaderJar=" + externalReaderJarFile.get().asFile.absolutePath,
      "-DpklGradle.javaExecutable=" + javaExecutablePath.get(),
    )
  }
}

publishing {
  publications {
    withType<MavenPublication>().configureEach {
      pom {
        name = "pkl-gradle plugin"
        url = "https://github.com/apple/pkl/tree/main/pkl-gradle"
        description = "Gradle plugin for the Pkl configuration language."
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
  minGradleVersion = GradleVersion.version("8.2")
  maxGradleVersion = GradleVersion.version("9.99")
  skippedGradleVersions = listOf()
}

signing {
  publishing.publications.withType(MavenPublication::class.java).configureEach {
    if (name != "library") {
      sign(this)
    }
  }
}

// As of Gradle 8.10, the following is necessary to avoid a test compile error.
// (Apparently, gradle-api.jar now contains metadata that causes kotlinc to enforce Gradle's Kotlin
// version.)
// A more robust solution would be to port plugin tests to Java.
tasks.compileTestKotlin {
  compilerOptions { freeCompilerArgs.add("-Xskip-metadata-version-check") }
}

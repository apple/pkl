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
  pklFatJar
  pklPublishLibrary
  signing
}

val pklCodegenJava: Configuration by configurations.creating
val firstPartySourcesJars by configurations.existing

val generateTestConfigClasses by
  tasks.registering(JavaExec::class) {
    val outputDir = layout.buildDirectory.dir("testConfigClasses")
    outputs.dir(outputDir)
    inputs.dir("src/test/resources/codegenPkl")

    classpath = pklCodegenJava
    mainClass.set("org.pkl.codegen.java.Main")
    argumentProviders.add(
      CommandLineArgumentProvider {
        listOf("--output-dir", outputDir.get().asFile.path, "--generate-javadoc") +
          fileTree("src/test/resources/codegenPkl").map { it.path }
      }
    )
  }

tasks.processTestResources { dependsOn(generateTestConfigClasses) }

tasks.compileTestKotlin { dependsOn(generateTestConfigClasses) }

val bundleTests by tasks.registering(Jar::class) { from(sourceSets.test.get().output) }

// Runs unit tests using jar'd class files as a source.
// This is to test loading the ClassRegistry from within a jar, as opposed to directly from the file
// system.
val testFromJar by
  tasks.registering(Test::class) {
    dependsOn(bundleTests)

    testClassesDirs = files(tasks.test.get().testClassesDirs)

    classpath =
      // compiled test classes
      bundleTests.get().outputs.files +
        // fat Jar
        tasks.shadowJar.get().outputs.files +
        // test-only dependencies
        // (test dependencies that are also main dependencies must already be contained in fat Jar;
        // to verify that, we don't want to include them here)
        (configurations.testRuntimeClasspath.get() - configurations.runtimeClasspath.get())
  }

// TODO: the below snippet causes `./gradlew check` to fail specifically on
// `pkl-codegen-java:check`. Why?
// tasks.test {
//  dependsOn(testFromJar)
// }

sourceSets.getByName("test") {
  java.srcDir(layout.buildDirectory.dir("testConfigClasses/java"))
  resources.srcDir(layout.buildDirectory.dir("testConfigClasses/resources"))
}

dependencies {
  // "api" because ConfigEvaluator extends Evaluator
  api(projects.pklCore)

  implementation(libs.geantyref)

  testImplementation(libs.javaxInject)

  firstPartySourcesJars(project(":pkl-core", "sourcesJar"))

  pklCodegenJava(projects.pklCodegenJava)
}

tasks.shadowJar { archiveBaseName.set("pkl-config-java-all") }

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-config-java")
        description.set("Java config library based on the Pkl config language.")
      }
    }

    named<MavenPublication>("fatJar") {
      artifactId = "pkl-config-java-all"
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-config-java")
        description.set(
          "Shaded fat Jar for pkl-config-java, a Java config library based on the Pkl config language."
        )
      }
    }
  }
}

signing { sign(publishing.publications["fatJar"]) }

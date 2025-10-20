/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
  pklKotlinLibrary
  pklPublishLibrary
}

val pklConfigJava: Configuration by configurations.creating

val pklConfigJavaAll: Configuration by configurations.creating

val pklCodegenKotlin: Configuration by configurations.creating

val buildInfo = project.extensions.getByType<BuildInfo>()

dependencies {
  pklCodegenKotlin(projects.pklCodegenKotlin)
  implementation(libs.kotlinReflect)
  implementation(libs.msgpack)

  // Don't declare a runtime dependency to pkl-config-java because Gradle cannot resolve
  // the correct publication (library vs fatJar) when generating the POM.
  // We add the dependency manually to the POM later.
  //
  // Avoids this error during publish:
  //
  //  > Failed to query the value of property 'dependencies'.
  //  > Publishing is not able to resolve a dependency on a project with multiple publications that
  // have different coordinates.
  //  Found the following publications in project ':pkl-config-java':
  //  - Maven publication 'fatJar' with coordinates org.pkl-lang:pkl-config-java-all:0.30.0-SNAPSHOT
  //  - Maven publication 'library' with coordinates org.pkl-lang:pkl-config-java:0.30.0-SNAPSHOT
  compileOnly(projects.pklConfigJava)
  testImplementation(projects.pklConfigJava)
  testImplementation(libs.geantyref)
}

val generateTestConfigClasses by
  tasks.registering(JavaExec::class) {
    val outputDir = layout.buildDirectory.dir("testConfigClasses")
    outputs.dir(outputDir)
    inputs.dir("src/test/resources/codegenPkl")

    classpath = pklCodegenKotlin
    mainClass.set("org.pkl.codegen.kotlin.Main")
    argumentProviders.add(
      CommandLineArgumentProvider {
        listOf("--output-dir", outputDir.get().asFile.absolutePath) +
          fileTree("src/test/resources/codegenPkl").map { it.absolutePath }
      }
    )
  }

sourceSets.getByName("test") {
  java.srcDir(layout.buildDirectory.dir("testConfigClasses/kotlin"))
  resources.srcDir(layout.buildDirectory.dir("testConfigClasses/resources"))
}

tasks.processTestResources { dependsOn(generateTestConfigClasses) }

tasks.compileTestKotlin { dependsOn(generateTestConfigClasses) }

// disable publishing of .module until we find a way to manipulate it like POM (or ideally both
// together)
tasks.withType<GenerateModuleMetadata> { enabled = false }

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-config-kotlin")
        description.set(
          "Kotlin extensions for pkl-config-java, a Java config library based on the Pkl config language."
        )

        // Modify POM and add pkl-config-java-all dependency
        withXml {
          val dependenciesNode = asNode().get("dependencies") as groovy.util.NodeList
          val dependencies =
            if (dependenciesNode.isNotEmpty()) {
              dependenciesNode[0] as groovy.util.Node
            } else {
              asNode().appendNode("dependencies")
            }

          dependencies.appendNode("dependency").apply {
            appendNode("groupId", "org.pkl-lang")
            appendNode("artifactId", "pkl-config-java-all")
            appendNode("version", project.version)
            appendNode("scope", "runtime")
          }
        }
      }
    }
  }
}

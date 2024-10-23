/*
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
  pklKotlinLibrary
  pklPublishLibrary
}

dependencies {
  // CliJavaCodeGeneratorOptions exposes CliBaseOptions
  api(projects.pklCommonsCli)

  implementation(projects.pklCommons)
  implementation(projects.pklCore)
  implementation(libs.javaPoet)

  testImplementation(projects.pklConfigJava)
  testImplementation(projects.pklCommonsTest)
}

// with `org.gradle.parallel=true` and without the line below, `test` strangely runs into:
// java.lang.NoClassDefFoundError: Lorg/pkl/config/java/ConfigEvaluator;
// perhaps somehow related to InMemoryJavaCompiler?
tasks.test { mustRunAfter(":pkl-config-java:testFatJar") }

tasks.jar { manifest { attributes += mapOf("Main-Class" to "org.pkl.codegen.java.Main") } }

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-codegen-java")
        description.set(
          """
          Java source code generator that generates corresponding Java classes for Pkl classes,
          simplifying consumption of Pkl configuration as statically typed Java objects.
        """
            .trimIndent()
        )
      }
    }
  }
}

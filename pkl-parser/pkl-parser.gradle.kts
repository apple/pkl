/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
  pklPublishLibrary
  antlr
  idea
}

val generatorSourceSet = sourceSets.register("generator")

sourceSets { test { java { srcDir(file("testgenerated/antlr")) } } }

idea {
  module {
    // mark src/test/antlr as source dir
    // mark generated/antlr as generated source dir
    generatedSourceDirs = generatedSourceDirs + files("testgenerated/antlr")
    testSources.from(files("src/test/antlr", "testgenerated/antlr"))
  }
}

// workaround for https://github.com/gradle/gradle/issues/820
configurations.api.get().let { apiConfig ->
  apiConfig.setExtendsFrom(apiConfig.extendsFrom.filter { it.name != "antlr" })
}

dependencies {
  annotationProcessor(generatorSourceSet.get().runtimeClasspath)

  compileOnly(libs.jsr305)

  antlr(libs.antlr)

  testImplementation(projects.pklCommonsTest)
  testImplementation(libs.antlrRuntime)
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-parser")
        description.set("The parser for the Pkl language.")
      }
    }
  }
}

tasks.generateTestGrammarSource {
  maxHeapSize = "64m"

  // generate only visitor
  arguments = arguments + listOf("-visitor", "-no-listener")

  // Due to https://github.com/antlr/antlr4/issues/2260,
  // we can't put .g4 files into src/test/antlr/org/pkl/parser/antlr.
  // Instead, we put .g4 files into src/test/antlr, adapt output dir below,
  // and use @header directives in .g4 files (instead of setting `-package` argument here)
  // and task makeIntelliJAntlrPluginHappy to fix up the IDE story.
  outputDirectory = file("testgenerated/antlr/org/pkl/parser/antlr")
}

tasks.generateGrammarSource { enabled = false }

tasks.named("generateGeneratorGrammarSource") { enabled = false }

tasks.compileTestKotlin { dependsOn(tasks.generateTestGrammarSource) }

// Satisfy expectations of IntelliJ ANTLR plugin,
// which can't otherwise cope with our ANTLR setup.
val makeIntelliJAntlrPluginHappy by
  tasks.registering(Copy::class) {
    dependsOn(tasks.generateGrammarSource)
    into("test/antlr")
    from("testgenerated/antlr/org/pkl/parser/antlr") { include("PklLexer.tokens") }
  }

/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import gradle.kotlin.dsl.accessors._838481aba483a75943d3cbc72e5f5c7e.runtimeClasspath

// ideally we'd configure this automatically based on project dependencies
val firstPartySourcesJarsConfiguration: Configuration =
  configurations.maybeCreate("firstPartySourcesJars")

val resolveBundleSourcesJars =
  tasks.register<ResolveSourcesJars>("resolveBundleSourcesJars") {
    configuration.set(configurations.runtimeClasspath)
    outputDir.set(layout.buildDirectory.dir("resolveBundleSourcesJars"))
  }

val sourceBundle =
  tasks.register<BuildSourceBundle>("sourceBundle") {
    plugins.withId("pklJavaLibrary") { inputJars.from(tasks.named("sourcesJar")) }
    plugins.withId("pklKotlinLibrary") { inputJars.from(tasks.named("sourcesJar")) }
    inputJars.from(firstPartySourcesJarsConfiguration)
    inputJars.from(resolveBundleSourcesJars.map { fileTree(it.outputDir) })

    outputZip = layout.buildDirectory.file("${project.name}-${project.version}-sourcebundle.zip")
  }

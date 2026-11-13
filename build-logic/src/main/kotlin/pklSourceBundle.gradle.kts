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
plugins { id("pklAllProjects") }

// Sources jars of the first-party projects that this project depends on.
// Ideally we'd configure this automatically based on project dependencies.
val firstPartySourcesJars: Configuration = configurations.create("firstPartySourcesJars")

// Sources jars of third-party dependencies. Project dependencies are not covered by this; they
// are added to `firstPartySourcesJars` instead.
val resolveSourcesJars =
  tasks.register<ResolveSourcesJars>("resolveSourcesJars") {
    configuration.set(configurations.named("runtimeClasspath"))
    outputDir.set(layout.buildDirectory.dir("resolveSourcesJars"))
  }

val sourceBundleSpec = extensions.create<SourceBundleSpec>("sourceBundleSpec")

sourceBundleSpec.staticMuslLibc.convention(false)

plugins.withId("pklJavaLibrary") { sourceBundleSpec.firstPartyJars.from(tasks.named("sourcesJar")) }

plugins.withId("pklKotlinLibrary") {
  sourceBundleSpec.firstPartyJars.from(tasks.named("sourcesJar"))
}

sourceBundleSpec.firstPartyJars.from(firstPartySourcesJars)

sourceBundleSpec.dependencyJars.from(resolveSourcesJars.map { fileTree(it.outputDir) })

// Builds every source bundle of this project; there is one for each variant that the project is
// distributed as. The variants are registered by `pklJvmSourceBundle` and `pklNativeSourceBundle`.
tasks.register("sourceBundle") {
  group = "build"
  description = "Builds the source bundles of all variants of this project."
}

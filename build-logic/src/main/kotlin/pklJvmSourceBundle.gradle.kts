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
// Source bundle for the JVM variant of a project: its fat JAR, or its executable JAR.
//
// These contain the complete sources of all dependencies, because fat JARs contain the complete
// classes of all dependencies.
plugins { id("pklSourceBundle") }

val sourceBundleSpec = extensions.getByType<SourceBundleSpec>()

val sourceBundleJava =
  tasks.register<BuildSourceBundle>("sourceBundleJava") {
    group = "build"
    description = "Builds the source bundle of ${project.name}'s JVM variant."
    inputJars.from(sourceBundleSpec.firstPartyJars, sourceBundleSpec.dependencyJars)
    outputZip =
      layout.buildDirectory.file(
        "sourceBundle/${project.name}-${project.version}-java-sourcebundle.zip"
      )
  }

tasks.named("sourceBundle") { dependsOn(sourceBundleJava) }

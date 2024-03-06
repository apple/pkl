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
rootProject.name = "pkl"

include("bench")

include("docs")

include("stdlib")

include("pkl-cli")

include("pkl-codegen-java")

include("pkl-codegen-kotlin")

include("pkl-commons")

include("pkl-commons-cli")

include("pkl-commons-test")

include("pkl-config-java")

include("pkl-config-kotlin")

include("pkl-core")

include("pkl-doc")

include("pkl-gradle")

include("pkl-executor")

include("pkl-lsp")

include("pkl-tools")

include("pkl-server")

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

@Suppress("UnstableApiUsage") dependencyResolutionManagement { repositories { mavenCentral() } }

val javaVersion = JavaVersion.current()

require(javaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
  "Project requires Java 17 or higher, but found ${javaVersion.majorVersion}."
}

if (
  gradle.startParameter.taskNames.contains("updateDependencyLocks") ||
    gradle.startParameter.taskNames.contains("uDL")
) {
  gradle.startParameter.isWriteDependencyLocks = true
}

for (prj in rootProject.children) {
  prj.buildFileName = "${prj.name}.gradle.kts"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

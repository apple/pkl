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
plugins {
  id("pklAllProjects")
  id("pklJavaLibrary")
  alias(libs.plugins.protobuf)
  idea
}

dependencies {
  annotationProcessor(libs.truffleDslProcessor)
  implementation(projects.pklCore)
  implementation(libs.graalSdk)
  implementation(libs.truffleApi)
  implementation(libs.protobuf)
  implementation(libs.jspecify)
  implementation(libs.truffleProfiler)
}

// prevent `profile.proto` from being added to the classpath; doesn't provide any value
// (implicitly added by the protobuf plugin)
sourceSets.main { resources { exclude("**/*.proto") } }

idea.module { generatedSourceDirs.add(file("build/generated/sources/proto/main/java")) }

protobuf.protoc { artifact = libs.protoc.get().toString() }

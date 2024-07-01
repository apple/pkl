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
plugins { `kotlin-dsl` }

dependencies {
  implementation(libs.downloadTaskPlugin)
  implementation(libs.spotlessPlugin)
  implementation(libs.kotlinPlugin) { exclude(module = "kotlin-android-extensions") }
  implementation(libs.shadowPlugin)

  // fix from the Gradle team: makes version catalog symbols available in build scripts
  // see here for more: https://github.com/gradle/gradle/issues/15383
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin { target { compilations.configureEach { kotlinOptions { jvmTarget = "17" } } } }

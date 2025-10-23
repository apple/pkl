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
plugins { id("com.diffplug.spotless") }

val pklFormatter by configurations.creating

dependencies { pklFormatter(rootProject.project("pkl-formatter")) }

spotless {
  format("pkl") {
    target("**/*.pkl")
    addStep(PklFormatterStep(pklFormatter).create())
    licenseHeaderFile(
      rootProject.file("buildSrc/src/main/resources/license-header.line-comment.txt"),
      "/// ",
    )
  }
}

for (taskName in
  listOf("spotlessPkl", "spotlessPklApply", "spotlessPklCheck", "spotlessPklDiagnose")) {
  tasks.named(taskName) { dependsOn(":pkl-formatter:assemble") }
}

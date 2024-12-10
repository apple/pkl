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
package org.pkl.gradle;

/** Constant values used by tasks */
class TaskConstants {
  private TaskConstants() {
    /* no construction */
  }

  /** Gradle task group declared for code-gen tasks. */
  static final String TASK_GROUP_CODEGEN = "Code generation";

  /** Gradle task group declared for documentation tasks. */
  static final String TASK_GROUP_DOCS = "Documentation";

  /** Gradle task description for generating Java code from Pkl modules. */
  static final String GENERATE_JAVA_DESCRIPTION = "Generate Java code from Pkl modules";

  /** Gradle task description for generating Kotlin code from Pkl modules. */
  static final String GENERATE_KOTLIN_DESCRIPTION = "Generate Kotlin code from Pkl modules";

  /** Gradle task description for generating Pkldoc from Pkl modules. */
  static final String GENERATE_PKLDOC_DESCRIPTION = "Generate Pkldoc from Pkl modules";
}

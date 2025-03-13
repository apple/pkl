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
import org.gradle.api.provider.Property

abstract class ExecutableSpec {
  /** The main entrypoint Java class of the executable. */
  abstract val mainClass: Property<String>

  /**
   * The name of the native executable.
   *
   * Not required if not building a native executable.
   */
  abstract val name: Property<String>

  /** The name of the Java executable. */
  abstract val javaName: Property<String>

  /** The name of the executable that shows in the description when published to Maven. */
  abstract val documentationName: Property<String>

  /**
   * The base name of the Maven publication.
   *
   * This becomes the base name of the Artifact ID, with the os and arch suffixed.
   *
   * For example, `pkl` becomes `pkl-macos-aarch` for the macOS/aarch64 variant.
   */
  abstract val publicationName: Property<String>

  /** The name of the artifact ID for the Java executable. */
  abstract val javaPublicationName: Property<String>

  /** The website for this executable. */
  abstract val website: Property<String>
}

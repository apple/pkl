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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property

/**
 * The inputs that source bundles of a project are made of.
 *
 * This is configured by the `pklSourceBundle` plugin; the plugins that register the actual source
 * bundles (`pklJvmSourceBundle` and `pklNativeSourceBundle`) consume it.
 */
abstract class SourceBundleSpec {
  /** Sources jars of the project itself, and of the projects that it depends on. */
  abstract val firstPartyJars: ConfigurableFileCollection

  /** Sources jars of third-party dependencies, as resolved from the runtime classpath. */
  abstract val dependencyJars: ConfigurableFileCollection

  /**
   * Additional sources to include in source bundles of native targets (e.g. C sources of the
   * project itself).
   */
  abstract val extraNativeSources: ConfigurableFileCollection

  /**
   * Whether musl and zlib are statically linked into the project's native executable for musl
   * targets, in which case their sources are included in the source bundle of those targets.
   */
  abstract val staticMuslLibc: Property<Boolean>
}

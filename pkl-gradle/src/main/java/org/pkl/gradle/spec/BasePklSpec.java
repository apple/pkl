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
package org.pkl.gradle.spec;

import java.time.Duration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/** Configuration options shared between plugin features. Documented in user manual. */
public interface BasePklSpec {
  String getName();

  ConfigurableFileCollection getTransitiveModules();

  ListProperty<String> getAllowedModules();

  ListProperty<String> getAllowedResources();

  MapProperty<String, String> getEnvironmentVariables();

  MapProperty<String, String> getExternalProperties();

  ConfigurableFileCollection getModulePath();

  Property<Object> getSettingsModule();

  DirectoryProperty getEvalRootDir();

  DirectoryProperty getModuleCacheDir();

  Property<Boolean> getNoCache();

  // use same type (Duration) as Gradle's `Task.timeout`
  Property<Duration> getEvalTimeout();
}

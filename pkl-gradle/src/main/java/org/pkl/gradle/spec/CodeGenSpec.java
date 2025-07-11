/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;

/** Configuration options shared between code generators. Documented in user manual. */
public interface CodeGenSpec extends ModulesSpec {
  DirectoryProperty getOutputDir();

  Property<Boolean> getAddGeneratedAnnotation();

  Property<SourceSet> getSourceSet();

  Property<String> getIndent();

  Property<Boolean> getGenerateSpringBootConfig();

  Property<Boolean> getImplementSerializable();

  MapProperty<String, String> getRenames();
}

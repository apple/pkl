/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.pkl.gradle.spec.ProjectPackageSpec;
import org.pkl.gradle.spec.ProjectResolveSpec;

@SuppressWarnings("unused")
public interface PklProjectCommands {
  NamedDomainObjectContainer<ProjectPackageSpec> getPackagers();

  NamedDomainObjectContainer<ProjectResolveSpec> getResolvers();

  default void packagers(Action<? super NamedDomainObjectContainer<ProjectPackageSpec>> action) {
    action.execute(getPackagers());
  }

  default void resolvers(Action<? super NamedDomainObjectContainer<ProjectResolveSpec>> action) {
    action.execute(getResolvers());
  }
}

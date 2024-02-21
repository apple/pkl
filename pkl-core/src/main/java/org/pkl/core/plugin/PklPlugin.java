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
package org.pkl.core.plugin;

import java.util.Optional;

/** Describes the public SPI for Pkl engine plugins */
public interface PklPlugin {
  /**
   * @return Name or label to use for this plugin; shown in logs and other dev circumstances
   */
  String getName();

  /**
   * Determine whether this Pkl Plugin should run
   *
   * @param configuration Configuration context to determine eligibility
   * @return Whether the plug-in should run; defaults to `true`.
   */
  default Boolean isInConfiguration(PklPluginConfiguration configuration) {
    return true;
  }

  /**
   * Context event listener
   *
   * <p>Provides the optional {@link ContextEventListener} for a Pkl plugin.
   *
   * @return Context listener or {@link Optional#empty()}.
   */
  default Optional<ContextEventListener> contextEventListener() {
    return Optional.empty();
  }
}

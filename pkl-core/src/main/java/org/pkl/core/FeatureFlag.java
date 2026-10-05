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
package org.pkl.core;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

public enum FeatureFlag {
  ; // no feature flags yet!

  // keep in sync with pkl.EvaluatorSettings#KnownFeatureFlags

  // if the stdlib needs to be eval'd with different flags than the defaults, edit
  // prg.pkl.core.runtime.StdLibModule.stdLibFeatureFlags

  FeatureFlag(boolean defaultValue) {
    this.defaultValue = defaultValue;
  }

  public static @Nullable FeatureFlag parse(String name) {
    try {
      return FeatureFlag.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private final boolean defaultValue;

  public boolean defaultValue() {
    return defaultValue;
  }
}

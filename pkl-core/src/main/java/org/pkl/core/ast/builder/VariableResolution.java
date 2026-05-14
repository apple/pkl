/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.builder;

import org.pkl.core.ast.VmModifier;

public sealed interface VariableResolution {

  record LexicalProperty(boolean isModuleScope, int modifiers, int levelsUp)
      implements VariableResolution {

    public boolean isLocal() {
      return VmModifier.isLocal(modifiers);
    }

    public boolean isAmbiguousLocality() {
      return VmModifier.isAmbiguousLocality(modifiers);
    }
  }

  // let, lambda, object body param
  record Parameter(int slot, int levelsUp) implements VariableResolution {}

  record ForGeneratorVariable(int slot, int levelsUp) implements VariableResolution {}

  // Implicit base module lookup
  record ImplicitBaseProperty() implements VariableResolution {}

  record ImplicitThisProperty() implements VariableResolution {}
}

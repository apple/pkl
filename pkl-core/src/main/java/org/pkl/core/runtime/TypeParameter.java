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
package org.pkl.core.runtime;

import java.util.List;
import org.pkl.core.TypeParameter.Variance;

public record TypeParameter(Variance variance, String name, int index) {
  public org.pkl.core.TypeParameter export() {
    return new org.pkl.core.TypeParameter(variance, name, index);
  }

  public static List<org.pkl.core.TypeParameter> export(List<TypeParameter> typeParameters) {
    return typeParameters.stream().map(TypeParameter::export).toList();
  }

  public enum OwnerType {
    CLASS,
    METHOD,
    TYPEALIAS
  }
}

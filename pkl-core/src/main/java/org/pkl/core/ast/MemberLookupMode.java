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
package org.pkl.core.ast;

public enum MemberLookupMode {
  /** Lookup of a local member in the lexical scope. */
  IMPLICIT_LOCAL,

  /** Lookup of a non-local member in the lexical scope. */
  IMPLICIT_LEXICAL,

  /** Member lookup whose implicit receiver is the {@code pkl.base} module. */
  IMPLICIT_BASE,

  /** Member lookup whose implicit receiver is {@code this}. */
  IMPLICIT_THIS,

  /** Member lookup with explicit receiver (e.g., {@code foo.bar}). */
  EXPLICIT_RECEIVER
}

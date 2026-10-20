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
package org.pkl.parser;

/** Limits that keep the depth of parsed syntax trees bounded. */
final class ParserLimits {
  private ParserLimits() {}

  /** Total depth budget. */
  static final int MAX_DEPTH = 1000;

  /** Cost of one level of nesting. Allows {@code MAX_DEPTH / NESTING_COST} levels. */
  static final int NESTING_COST = 20;

  /** Cost of one link in a chain of operators or accesses. */
  static final int CHAIN_LINK_COST = 8;
}

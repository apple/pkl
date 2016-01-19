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
package org.pkl.core;

/** Renders Pkl values in some output format. */
public interface ValueRenderer {
  /**
   * Renders the given value as a complete document.
   *
   * <p>Some renderers impose restrictions on which types of values can be rendered as document.
   *
   * <p>A typical implementation of this method renders a document header/footer and otherwise
   * delegates to {@link #renderValue}.
   */
  void renderDocument(Object value);

  /** Renders the given value. */
  void renderValue(Object value);
}

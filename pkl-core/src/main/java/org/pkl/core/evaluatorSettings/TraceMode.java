/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.evaluatorSettings;

/** Dictates the rendering of calls to the trace() method within Pkl. */
public enum TraceMode {
  /** All trace() calls will not be emitted to stderr. */
  HIDDEN,
  /** All structures passed to trace() will be emitted on a single line. */
  DEFAULT,
  /** All structures passed to trace() will be indented and emitted across multiple lines. */
  PRETTY
}

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
package org.pkl.core.util.paguro.indent;

/** Created by gpeterso on 5/21/17. */
public interface Indented {
  /**
   * Returns a string where line breaks extend the given amount of indentation.
   *
   * @param indent the amount of indent to start at. Pretty-printed subsequent lines may have
   *     additional indent.
   * @return a string with the given starting offset (in spaces) for every line.
   */
  String indentedStr(int indent);
}

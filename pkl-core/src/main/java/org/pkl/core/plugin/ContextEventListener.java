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

import org.graalvm.polyglot.Context;

/** Listener interface for pluggable context and execution controls */
public interface ContextEventListener {
  /**
   * Context creation event
   *
   * <p>This hook is dispatched right before the {@link Context} is built for execution; plug-ins
   * providing logic at this stage have a chance to customize the context builder before it is used.
   *
   * @param builder Context which is being constructed
   */
  default void onCreateContext(@SuppressWarnings("unused") Context.Builder builder) {
    /* no-op */
  }
}

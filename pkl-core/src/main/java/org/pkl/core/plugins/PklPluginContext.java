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
package org.pkl.core.plugins;

import org.pkl.core.plugin.PklPlugin;

/**
 * Context API for the execution of a Pkl Plugin event
 *
 * <p>This interface is provided to each execution of a Pkl Plugin's event pipeline, so that it may
 * declare errors, log messages, and so on
 */
public interface PklPluginContext<Plugin extends PklPlugin> {
  /**
   * Build a new Pkl plugin execution context
   *
   * @param plugin Plug-in under dispatch
   * @return Context to pass to an event dispatch
   * @param <P> Plug-in type
   */
  static <P extends PklPlugin> PklPluginContext<P> of(P plugin) {
    return () -> plugin;
  }

  /**
   * @return Plug-in under dispatch
   */
  Plugin getPlugin();
}

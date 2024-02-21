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

import java.util.function.Function;
import java.util.stream.Stream;
import org.pkl.core.plugin.PklPlugin;

/** Manager interface for loading and executing plugins */
public interface PluginManager {
  /** Default plug-in manager. */
  PluginManager DEFAULT = new GlobalPluginManagerImpl();

  /**
   * Acquire an instance of the plugin manager
   *
   * @return Plug-in manager
   */
  static PluginManager acquire() {
    return DEFAULT;
  }

  /**
   * Resolve all installed plug-ins available to service loader
   *
   * <p>Implementations are allowed the space to cache the result of this call, especially in AOT
   * circumstances.
   *
   * @return Installed suite of Pkl engine plug-ins
   */
  default Stream<PklPlugin> installedPlugins() {
    return Stream.empty();
  }

  /**
   * Resolve all installed plug-ins available to service loader
   *
   * <p>Implementations are allowed the space to cache the result of this call, especially in AOT
   * circumstances.
   *
   * @param event Event to dispatch to listening plugins
   * @param exec Function which dispatches the event for a given plug-in, to produce a result
   * @return Stream of event dispatch results, aligned with the list returned by {@link
   *     #installedPlugins()}
   */
  default Stream<EventResult> dispatchEvent(
      PluginEvent<?, ?> event, Function<PklPluginContext<? extends PklPlugin>, EventResult> exec) {
    return Stream.empty();
  }
}

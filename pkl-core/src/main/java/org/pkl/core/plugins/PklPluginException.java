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
import org.pkl.core.plugin.PklPluginError;

/** Wraps a caught exception which was encountered while executing a plug-in hook */
public class PklPluginException<
    Plugin extends PklPlugin, Err extends Enum<Err>, Exc extends Throwable & PklPluginError<Err>> {
  // Private (use static methods).
  private PklPluginException(Plugin plugin, PklPluginError<Err> err) {
    this.err = err;
    this.plugin = plugin;
  }

  // Plugin where the error happened.
  private final Plugin plugin;

  // Held error type.
  private final PklPluginError<Err> err;

  /**
   * Build a Pkl plugin exception wrapping the provided inputs
   *
   * @param err Error to wrap
   * @return Wrapped error
   * @param <Plugin> Plugin type
   * @param <Err> Error type
   * @param <Exc> Exception type
   */
  static <
          Plugin extends PklPlugin,
          Err extends Enum<Err>,
          Exc extends Throwable & PklPluginError<Err>>
      PklPluginException<Plugin, Err, Exc> wrapping(Plugin plugin, PklPluginError<Err> err) {
    return new PklPluginException<>(plugin, err);
  }

  /**
   * @return Held plugin error
   */
  PklPluginError<Err> getErr() {
    return err;
  }

  /**
   * @return The plug-in where the error took place
   */
  Plugin getPlugin() {
    return this.plugin;
  }
}

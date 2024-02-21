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

import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.graalvm.nativeimage.ImageInfo;
import org.pkl.core.Logger;
import org.pkl.core.Loggers;
import org.pkl.core.plugin.PklPlugin;
import org.pkl.core.plugin.PklPluginConfiguration;
import org.pkl.core.plugin.PklPluginError;
import org.pkl.core.runtime.VmInfo;

/** Default implementation of {@link PluginManager}. */
class GlobalPluginManagerImpl implements PluginManager {
  private final Logger logger = Loggers.logger(GlobalPluginManagerImpl.class);
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final ConcurrentMap<String, PklPlugin> pluginMap = new ConcurrentSkipListMap<>();

  protected GlobalPluginManagerImpl() {
    /* protected construction */
  }

  // Plug-in loader
  private final ServiceLoader<PklPlugin> loader = ServiceLoader.loadInstalled(PklPlugin.class);

  // Log about a plug-in event, if configured to do so.
  private void maybeLog(
      PklPluginContext<? extends PklPlugin> pluginContext, PluginEvent<?, ?> event) {
    if (logger.isLevelEnabled(Level.FINE)) {
      String msg =
          "Dispatching event type '"
              + event.type().name()
              + "' via plugin '"
              + pluginContext.getPlugin().getName()
              + "'";
      logger.debug(msg);
    }
  }

  private void loadAllPlugins() {
    initialized.compareAndSet(false, true);
    var configuration =
        new PklPluginConfiguration() {
          @Override
          public String getPklVersion() {
            return VmInfo.PKL_CORE_VERSION;
          }

          @Override
          public Boolean isNative() {
            return ImageInfo.inImageCode();
          }
        };

    synchronized (this) {
      loader.stream()
          .map(Provider::get)
          .filter(plugin -> plugin.isInConfiguration(configuration))
          .forEach(
              plugin -> {
                pluginMap.put(plugin.getName(), plugin);
              });
    }
  }

  @Override
  public Stream<PklPlugin> installedPlugins() {
    if (!initialized.get()) {
      loadAllPlugins();
    }
    return pluginMap.values().stream();
  }

  @Override
  public Stream<EventResult> dispatchEvent(
      PluginEvent<?, ?> event, Function<PklPluginContext<? extends PklPlugin>, EventResult> exec) {
    // resolve each installed plugin
    return installedPlugins()
        .map(
            pklPlugin -> {
              // and, for each, prepare an event context...
              var context = PklPluginContext.of(pklPlugin);

              try {
                maybeLog(context, event);
                return exec.apply(context);
              } catch (Throwable err) {
                if (err instanceof PklPluginError<?>) {
                  return EventResult.failure(
                      PklPluginException.wrapping(pklPlugin, (PklPluginError<?>) err));
                } else {
                  String msg =
                      "PklPlugin '"
                          + pklPlugin.getName()
                          + "' failed with unexpected exception of type '"
                          + err.getClass().getName()
                          + "'. Normally, this exception would be swallowed, but assertions are on.";
                  assert false : msg;
                }
              }
              return EventResult.failure();
            });
  }
}

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
package org.pkl.core.runtime;

import java.util.function.Supplier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.pkl.core.plugins.EventResult;
import org.pkl.core.plugins.PluginEvent;
import org.pkl.core.plugins.PluginEventInfo.ContextCreate;
import org.pkl.core.plugins.PluginEventType;
import org.pkl.core.plugins.PluginManager;

class VmEngineManager implements Supplier<Context.Builder> {
  // Manager for Pkl plugin dispatch.
  private final PluginManager pluginManager;

  VmEngineManager(PluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  private static final Engine PKL_ENGINE =
      Engine.newBuilder("pkl").option("engine.WarnInterpreterOnly", "false").build();

  @Override
  public Context.Builder get() {
    var builder = Context.newBuilder("pkl").engine(PKL_ENGINE);

    var payload = ContextCreate.of(builder);
    var event = PluginEvent.ofType(PluginEventType.CONTEXT_CREATE, payload);
    pluginManager.dispatchEvent(
        event,
        ctx -> {
          ctx.getPlugin()
              .contextEventListener()
              .ifPresent(
                  listener -> {
                    listener.onCreateContext(builder);
                  });
          return EventResult.success();
        });
    return builder;
  }
}

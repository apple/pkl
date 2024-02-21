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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;

/** Inner plug-in event info type */
public interface PluginEventInfo<Info> {
  /**
   * @return Data held by this plug-in event info
   */
  Info data();

  /** Event shape for a VM context creation event */
  public class ContextCreate implements PluginEventInfo<Context.Builder> {
    private final Context.Builder builder;

    ContextCreate(Context.Builder builder) {
      this.builder = builder;
    }

    public static ContextCreate of(Context.Builder builder) {
      return new ContextCreate(builder);
    }

    @Override
    public Builder data() {
      return this.builder;
    }
  }
}

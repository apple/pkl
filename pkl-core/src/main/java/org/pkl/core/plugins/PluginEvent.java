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

/** Describes the concept of an event which plug-ins are notified about */
public interface PluginEvent<Data, Payload extends PluginEventInfo<Data>> {
  /**
   * @return Type enumeration for this event
   */
  PluginEventType type();

  /**
   * @return Payload for this event
   */
  Payload event();

  /**
   * Wrap the provided plugin event type and data payload
   *
   * @param type Plugin event type
   * @param data Plugin event data
   * @return Wrapped event info
   * @param <Payload> Payload type expected
   */
  static <D, Payload extends PluginEventInfo<D>> PluginEvent<D, Payload> ofType(
      PluginEventType type, Payload data) {
    return new PluginEvent<>() {
      @Override
      public PluginEventType type() {
        return type;
      }

      @Override
      public Payload event() {
        return data;
      }
    };
  }
}

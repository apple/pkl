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
import org.pkl.core.util.Nullable;

/** Describes the result of executing a single event within the context of a Pkl Plugin. */
public interface EventResult {
  EventResult SUCCESS = () -> true;
  EventResult FAILURE = () -> false;

  /**
   * @return Whether the event dispatch finished successfully
   */
  boolean isOk();

  /**
   * @return Exception that was caught; defaults to `null`
   */
  default @Nullable PklPluginException<? extends PklPlugin, ?, ?> getException() {
    return null;
  }

  /**
   * @return Optional failure message provided by the plug-in; defaults to `null`
   */
  default @Nullable String message() {
    return null;
  }

  /**
   * Shortcut to indicate a successful event dispatch
   *
   * @return Successful event dispatch marker
   */
  static EventResult success() {
    return SUCCESS;
  }

  /**
   * Shortcut to indicate a failed event dispatch
   *
   * @return Failed event dispatch marker
   */
  static EventResult failure() {
    return FAILURE;
  }

  /**
   * Shortcut to indicate a failed event dispatch
   *
   * @return Failed event dispatch marker
   */
  static EventResult failure(@Nullable String message) {
    if (message != null && !message.isEmpty()) {
      return new EventResult() {
        @Override
        public boolean isOk() {
          return false;
        }

        @Override
        public String message() {
          return message;
        }
      };
    }
    return FAILURE;
  }

  /**
   * Shortcut to indicate a failed event dispatch from a formal exception
   *
   * @return Failed event dispatch marker
   */
  static EventResult failure(PklPluginException<? extends PklPlugin, ?, ?> err) {
    return new EventResult() {
      @Override
      public boolean isOk() {
        return false;
      }

      @Override
      public @Nullable String message() {
        return err.toString();
      }

      @Override
      public PklPluginException<? extends PklPlugin, ?, ?> getException() {
        return err;
      }
    };
  }
}

/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core;

import java.io.PrintStream;
import java.io.PrintWriter;

/** Predefined {@link Logger}s. */
@SuppressWarnings("unused")
public final class Loggers {
  private Loggers() {}

  /** Returns a logger that discards log messages. */
  public static Logger noop() {
    return new Logger() {
      @Override
      public void trace(String message, String frameUri) {
        // do nothing
      }

      @Override
      public void warn(String message, String frameUri) {
        // do nothing
      }
    };
  }

  /** Returns a logger that sends log messages to standard error. */
  public static Logger stdErr() {
    return stream(System.err);
  }

  /** Returns a logger that sends log messages to the given stream. */
  @SuppressWarnings("DuplicatedCode")
  public static Logger stream(PrintStream stream) {
    return new Logger() {
      @Override
      public void trace(String message, String frameUri) {
        stream.println(formatMessage("TRACE", message, frameUri));
        stream.flush();
      }

      @Override
      public void warn(String message, String frameUri) {
        stream.println(formatMessage("WARN", message, frameUri));
        stream.flush();
      }
    };
  }

  /** Returns a logger that sends log messages to the given writer. */
  @SuppressWarnings("DuplicatedCode")
  public static Logger writer(PrintWriter writer) {
    return new Logger() {
      @Override
      public void trace(String message, String frameUri) {
        writer.println(formatMessage("TRACE", message, frameUri));
        writer.flush();
      }

      @Override
      public void warn(String message, String frameUri) {
        writer.println(formatMessage("WARN", message, frameUri));
        writer.flush();
      }
    };
  }

  private static String formatMessage(String level, String message, String frameUri) {
    return "pkl: "
        + level
        + ": "
        + message
        + (message.endsWith("\n") ? "" : " ")
        + "("
        + frameUri
        + ')';
  }
}

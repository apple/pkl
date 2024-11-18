/*
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
package org.pkl.core;

/** A logger that keeps messages locally and can return them. */
public final class BufferedLogger implements Logger {

  private final StringBuilder builder = new StringBuilder();
  private final Logger logger;

  public BufferedLogger(Logger logger) {
    this.logger = logger;
  }

  @Override
  public void trace(String message, StackFrame frame) {
    builder.append(message).append("\n");
    logger.trace(message, frame);
  }

  @Override
  public void warn(String message, StackFrame frame) {
    builder.append(message).append("\n");
    logger.warn(message, frame);
  }

  public void clear() {
    builder.setLength(0);
  }

  public String getLogs() {
    return builder.toString();
  }
}

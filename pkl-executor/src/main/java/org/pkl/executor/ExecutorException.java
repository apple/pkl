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
package org.pkl.executor;

import java.util.Objects;

/**
 * Indicates an {@link Executor} error. {@link #getMessage()} returns a user-facing error message.
 */
public final class ExecutorException extends RuntimeException {
  private final String pklVersion;

  public ExecutorException(String message) {
    super(message);
    pklVersion = null;
  }

  public ExecutorException(String message, Throwable cause) {
    super(message, cause);
    pklVersion = null;
  }

  public ExecutorException(String message, Throwable cause, String version) {
    super(message, cause);
    pklVersion = Objects.requireNonNull(version);
  }

  /**
   * The selected Pkl version used to evaluate the module.
   *
   * <p>Returns {@code null} if this exception does not originate from an underlying Pkl evaluator.
   */
  public String getPklVersion() {
    return pklVersion;
  }

  @Override
  public String getMessage() {
    var message = super.getMessage();
    if (pklVersion == null) {
      return message;
    }
    return message + "\nPkl version: " + pklVersion;
  }
}

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
package org.pkl.core.repl;

import java.util.*;

public abstract class ReplResponse {
  private final String message;

  private ReplResponse(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  public static final class Completion extends ReplResponse {
    public static final Completion EMPTY = new Completion(List.of());

    public final Collection<String> members;

    public Completion(Collection<String> members) {
      super("");
      this.members = members;
    }

    public String toString() {
      return String.format("%s(members=%s)", getClass().getSimpleName(), members);
    }
  }

  public static final class EvalSuccess extends ReplResponse {
    public EvalSuccess(String message) {
      super(message);
    }

    public String getResult() {
      return getMessage();
    }

    public String toString() {
      return String.format("%s(result=%s)", getClass().getSimpleName(), getResult());
    }
  }

  public static final class EvalError extends ReplResponse {
    public EvalError(String message) {
      super(message);
    }

    public String toString() {
      return String.format("%s(message=%s)", getClass().getSimpleName(), getMessage());
    }
  }

  public static final class IncompleteInput extends ReplResponse {
    public IncompleteInput(String message) {
      super(message);
    }

    public String toString() {
      return String.format("%s(message=%s)", getClass().getSimpleName(), getMessage());
    }
  }

  public static final class InvalidRequest extends ReplResponse {
    public InvalidRequest(String message) {
      super(message);
    }

    public String toString() {
      return String.format("%s(message=%s)", getClass().getSimpleName(), getMessage());
    }
  }

  public static final class InternalError extends ReplResponse {
    private final Throwable cause;

    public InternalError(Throwable cause) {
      super(cause.toString());
      this.cause = cause;
    }

    public Throwable getCause() {
      return cause;
    }

    public String toString() {
      return String.format("%s(cause=%s)", getClass().getSimpleName(), cause);
    }
  }
}

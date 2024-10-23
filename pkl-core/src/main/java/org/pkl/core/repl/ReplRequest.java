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
package org.pkl.core.repl;

import java.net.URI;

public abstract class ReplRequest {
  public final String id;

  private ReplRequest(String id) {
    if (id.isEmpty()) {
      throw new IllegalArgumentException("Request ID must be empty.");
    }
    this.id = id;
  }

  /** Requests evaluation of REPL input. */
  public static final class Eval extends ReplRequest {
    public final String text;
    public final boolean evalDefinitions;
    public final boolean forceResults;

    public Eval(String id, String text, boolean evalDefinitions, boolean forceResults) {
      super(id);
      this.text = text;
      this.evalDefinitions = evalDefinitions;
      this.forceResults = forceResults;
    }

    public String toString() {
      return String.format(
          "%s(text=%s,evalDefinitions=%s,forceResults=%s)",
          getClass().getSimpleName(), text, evalDefinitions, forceResults);
    }
  }

  /** Requests loading of a module. */
  public static final class Load extends ReplRequest {
    public final URI uri;

    public Load(String id, URI uri) {
      super(id);
      this.uri = uri;
    }

    public String toString() {
      return String.format("%s(url=%s)", getClass().getSimpleName(), uri);
    }
  }

  public static final class Completion extends ReplRequest {
    public final String text;

    public Completion(String id, String text) {
      super(id);
      this.text = text;
    }

    public String toString() {
      return String.format("%s(text=%s)", getClass().getSimpleName(), text);
    }
  }

  public static final class Reset extends ReplRequest {
    public Reset(String id) {
      super(id);
    }

    public String toString() {
      return String.format("%s()", getClass().getSimpleName());
    }
  }
}

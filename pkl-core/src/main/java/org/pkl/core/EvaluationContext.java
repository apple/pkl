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

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable settings for a single evaluation, created with {@link #builder()}.
 *
 * <p>External properties and environment variables override the evaluator's configured values for
 * matching names. Other configured values remain available. Each evaluation with a context uses
 * fresh module and resource caches, even when the context is empty or reused.
 *
 * @since 0.32.0
 */
public final class EvaluationContext {
  private final Map<String, String> externalProperties;
  private final Map<String, String> environmentVariables;

  private EvaluationContext(Builder builder) {
    externalProperties = Map.copyOf(builder.externalProperties);
    environmentVariables = Map.copyOf(builder.environmentVariables);
  }

  /** Returns a builder with no overrides. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns the external property overrides. */
  public Map<String, String> externalProperties() {
    return externalProperties;
  }

  /** Returns the environment variable overrides. */
  public Map<String, String> environmentVariables() {
    return environmentVariables;
  }

  /** Builds an immutable evaluation context. */
  public static final class Builder {
    private final Map<String, String> externalProperties = new HashMap<>();
    private final Map<String, String> environmentVariables = new HashMap<>();

    private Builder() {}

    /** Adds an external property, overriding any value previously set for this name. */
    public Builder addExternalProperty(String name, String value) {
      externalProperties.put(name, value);
      return this;
    }

    /** Adds external properties, overriding any values previously set for these names. */
    public Builder addExternalProperties(Map<String, String> properties) {
      externalProperties.putAll(properties);
      return this;
    }

    /** Adds an environment variable, overriding any value previously set for this name. */
    public Builder addEnvironmentVariable(String name, String value) {
      environmentVariables.put(name, value);
      return this;
    }

    /** Adds environment variables, overriding any values previously set for these names. */
    public Builder addEnvironmentVariables(Map<String, String> variables) {
      environmentVariables.putAll(variables);
      return this;
    }

    /** Returns an immutable snapshot of this builder's settings. */
    public EvaluationContext build() {
      return new EvaluationContext(this);
    }
  }
}

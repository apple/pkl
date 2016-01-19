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
package org.pkl.config.java;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.pkl.config.java.mapper.ValueMapperBuilder;
import org.pkl.core.EvaluatorBuilder;
import org.pkl.core.SecurityManager;
import org.pkl.core.StackFrameTransformer;
import org.pkl.core.project.DeclaredDependencies;
import org.pkl.core.project.Project;
import org.pkl.core.util.Nullable;

/** A builder for {@link ConfigEvaluator}s. */
@SuppressWarnings({"UnusedReturnValue", "unused"})
public final class ConfigEvaluatorBuilder {
  private EvaluatorBuilder evaluatorBuilder;
  private ValueMapperBuilder mapperBuilder;

  private ConfigEvaluatorBuilder(
      EvaluatorBuilder evaluatorBuilder, ValueMapperBuilder mapperBuilder) {
    this.evaluatorBuilder = evaluatorBuilder;
    this.mapperBuilder = mapperBuilder;
  }

  /** Creates a builder with preconfigured module evaluator and value mapper builders. */
  public static ConfigEvaluatorBuilder preconfigured() {
    return new ConfigEvaluatorBuilder(
        EvaluatorBuilder.preconfigured(), ValueMapperBuilder.preconfigured());
  }

  /** Creates a builder with unconfigured module evaluator and value mapper builders. */
  public static ConfigEvaluatorBuilder unconfigured() {
    return new ConfigEvaluatorBuilder(
        EvaluatorBuilder.unconfigured(), ValueMapperBuilder.unconfigured());
  }

  /**
   * Sets the underlying module evaluator builder. When a config evaluator is built, the underlying
   * module evaluator comes from this builder.
   */
  public ConfigEvaluatorBuilder setEvaluatorBuilder(EvaluatorBuilder evaluatorBuilder) {
    this.evaluatorBuilder = evaluatorBuilder;
    return this;
  }

  /** Returns the currently set module evaluator builder. */
  public EvaluatorBuilder getEvaluatorBuilder() {
    return evaluatorBuilder;
  }

  /**
   * Sets the underlying value mapper builder. When a config evaluator is built, the underlying
   * value mapper comes from this builder.
   */
  public ConfigEvaluatorBuilder setValueMapperBuilder(ValueMapperBuilder mapperBuilder) {
    this.mapperBuilder = mapperBuilder;
    return this;
  }

  /** Returns the currently set value mapper builder. */
  public ValueMapperBuilder getValueMapperBuilder() {
    return mapperBuilder;
  }

  /**
   * Adds the given environment variable, overriding any environment variable previously added under
   * the same name.
   *
   * <p>Modules can read environment variables with {@code read("env:<NAME>")}.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public ConfigEvaluatorBuilder addEnvironmentVariable(String name, String value) {
    evaluatorBuilder.addEnvironmentVariable(name, value);
    return this;
  }

  /**
   * Adds the given environment variables, overriding any environment variables previously added
   * under the same name.
   *
   * <p>Modules can read environment variables with {@code read("env:<NAME>")}.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public ConfigEvaluatorBuilder addEnvironmentVariables(Map<String, String> envVars) {
    evaluatorBuilder.addEnvironmentVariables(envVars);
    return this;
  }

  /**
   * Removes any existing environment variables, then adds the given environment variables.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public ConfigEvaluatorBuilder setEnvironmentVariables(Map<String, String> envVars) {
    evaluatorBuilder.setEnvironmentVariables(envVars);
    return this;
  }

  /**
   * Returns the currently set environment variables.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public Map<String, String> getEnvironmentVariables() {
    return evaluatorBuilder.getEnvironmentVariables();
  }

  /**
   * Adds the given external property, overriding any property previously set under the same name.
   *
   * <p>Modules can read external properties with {@code read("prop:<name>")}.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public ConfigEvaluatorBuilder addExternalProperty(String name, String value) {
    evaluatorBuilder.addExternalProperty(name, value);
    return this;
  }

  /**
   * Adds the given external properties, overriding any properties previously set under the same
   * name.
   *
   * <p>Modules can read external properties with {@code read("prop:<name>")}.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public ConfigEvaluatorBuilder addExternalProperties(Map<String, String> properties) {
    evaluatorBuilder.addExternalProperties(properties);
    return this;
  }

  /**
   * Removes any existing external properties, then adds the given properties.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public ConfigEvaluatorBuilder setExternalProperties(Map<String, String> properties) {
    evaluatorBuilder.setExternalProperties(properties);
    return this;
  }

  /**
   * Returns the currently set external properties.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public Map<String, String> getExternalProperties() {
    return evaluatorBuilder.getExternalProperties();
  }

  /**
   * Sets the given security manager, replacing any previously set security manager.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public ConfigEvaluatorBuilder setSecurityManager(SecurityManager manager) {
    evaluatorBuilder.setSecurityManager(manager);
    return this;
  }

  /**
   * Returns the currently set security manager.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public @Nullable SecurityManager getSecurityManager() {
    return evaluatorBuilder.getSecurityManager();
  }

  /**
   * Sets the given stack frame transformer, replacing any previously set transformer.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public ConfigEvaluatorBuilder setStackFrameTransformer(
      StackFrameTransformer stackFrameTransformer) {
    evaluatorBuilder.setStackFrameTransformer(stackFrameTransformer);
    return this;
  }

  /**
   * Returns the currently set stack frame transformer.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public @Nullable StackFrameTransformer getStackFrameTransformer() {
    return evaluatorBuilder.getStackFrameTransformer();
  }

  /**
   * Sets the project for the evaluator, without applying evaluator settings in the project.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public ConfigEvaluatorBuilder setProjectDependencies(DeclaredDependencies dependencies) {
    evaluatorBuilder.setProjectDependencies(dependencies);
    return this;
  }

  /**
   * Sets the project for the evaluator, and applies any settings if set.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   *
   * @throws IllegalStateException if {@link #setSecurityManager(SecurityManager)} was also called.
   */
  public ConfigEvaluatorBuilder applyFromProject(Project project) {
    evaluatorBuilder.applyFromProject(project);
    return this;
  }

  /**
   * Sets an evaluation timeout to be enforced by the {@link ConfigEvaluator}'s {@code evaluate}
   * methods.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public ConfigEvaluatorBuilder setTimeout(Duration timeout) {
    evaluatorBuilder.setTimeout(timeout);
    return this;
  }

  /**
   * Sets the set of URI patterns to be allowed when importing modules.
   *
   * <p>This is a convenieince method that delegates to the underlying evaluator builder.
   *
   * @throws IllegalStateException if {@link #setSecurityManager(SecurityManager)} was also called.
   */
  public ConfigEvaluatorBuilder setAllowedModules(Collection<Pattern> patterns) {
    evaluatorBuilder.setAllowedModules(patterns);
    return this;
  }

  /**
   * Returns the set of patterns to be allowed when importing modules.
   *
   * <p>This is a convenieince method that delegates to the underlying evaluator builder.
   */
  public List<Pattern> getAllowedModules() {
    return evaluatorBuilder.getAllowedModules();
  }

  /**
   * Sets the set of URI patterns to be allowed when reading resources.
   *
   * <p>This is a convenieince method that delegates to the underlying evaluator builder.
   *
   * @throws IllegalStateException if {@link #setSecurityManager(SecurityManager)} was also called.
   */
  public ConfigEvaluatorBuilder setAllowedResources(Collection<Pattern> patterns) {
    evaluatorBuilder.setAllowedResources(patterns);
    return this;
  }

  /**
   * Returns the set of patterns to be allowed when reading resources.
   *
   * <p>This is a convenieince method that delegates to the underlying evaluator builder.
   */
  public List<Pattern> getAllowedResources() {
    return evaluatorBuilder.getAllowedResources();
  }

  /**
   * Sets the root directory, which restricts access to file-based modules and resources located
   * under this directory.
   *
   * <p>This is a convenieince method that delegates to the underlying evaluator builder.
   */
  public ConfigEvaluatorBuilder setRootDir(@Nullable Path rootDir) {
    evaluatorBuilder.setRootDir(rootDir);
    return this;
  }

  /**
   * Returns the currently set root directory, if set.
   *
   * <p>This is a convenieince method that delegates to the underlying evaluator builder.
   */
  public @Nullable Path getRootDir() {
    return evaluatorBuilder.getRootDir();
  }

  /**
   * Returns the currently set evaluation timeout.
   *
   * <p>This is a convenience method that delegates to the underlying evaluator builder.
   */
  public @Nullable Duration getTimeout() {
    return evaluatorBuilder.getTimeout();
  }

  /**
   * Builds a config evaluator whose underlying module evaluator and value mapper is built using the
   * configured builders. The same builder can be used to build multiple config evaluators.
   */
  public ConfigEvaluator build() {
    return new ConfigEvaluatorImpl(evaluatorBuilder.build(), mapperBuilder.build());
  }
}

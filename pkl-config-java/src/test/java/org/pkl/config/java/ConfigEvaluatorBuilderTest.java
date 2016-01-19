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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.pkl.config.java.mapper.ConverterFactories;
import org.pkl.core.SecurityManagers;

public class ConfigEvaluatorBuilderTest {
  @Test
  public void preconfiguredBuilderHasPreconfiguredUnderlyingBuilders() {
    var builder = ConfigEvaluatorBuilder.preconfigured();

    var evaluatorBuilder = builder.getEvaluatorBuilder();
    assertThat(evaluatorBuilder).isNotNull();
    assertThat(evaluatorBuilder.getEnvironmentVariables()).isEqualTo(System.getenv());
    assertThat(evaluatorBuilder.getExternalProperties()).isEqualTo(System.getProperties());

    var mapperBuilder = builder.getValueMapperBuilder();
    assertThat(mapperBuilder).isNotNull();
    assertThat(mapperBuilder.getConverterFactories()).isEqualTo(ConverterFactories.all);
  }

  @Test
  public void unconfiguredBuilderHasUnconfiguredUnderlyingBuilders() {
    var builder = ConfigEvaluatorBuilder.unconfigured();

    var evaluatorBuilder = builder.getEvaluatorBuilder();
    assertThat(evaluatorBuilder).isNotNull();
    assertThat(evaluatorBuilder.getEnvironmentVariables()).isEmpty();
    assertThat(evaluatorBuilder.getExternalProperties()).isEmpty();

    var mapperBuilder = builder.getValueMapperBuilder();
    assertThat(mapperBuilder).isNotNull();
    assertThat(mapperBuilder.getConverterFactories()).isEmpty();
  }

  @Test
  public void preconfiguredBuilderContainsProcessEnvironmentVariables() {
    var builder = ConfigEvaluatorBuilder.preconfigured();
    assertThat(builder.getEnvironmentVariables()).isEqualTo(System.getenv());
  }

  @Test
  public void unconfiguredBuilderContainsNoEnvironmentVariables() {
    var builder = ConfigEvaluatorBuilder.unconfigured();
    assertThat(builder.getEnvironmentVariables()).isEmpty();
  }

  @Test
  public void addEnvironmentVariables() {
    var builder = ConfigEvaluatorBuilder.unconfigured();
    builder.addEnvironmentVariable("ONE", "one");
    var envVars = Map.of("TWO", "two", "THREE", "three");
    builder.addEnvironmentVariables(envVars);

    assertThat(builder.getEnvironmentVariables()).hasSize(3);
    assertThat(builder.getEnvironmentVariables()).containsEntry("ONE", "one");
    assertThat(builder.getEnvironmentVariables()).containsAllEntriesOf(envVars);
  }

  @Test
  public void overrideEnvironmentVariables() {
    var builder = ConfigEvaluatorBuilder.unconfigured();

    var envVars1 = Map.of("TWO", "two", "THREE", "three");
    builder.addEnvironmentVariables(envVars1);

    var envVars2 = Map.of("FOUR", "four", "FIVE", "five");
    builder.setEnvironmentVariables(envVars2);

    assertThat(builder.getEnvironmentVariables()).hasSize(2);
    assertThat(builder.getEnvironmentVariables()).containsAllEntriesOf(envVars2);
  }

  @Test
  public void preconfiguredBuilderContainsSystemProperties() {
    var builder = ConfigEvaluatorBuilder.preconfigured();
    assertThat(builder.getExternalProperties()).isEqualTo(System.getProperties());
  }

  @Test
  public void unconfiguredBuilderContainsNoExternalProperties() {
    var builder = ConfigEvaluatorBuilder.unconfigured();
    assertThat(builder.getExternalProperties()).isEmpty();
  }

  @Test
  public void addExternalProperties() {
    var builder = ConfigEvaluatorBuilder.unconfigured();
    builder.addExternalProperty("ONE", "one");
    var properties = Map.of("TWO", "two", "THREE", "three");
    builder.addExternalProperties(properties);

    assertThat(builder.getExternalProperties()).hasSize(3);
    assertThat(builder.getExternalProperties()).containsEntry("ONE", "one");
    assertThat(builder.getExternalProperties()).containsAllEntriesOf(properties);
  }

  @Test
  public void overrideExternalProperties() {
    var builder = ConfigEvaluatorBuilder.unconfigured();

    var properties1 = Map.of("TWO", "two", "THREE", "three");
    builder.addExternalProperties(properties1);

    var properties2 = Map.of("FOUR", "four", "FIVE", "five");
    builder.setExternalProperties(properties2);

    assertThat(builder.getExternalProperties()).hasSize(2);
    assertThat(builder.getExternalProperties()).containsAllEntriesOf(properties2);
  }

  @Test
  public void setSecurityManager() {
    var builder = ConfigEvaluatorBuilder.preconfigured();

    assertThat(builder.getAllowedModules()).isEqualTo(SecurityManagers.defaultAllowedModules);
    assertThat(builder.getAllowedResources()).isEqualTo(SecurityManagers.defaultAllowedResources);

    var manager =
        SecurityManagers.standard(List.of(), List.of(), SecurityManagers.defaultTrustLevels, null);

    builder = ConfigEvaluatorBuilder.preconfigured().setSecurityManager(manager);

    assertThat(builder.getSecurityManager()).isSameAs(manager);
  }
}

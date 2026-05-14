/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.core.ModuleSource;

public final class ConfigEvaluatorTest extends AbstractConfigTest {
  private static final ConfigEvaluator evaluator = ConfigEvaluator.preconfigured();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  @Override
  protected Config loadConfig(String text) {
    return evaluator.evaluate(ModuleSource.text(text));
  }

  @Test
  public void evaluateOutputValue() {
    var valueConfig =
        evaluator.evaluateOutputValue(
            ModuleSource.text(pigeonText + "\noutput { value = (outer) { pigeon { age = 99 } } }"));
    var pigeon = valueConfig.get("pigeon").as(Person.class);
    assertThat(pigeon.age).isEqualTo(99);
  }

  @Test
  public void evaluateExpression() {
    var addressConfig =
        evaluator.evaluateExpression(ModuleSource.text(pigeonText), "pigeon.address");
    var address = addressConfig.as(Address.class);
    assertThat(address.street).isEqualTo("Fuzzy St.");
  }

  @Test
  public void evaluateWithPerEvaluationExternalProperties() {
    var source =
        ModuleSource.create(
            URI.create("file:///config-evaluator-external-properties.pkl"),
            """
            configured = read("prop:configured")
            request = read("prop:request")
            output {
              value {
                configured = read("prop:configured")
                request = read("prop:request")
              }
            }
            """);

    try (var evaluator =
        ConfigEvaluatorBuilder.preconfigured()
            .addExternalProperty("configured", "configured")
            .addExternalProperty("request", "default")
            .build()) {
      var first = evaluator.evaluate(source, Map.of("request", "one"));
      assertThat(first.get("configured").as(String.class)).isEqualTo("configured");
      assertThat(first.get("request").as(String.class)).isEqualTo("one");

      var second = evaluator.evaluate(source, Map.of("request", "two"));
      assertThat(second.get("request").as(String.class)).isEqualTo("two");

      var unscoped = evaluator.evaluate(source);
      assertThat(unscoped.get("request").as(String.class)).isEqualTo("default");

      var outputValue = evaluator.evaluateOutputValue(source, Map.of("request", "three"));
      assertThat(outputValue.get("request").as(String.class)).isEqualTo("three");

      var expression = evaluator.evaluateExpression(source, "request", Map.of("request", "four"));
      assertThat(expression.as(String.class)).isEqualTo("four");
    }
  }
}

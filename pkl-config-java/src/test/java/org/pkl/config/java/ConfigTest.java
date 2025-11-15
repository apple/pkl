/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import static org.pkl.core.ModuleSource.text;

import org.junit.jupiter.api.Test;

public class ConfigTest extends AbstractConfigTest {
  private static final ConfigEvaluator evaluator = ConfigEvaluator.preconfigured();

  private static final String pigeonText =
      "pigeon { age = 30; friends = List(\"john\", \"mary\"); address { street = \"Fuzzy St.\" } }";

  @Override
  protected Config getPigeonConfig() {
    return evaluator.evaluate(text(pigeonText));
  }

  @Override
  protected Config getPigeonModuleConfig() {
    return evaluator.evaluate(
        text("age = 30; friends = List(\"john\", \"mary\"); address { street = \"Fuzzy St.\" }"));
  }

  @Override
  protected Config getPairConfig() {
    return evaluator.evaluate(text("x { first = \"file/path\"; second = 42 }"));
  }

  @Override
  protected Config getMapConfig() {
    return evaluator.evaluate(text("x = Map(\"one\", 1, \"two\", 2)"));
  }

  @Test
  public void evaluateOutputValue() {
    var valueConfig =
        evaluator.evaluateOutputValue(
            text(pigeonText + "\noutput { value = (outer) { pigeon { age = 99 } } }"));
    var pigeon = valueConfig.get("pigeon").as(Person.class);
    assertThat(pigeon.age).isEqualTo(99);
  }

  @Test
  public void evaluateExpression() {
    var addressConfig = evaluator.evaluateExpression(text(pigeonText), "pigeon.address");
    var address = addressConfig.as(Address.class);
    assertThat(address.street).isEqualTo("Fuzzy St.");
  }
}

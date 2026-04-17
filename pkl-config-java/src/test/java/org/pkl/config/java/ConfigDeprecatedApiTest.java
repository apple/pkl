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
package org.pkl.config.java;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.config.java.mapper.ValueMapper;
import org.pkl.core.Evaluator;
import org.pkl.core.ModuleSource;

@SuppressWarnings("removal")
public final class ConfigDeprecatedApiTest extends AbstractConfigTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  private ModuleSource toModuleSource(String text) {
    return ModuleSource.text(
"""
import "pkl:pklbinary"
"""
            + text
            + """
  output {
    renderer = new pklbinary.Renderer {}
  }
  """);
  }

  @Override
  protected Config loadConfig(String text) {
    var bytes = evaluator.evaluateOutputBytes(toModuleSource(text));
    return Config.fromPklBinary(bytes);
  }

  @Test
  public void fromInputStream() {
    var bytes = evaluator.evaluateOutputBytes(toModuleSource(pigeonText));
    var config = Config.fromPklBinary(new ByteArrayInputStream(bytes));
    assertThat(config.get("pigeon").get("age").as(Integer.class)).isEqualTo(30);
  }

  @Test
  public void fromBytesWithOwnValueMapper() {
    var bytes = evaluator.evaluateOutputBytes(toModuleSource(pigeonText));
    var config = Config.fromPklBinary(bytes, ValueMapper.preconfigured());
    assertThat(config.get("pigeon").get("age").as(Integer.class)).isEqualTo(30);
  }

  @Test
  public void fromInputStreamWithOwnValueMapper() {
    var bytes = evaluator.evaluateOutputBytes(toModuleSource(pigeonText));
    var config = Config.fromPklBinary(new ByteArrayInputStream(bytes), ValueMapper.preconfigured());
    assertThat(config.get("pigeon").get("age").as(Integer.class)).isEqualTo(30);
  }
}

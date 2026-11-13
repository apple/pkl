/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.config.java.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.pkl.core.ModuleSource.modulePath;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.core.Evaluator;
import org.pkl.core.PModule;

public class PUrlToStringTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PUrlToURITest.pkl"));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  @Test
  public void absolute() {
    assertThat(map("absolute")).isEqualTo("https://user:pw@example.com:8080/a/b?q=1#frag");
  }

  @Test
  public void percentEncoding() {
    assertThat(map("literalSpace")).isEqualTo("https://example.com/a%20b");
    assertThat(map("nonAscii")).isEqualTo("https://example.com/%C3%A9");
  }

  @Test
  public void relative() {
    assertThat(map("relative")).isEqualTo("/foo/bar");
    assertThat(map("colonSegment")).isEqualTo("./a:b");
  }

  @Test
  public void ipvFuture() {
    assertThat(map("ipvFuture")).isEqualTo("http://[v1.fe80::a+en1]/");
  }

  private static String map(String propertyName) {
    var url = module.get(propertyName);
    assert url != null;
    return mapper.map(url, String.class);
  }
}

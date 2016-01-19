/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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

public class PVersionToStringTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PVersionToVersionTest.pkl"));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  @Test
  public void ex1() {
    var ex1 = module.get("ex1");
    assert ex1 != null;
    var mapped = mapper.map(ex1, String.class);
    assertThat(mapped).isEqualTo("1.2.3");
  }

  @Test
  public void ex2() {
    var ex2 = module.get("ex2");
    assert ex2 != null;
    var mapped = mapper.map(ex2, String.class);
    assertThat(mapped).isEqualTo("1.2.3-rc.1");
  }

  @Test
  public void ex3() {
    var ex3 = module.get("ex3");
    assert ex3 != null;
    var mapped = mapper.map(ex3, String.class);
    assertThat(mapped).isEqualTo("1.2.3+456.789");
  }

  @Test
  public void ex4() {
    var ex4 = module.get("ex4");
    assert ex4 != null;
    var mapped = mapper.map(ex4, String.class);
    assertThat(mapped).isEqualTo("1.2.3-rc.1+456.789");
  }

  @Test
  public void ex5() {
    var ex5 = module.get("ex5");
    assert ex5 != null;
    var mapped = mapper.map(ex5, String.class);
    assertThat(mapped).isEqualTo("999999999999999.0.0");
  }
}

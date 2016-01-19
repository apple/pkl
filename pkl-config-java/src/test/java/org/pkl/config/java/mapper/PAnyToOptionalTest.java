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
package org.pkl.config.java.mapper;

import static org.assertj.core.api.Assertions.*;
import static org.pkl.core.ModuleSource.modulePath;

import java.util.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.core.Evaluator;
import org.pkl.core.PModule;

public class PAnyToOptionalTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PAnyToOptionalTest.pkl"));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  @Test
  public void ex1() {
    var ex1 = module.getProperty("ex1");
    Optional<String> mapped = mapper.map(ex1, Types.optionalOf(String.class));

    assertThat(mapped).isEmpty();
  }

  @Test
  public void ex2() {
    var ex2 = module.getProperty("ex2");
    Optional<String> mapped = mapper.map(ex2, Types.optionalOf(String.class));

    assertThat(mapped).contains("str");
  }

  @Test
  public void ex3() {
    var ex3 = module.getProperty("ex3");
    Optional<List<Integer>> mapped = mapper.map(ex3, Types.optionalOf(Types.listOf(Integer.class)));

    assertThat(mapped).contains(List.of(1, 2, 3));
  }
}

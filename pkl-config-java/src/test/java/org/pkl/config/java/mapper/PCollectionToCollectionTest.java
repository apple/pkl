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
package org.pkl.config.java.mapper;

import static org.assertj.core.api.Assertions.*;
import static org.pkl.core.ModuleSource.modulePath;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.core.Evaluator;
import org.pkl.core.PModule;

public class PCollectionToCollectionTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PCollectionToCollectionTest.pkl"));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  @Test
  public void ex1() {
    var ex1 = module.getProperty("ex1");

    List<Byte> mapped1 = mapper.map(ex1, Types.listOf(Byte.class));
    assertThat(mapped1).isEmpty();

    List<Short> mapped2 = mapper.map(ex1, Types.listOf(Short.class));
    assertThat(mapped2).isEmpty();

    List<Integer> mapped3 = mapper.map(ex1, Types.listOf(Integer.class));
    assertThat(mapped3).isEmpty();

    List<Long> mapped4 = mapper.map(ex1, Types.listOf(Long.class));
    assertThat(mapped4).isEmpty();
  }

  @Test
  public void ex2() {
    var ex2 = module.getProperty("ex2");

    List<Byte> mapped1 = mapper.map(ex2, Types.listOf(Byte.class));
    assertThat(mapped1).containsExactly((byte) 1, (byte) 2, (byte) 3);

    List<Short> mapped2 = mapper.map(ex2, Types.listOf(Short.class));
    assertThat(mapped2).containsExactly((short) 1, (short) 2, (short) 3);

    List<Integer> mapped3 = mapper.map(ex2, Types.listOf(Integer.class));
    assertThat(mapped3).containsExactly(1, 2, 3);

    List<Long> mapped4 = mapper.map(ex2, Types.listOf(Long.class));
    assertThat(mapped4).containsExactly(1L, 2L, 3L);
  }

  @Test
  public void ex3() {
    var ex3 = module.getProperty("ex3");

    List<Byte> mapped1 = mapper.map(ex3, Types.listOf(Byte.class));
    assertThat(mapped1).containsExactly((byte) 1, (byte) 2, (byte) 3);

    List<Short> mapped2 = mapper.map(ex3, Types.listOf(Short.class));
    assertThat(mapped2).containsExactly((short) 1, (short) 2, (short) 3);

    List<Integer> mapped3 = mapper.map(ex3, Types.listOf(Integer.class));
    assertThat(mapped3).containsExactly(1, 2, 3);

    List<Long> mapped4 = mapper.map(ex3, Types.listOf(Long.class));
    assertThat(mapped4).containsExactly(1L, 2L, 3L);
  }

  @Test
  public void ex4() {
    var ex4 = module.getProperty("ex4");

    List<Float> mapped1 = mapper.map(ex4, Types.listOf(Float.class));
    assertThat(mapped1).containsExactly(1f, 2f, 3.3f);

    List<Double> mapped2 = mapper.map(ex4, Types.listOf(Double.class));
    assertThat(mapped2).containsExactly(1d, 2d, 3.3d);
  }

  @Test
  public void ex5() {
    var ex5 = module.getProperty("ex5");
    List<Boolean> mapped = mapper.map(ex5, Types.listOf(Boolean.class));
    assertThat(mapped).containsExactly(true, false, true);
  }

  @Test
  public void ex6() {
    var ex6 = module.getProperty("ex6");
    List<Person> mapped = mapper.map(ex6, Types.listOf(Person.class));
    Assertions.assertThat(mapped)
        .containsExactly(new Person("pigeon", 40), new Person("parrot", 30));
  }
}

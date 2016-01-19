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

import static org.assertj.core.api.Assertions.*;
import static org.pkl.core.ModuleSource.modulePath;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.core.Evaluator;
import org.pkl.core.PModule;

public class PCollectionToArrayTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PCollectionToArrayTest.pkl"));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  @Test
  public void ex1() {
    var ex1 = module.getProperty("ex1");
    assertThat(mapper.map(ex1, byte[].class)).isEqualTo(new byte[0]);
    assertThat(mapper.map(ex1, short[].class)).isEqualTo(new short[0]);
    assertThat(mapper.map(ex1, int[].class)).isEqualTo(new int[0]);
    assertThat(mapper.map(ex1, long[].class)).isEqualTo(new long[0]);
  }

  @Test
  public void ex2() {
    var ex2 = module.getProperty("ex2");
    assertThat(mapper.map(ex2, byte[].class)).isEqualTo(new byte[] {1, 2, 3});
    assertThat(mapper.map(ex2, short[].class)).isEqualTo(new short[] {1, 2, 3});
    assertThat(mapper.map(ex2, int[].class)).isEqualTo(new int[] {1, 2, 3});
    assertThat(mapper.map(ex2, long[].class)).isEqualTo(new long[] {1, 2, 3});
  }

  @Test
  public void ex3() {
    var ex3 = module.getProperty("ex3");
    assertThat(mapper.map(ex3, byte[].class)).isEqualTo(new byte[] {1, 2, 3});
    assertThat(mapper.map(ex3, short[].class)).isEqualTo(new short[] {1, 2, 3});
    assertThat(mapper.map(ex3, int[].class)).isEqualTo(new int[] {1, 2, 3});
    assertThat(mapper.map(ex3, long[].class)).isEqualTo(new long[] {1, 2, 3});
  }

  @Test
  public void ex4() {
    var ex4 = module.getProperty("ex4");
    assertThat(mapper.map(ex4, float[].class)).isEqualTo(new float[] {1f, 2f, 3.3f});
    assertThat(mapper.map(ex4, double[].class)).isEqualTo(new double[] {1d, 2d, 3.3d});
  }

  @Test
  public void ex5() {
    var ex5 = module.getProperty("ex5");
    assertThat(mapper.map(ex5, boolean[].class)).isEqualTo(new boolean[] {true, false, true});
  }

  @Test
  public void ex6() {
    var ex6 = module.getProperty("ex6");
    assertThat(mapper.map(ex6, Person[].class))
        .isEqualTo(new Person[] {new Person("pigeon", 40), new Person("parrot", 30)});
  }
}

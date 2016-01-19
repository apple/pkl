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

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.core.DataSizeUnit;
import org.pkl.core.DurationUnit;
import org.pkl.core.Evaluator;
import org.pkl.core.PModule;

public class PStringToEnumTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PStringToEnumTest.pkl"));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  public enum Hobby {
    READING,
    SWIMMING,
    COUCH_SURFING
  }

  @Test
  public void ex1() {
    assertThat(mapper.map(module.getProperty("ex1"), Hobby.class)).isEqualTo(Hobby.COUCH_SURFING);
  }

  @Test
  public void ex2() {
    assertThat(mapper.map(module.getProperty("ex2"), Hobby.class)).isEqualTo(Hobby.COUCH_SURFING);
  }

  @Test
  public void ex3() {
    assertThat(mapper.map(module.getProperty("ex3"), Hobby.class)).isEqualTo(Hobby.COUCH_SURFING);
  }

  @Test
  public void ex4() {
    assertThat(mapper.map(module.getProperty("ex4"), Hobby.class)).isEqualTo(Hobby.COUCH_SURFING);
  }

  @Test
  public void ex5() {
    assertThat(mapper.map(module.getProperty("ex5"), Hobby.class)).isEqualTo(Hobby.COUCH_SURFING);
  }

  @Test
  public void ex6() {
    assertThat(mapper.map(module.getProperty("ex6"), Hobby.class)).isEqualTo(Hobby.COUCH_SURFING);
  }

  @Test
  public void ex7() {
    List<Hobby> mapped = mapper.map(module.getProperty("ex7"), Types.listOf(Hobby.class));

    assertThat(mapped).containsExactly(Hobby.SWIMMING, Hobby.READING, Hobby.COUCH_SURFING);
  }

  @Test
  public void ex8() {
    List<Hobby> mapped = mapper.map(module.getProperty("ex8"), Types.listOf(Hobby.class));

    assertThat(mapped).containsExactly(Hobby.COUCH_SURFING, Hobby.COUCH_SURFING);
  }

  @Test
  public void ex9() {
    var t = catchThrowable(() -> mapper.map(module.getProperty("ex9"), Types.listOf(Hobby.class)));

    assertThat(t)
        .isInstanceOf(ConversionException.class)
        .hasMessage(
            "Cannot convert String `other` to Enum value "
                + "of type `org.pkl.config.java.mapper.PStringToEnumTest$Hobby`.");
  }

  @Test
  public void ex11() {
    var unit = mapper.map(module.getProperty("ex11"), DurationUnit.class);
    assertThat(unit).isEqualTo(DurationUnit.MINUTES);
  }

  @Test
  public void ex12() {
    var unit = mapper.map(module.getProperty("ex12"), DataSizeUnit.class);
    assertThat(unit).isEqualTo(DataSizeUnit.GIGABYTES);
  }
}

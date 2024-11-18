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

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.core.*;

public class PMapToMapTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PMapToMapTest.pkl"));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  @Test
  public void ex1() {
    var ex1 = module.getProperty("ex1");
    Map<Integer, Integer> mapped = mapper.map(ex1, Types.mapOf(Integer.class, Integer.class));

    assertThat(mapped).isEmpty();
  }

  @Test
  public void ex2() {
    var ex2 = module.getProperty("ex2");
    Map<Integer, Integer> mapped = mapper.map(ex2, Types.mapOf(Integer.class, Integer.class));
    assertThat(mapped).containsOnly(entry(1, 2), entry(2, 4), entry(3, 6));

    Map<Byte, Double> mapped2 = mapper.map(ex2, Types.mapOf(Byte.class, Double.class));
    assertThat(mapped2).containsOnly(entry((byte) 1, 2d), entry((byte) 2, 4d), entry((byte) 3, 6d));
  }

  @Test
  public void ex3() {
    var ex3 = module.getProperty("ex3");
    Map<Integer, Double> mapped = mapper.map(ex3, Types.mapOf(Integer.class, Double.class));

    assertThat(mapped).containsOnly(entry(1, 2d), entry(2, 4d), entry(3, 6.6d));
  }

  @Test
  public void ex4() {
    var ex4 = module.getProperty("ex4");

    Map<String, Map<String, Object>> mapped =
        mapper.map(ex4, Types.mapOf(String.class, Types.mapOf(String.class, Object.class)));

    Map<String, Object> pigeon = Map.of("name", "pigeon", "age", 40L);
    Map<String, Object> parrot = Map.of("name", "parrot", "age", 30L);

    assertThat(mapped).containsOnly(entry("pigeon", pigeon), entry("parrot", parrot));
  }

  @Test
  public void ex5() {
    var ex5 = module.getProperty("ex5");
    Map<String, Person> mapped = mapper.map(ex5, Types.mapOf(String.class, Person.class));

    assertThat(mapped)
        .containsOnly(
            entry("pigeon", new Person("pigeon", 40)), entry("parrot", new Person("parrot", 30)));
  }

  @Test
  public void ex6() {
    var ex6 = module.getProperty("ex6");
    Map<Person, String> mapped = mapper.map(ex6, Types.mapOf(Person.class, String.class));

    assertThat(mapped)
        .containsOnly(
            entry(new Person("pigeon", 40), "pigeon"), entry(new Person("parrot", 30), "parrot"));
  }

  @Test
  public void ex7() {
    var mapper =
        ValueMapperBuilder.preconfigured()
            .addConversion(
                Conversion.of(PClassInfo.Int, String.class, (num, mapper2) -> String.valueOf(num)))
            .build();
    var ex7 = module.getProperty("ex7");
    // conversion from PInt to String kicks in because PMapToMap treats Properties as
    // Map<String,String>
    var properties = mapper.map(ex7, Properties.class);

    assertThat(properties).hasSize(2);
    assertThat(properties.getProperty("1")).isEqualTo("2");
    assertThat(properties.getProperty("2")).isEqualTo("4");
  }
}

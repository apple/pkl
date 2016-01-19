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
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pkl.core.*;

public class PObjectToPObjectTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PObjectToPObjectTest.pkl"));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  private PObject pigeon;
  private PObject parrot;

  @BeforeEach
  public void before() {
    Map<String, Object> pigeonProps = Map.of("name", "pigeon", "age", 40L);
    pigeon = new PObject(PClassInfo.Dynamic, pigeonProps);

    Map<String, Object> parrotProps = Map.of("name", "parrot", "age", 30L);
    parrot = new PObject(PClassInfo.Dynamic, parrotProps);
  }

  @Test
  public void ex1() {
    var ex1 = module.getProperty("ex1");

    assertThat(mapper.map(ex1, PObject.class)).isEqualTo(pigeon);
  }

  @Test
  public void ex2() {
    var ex2 = module.getProperty("ex2");
    List<PObject> mapped = mapper.map(ex2, Types.listOf(PObject.class));

    assertThat(mapped).containsExactly(pigeon, parrot);
  }
}

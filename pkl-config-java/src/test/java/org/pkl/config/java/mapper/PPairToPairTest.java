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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.pkl.core.ModuleSource.modulePath;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.core.*;

public class PPairToPairTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PPairToPairTest.pkl"));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  @Test
  public void ex1() {
    var ex1 = module.getProperty("ex1");
    Pair<Integer, Duration> mapped = mapper.map(ex1, Types.pairOf(Integer.class, Duration.class));
    assertThat(mapped).isEqualTo(new Pair<>(1, new Duration(3.0, DurationUnit.SECONDS)));
  }

  @Test
  public void ex2() {
    var ex2 = module.getProperty("ex2");
    Pair<PObject, PObject> mapped = mapper.map(ex2, Types.pairOf(PObject.class, PObject.class));

    assertThat(mapped.getFirst().getProperties())
        .containsOnly(entry("name", "pigeon"), entry("age", 40L));

    assertThat(mapped.getSecond().getProperties())
        .containsOnly(entry("name", "parrot"), entry("age", 30L));
  }
}

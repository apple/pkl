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

import java.util.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.config.java.mapper.PObjectToDataObjectTest.Address;
import org.pkl.config.java.mapper.PObjectToDataObjectTest.Hobby;
import org.pkl.core.Evaluator;
import org.pkl.core.PModule;

public class PModuleToDataObjectTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PModuleToDataObjectTest.pkl"));
  PObjectToDataObjectTest.Person pigeon =
      new PObjectToDataObjectTest.Person(
          "pigeon",
          40,
          EnumSet.of(Hobby.SURFING, Hobby.SWIMMING),
          new Address("sesame street", 94105));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  @Test
  public void doit() {
    assertThat(mapper.map(module, PObjectToDataObjectTest.Person.class)).isEqualTo(pigeon);
  }
}

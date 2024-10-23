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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.pkl.core.PNull;

public class PNullToAnyTest {
  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @Test
  public void test() {
    // due to Conversions.identities
    assertThat(mapper.map(PNull.getInstance(), PNull.class)).isEqualTo(PNull.getInstance());

    assertThat(mapper.map(PNull.getInstance(), String.class)).isNull();
    assertThat(mapper.map(PNull.getInstance(), Person.class)).isNull();
    assertThat(mapper.map(PNull.getInstance(), Integer.class)).isNull();

    assertThatThrownBy(() -> mapper.map(PNull.getInstance(), int.class))
        .isInstanceOf(ConversionException.class);
  }

  public static class Person {}
}

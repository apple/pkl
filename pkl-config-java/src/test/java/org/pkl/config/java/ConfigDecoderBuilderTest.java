/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.config.java;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.pkl.config.java.mapper.ConverterFactories;

public final class ConfigDecoderBuilderTest {
  @Test
  public void preconfiguredBuilderHasPreconfiguredMapperBuilder() {
    var builder = ConfigDecoderBuilder.preconfigured();
    var mapperBuilder = builder.getValueMapperBuilder();
    assertThat(mapperBuilder).isNotNull();
    assertThat(mapperBuilder.getConverterFactories()).isEqualTo(ConverterFactories.all);
  }

  @Test
  public void unconfiguredBuilderHasUnconfiguredMapperBuilder() {
    var builder = ConfigDecoderBuilder.unconfigured();
    var mapperBuilder = builder.getValueMapperBuilder();
    assertThat(mapperBuilder).isNotNull();
    assertThat(mapperBuilder.getConverterFactories()).isEmpty();
  }
}

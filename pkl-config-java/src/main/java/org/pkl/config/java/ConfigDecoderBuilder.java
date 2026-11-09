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

import org.pkl.config.java.mapper.ValueMapperBuilder;

/**
 * Builder for {@link ConfigDecoder} instances.
 *
 * <p>Use {@link #preconfigured()} to obtain a preconfigured builder, or {@link #unconfigured()} for
 * full control over its configuration.
 */
public final class ConfigDecoderBuilder {
  private ValueMapperBuilder mapperBuilder;

  private ConfigDecoderBuilder(ValueMapperBuilder mapperBuilder) {
    this.mapperBuilder = mapperBuilder;
  }

  /**
   * Returns a preconfigured builder that uses {@link ValueMapperBuilder#preconfigured()}.
   *
   * @return a preconfigured builder
   */
  public static ConfigDecoderBuilder preconfigured() {
    return new ConfigDecoderBuilder(ValueMapperBuilder.preconfigured());
  }

  /**
   * Returns an unconfigured builder that uses {@link ValueMapperBuilder#unconfigured()}.
   *
   * @return an unconfigured builder
   */
  public static ConfigDecoderBuilder unconfigured() {
    return new ConfigDecoderBuilder(ValueMapperBuilder.unconfigured());
  }

  /**
   * Returns the value mapper builder used by this decoder builder.
   *
   * @return the value mapper builder
   */
  public ValueMapperBuilder getValueMapperBuilder() {
    return mapperBuilder;
  }

  /**
   * Sets the value mapper builder used by this decoder builder.
   *
   * @param mapperBuilder the value mapper builder to use
   * @return this builder
   */
  public ConfigDecoderBuilder setValueMapperBuilder(ValueMapperBuilder mapperBuilder) {
    this.mapperBuilder = mapperBuilder;
    return this;
  }

  /**
   * Builds a {@link ConfigDecoder} using the current configuration.
   *
   * @return the configured decoder
   */
  public ConfigDecoder build() {
    return new ConfigDecoderImpl(mapperBuilder.build());
  }
}

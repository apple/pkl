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

import java.io.InputStream;
import org.pkl.config.java.mapper.ValueMapper;
import org.pkl.core.PklBinaryDecoder;

final class ConfigDecoderImpl implements ConfigDecoder {
  private final ValueMapper mapper;

  ConfigDecoderImpl(ValueMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ValueMapper getValueMapper() {
    return mapper;
  }

  @Override
  public ConfigDecoder setValueMapper(ValueMapper mapper) {
    return new ConfigDecoderImpl(mapper);
  }

  @Override
  public Config decode(byte[] bytes) {
    return ConfigUtils.createConfig(PklBinaryDecoder.decode(bytes), mapper);
  }

  @Override
  public Config decode(InputStream inputStream) {
    return ConfigUtils.createConfig(PklBinaryDecoder.decode(inputStream), mapper);
  }
}

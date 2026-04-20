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

/** Decodes Pkl binary data into {@link Config} objects. */
public interface ConfigDecoder {

  /**
   * Returns a preconfigured decoder that uses {@link ValueMapper#preconfigured()}.
   *
   * <p>For more control over configuration, use {@link ConfigDecoderBuilder}.
   *
   * @return a preconfigured decoder
   */
  static ConfigDecoder preconfigured() {
    return ConfigDecoderBuilder.preconfigured().build();
  }

  ValueMapper getValueMapper();

  /**
   * Returns a copy of this decoder with the supplied value mapper.
   *
   * @param mapper the value mapper to use
   * @return a decoder with the supplied value mapper
   */
  ConfigDecoder setValueMapper(ValueMapper mapper);

  /**
   * Decodes configuration from the supplied byte array.
   *
   * @param bytes the data to decode
   * @return the decoded configuration
   */
  Config decode(byte[] bytes);

  /**
   * Decodes configuration from the supplied input stream.
   *
   * <p>This method does not close the stream; the caller is responsible for closing it.
   *
   * @param inputStream the data to decode
   * @return the decoded configuration
   */
  Config decode(InputStream inputStream);
}

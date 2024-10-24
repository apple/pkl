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

import java.lang.reflect.Type;
import java.util.Optional;
import org.pkl.core.PClassInfo;
import org.pkl.core.PObject;

final class PObjectToPObject implements ConverterFactory {
  @Override
  public Optional<Converter<?, ?>> create(PClassInfo<?> sourceType, Type targetType) {
    if (!(sourceType.getJavaClass() == PObject.class
        && (targetType == Object.class || targetType == PObject.class))) {
      return Optional.empty();
    }
    return Optional.of(Converter.identity());
  }
}

/**
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

import java.lang.reflect.*;
import java.util.*;
import org.pkl.core.PClassInfo;
import org.pkl.core.PObject;

public final class PObjectToMap implements ConverterFactory {
  private final ConverterFactory pMapToMap = new PMapToMap();

  @Override
  public Optional<Converter<?, ?>> create(PClassInfo<?> sourceType, Type targetType) {
    var targetClass = Reflection.toRawType(targetType);

    if (!((sourceType == PClassInfo.Module || sourceType.getJavaClass() == PObject.class)
        && Map.class.isAssignableFrom(targetClass))) {
      return Optional.empty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Optional<Converter<Map<?, ?>, Map<?, ?>>> underlying =
        (Optional) pMapToMap.create(PClassInfo.Map, targetType);

    return underlying.map(
        converter ->
            (Converter<PObject, Map<?, ?>>)
                (value, valueMapper) -> converter.convert(value.getProperties(), valueMapper));
  }
}

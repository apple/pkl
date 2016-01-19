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
package org.pkl.config.java;

import java.util.Map;
import org.pkl.config.java.mapper.ValueMapper;
import org.pkl.core.PClassInfo;

class MapConfig extends AbstractConfig {
  private final Map<?, ?> map;

  MapConfig(String qualifiedName, ValueMapper mapper, Map<?, ?> map) {
    super(qualifiedName, mapper);
    this.map = map;
  }

  @Override
  public Object getRawValue() {
    return map;
  }

  @Override
  protected Object getRawChildValue(String propertyName) {
    var result = map.get(propertyName);
    if (result != null) return result;

    throw new NoSuchChildException(
        String.format(
            "Node `%s` of type `%s` does not have a key named `%s`. Available keys: %s",
            getQualifiedName(), PClassInfo.Map.getQualifiedName(), propertyName, map.keySet()),
        propertyName);
  }
}

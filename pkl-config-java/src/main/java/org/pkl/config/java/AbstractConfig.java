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
package org.pkl.config.java;

import java.lang.reflect.Type;
import java.util.Map;
import org.pkl.config.java.mapper.ValueMapper;
import org.pkl.core.Composite;

abstract class AbstractConfig implements Config {
  protected final String qualifiedName;
  protected final ValueMapper mapper;

  public AbstractConfig(String qualifiedName, ValueMapper mapper) {
    this.qualifiedName = qualifiedName;
    this.mapper = mapper;
  }

  @Override
  public String getQualifiedName() {
    return qualifiedName;
  }

  @Override
  public Config get(String propertyName) {
    var childValue = getRawChildValue(propertyName);
    var childName = qualifiedName.isEmpty() ? propertyName : qualifiedName + '.' + propertyName;
    if (childValue instanceof Composite composite) {
      return new CompositeConfig(childName, mapper, composite);
    }
    if (childValue instanceof Map<?, ?> map) {
      return new MapConfig(childName, mapper, map);
    }
    return new LeafConfig(childName, mapper, childValue);
  }

  @Override
  public <T> T as(Class<T> type) {
    return as((Type) type);
  }

  @Override
  public <T> T as(Type type) {
    return mapper.map(getRawValue(), type);
  }

  @Override
  public <T> T as(JavaType<T> javaType) {
    return as(javaType.getType());
  }

  protected abstract Object getRawChildValue(String property);
}

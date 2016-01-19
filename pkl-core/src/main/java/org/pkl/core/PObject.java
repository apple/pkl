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
package org.pkl.core;

import java.util.Map;
import java.util.Objects;
import org.pkl.core.util.Nullable;

/** Java representation of a Pkl object. */
public class PObject implements Composite {
  private static final long serialVersionUID = 0L;

  protected final PClassInfo<?> classInfo;
  protected final Map<String, Object> properties;

  public PObject(PClassInfo<?> classInfo, Map<String, Object> properties) {
    this.classInfo = Objects.requireNonNull(classInfo, "classInfo");
    this.properties = Objects.requireNonNull(properties, "properties");
  }

  @Override
  public final PClassInfo<?> getClassInfo() {
    return classInfo;
  }

  @Override
  public final Map<String, Object> getProperties() {
    return properties;
  }

  @Override
  public Object getProperty(String name) {
    var result = properties.get(name);
    if (result != null) return result;

    throw new NoSuchPropertyException(
        String.format(
            "Object of type `%s` does not have a property named `%s`. Available properties: %s",
            classInfo.getQualifiedName(), name, properties.keySet()),
        name);
  }

  @Override
  public void accept(ValueVisitor visitor) {
    visitor.visitObject(this);
  }

  @Override
  public <T> T accept(ValueConverter<T> converter) {
    return converter.convertObject(this);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;

    var other = (PObject) obj;
    return classInfo.equals(other.classInfo) && properties.equals(other.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classInfo, properties);
  }

  @Override
  public String toString() {
    return render(getClassInfo().getDisplayName());
  }

  protected String render(@Nullable String prefix) {
    var builder = new StringBuilder();

    if (prefix != null) {
      builder.append(prefix);
      builder.append(" { ");
    } else {
      builder.append("{ ");
    }

    var first = true;
    for (var property : properties.entrySet()) {
      if (first) {
        first = false;
      } else {
        builder.append("; ");
      }
      builder.append(property.getKey()).append(" = ").append(property.getValue());
    }

    builder.append(" }");
    return builder.toString();
  }
}

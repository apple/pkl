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
package org.pkl.core;

import java.io.Serial;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import org.pkl.core.util.Nullable;

/** Java representation of a Pkl module. */
public final class PModule extends PObject {
  @Serial private static final long serialVersionUID = 0L;

  private final URI moduleUri;
  private final String moduleName;

  public PModule(
      URI moduleUri, String moduleName, PClassInfo<?> classInfo, Map<String, Object> properties) {
    super(classInfo, properties);
    this.moduleUri = Objects.requireNonNull(moduleUri, "moduleUri");
    this.moduleName = Objects.requireNonNull(moduleName, "moduleName");
  }

  public URI getModuleUri() {
    return moduleUri;
  }

  public String getModuleName() {
    return moduleName;
  }

  @Override
  public Object getProperty(String name) {
    var result = properties.get(name);
    if (result != null) return result;

    throw new NoSuchPropertyException(
        String.format(
            "Module `%s` does not have a property named `%s`. Available properties: %s",
            moduleName, name, properties.keySet()),
        name);
  }

  @Override
  public void accept(ValueVisitor visitor) {
    visitor.visitModule(this);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof PModule other)) return false;
    return moduleUri.equals(other.moduleUri)
        && moduleName.equals(other.moduleName)
        && classInfo.equals(other.classInfo)
        && properties.equals(other.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(moduleUri, moduleName, classInfo, properties);
  }

  @Override
  public String toString() {
    return render(moduleName);
  }
}

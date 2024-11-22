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
package org.pkl.core.module;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.pkl.core.collection.EconomicMap;
import org.pkl.core.util.Nullable;

public class PathElement {
  public static final Comparator<PathElement> comparator =
      (o1, o2) -> {
        if (o1.isDirectory && !o2.isDirectory) {
          return 1;
        } else if (!o1.isDirectory && o2.isDirectory) {
          return -1;
        }
        return o1.name.compareTo(o2.name);
      };

  private final String name;

  private final boolean isDirectory;

  public static PathElement opaque(String name) {
    return new PathElement(name, false);
  }

  public PathElement(String name, boolean isDirectory) {
    this.name = name;
    this.isDirectory = isDirectory;
  }

  public String getName() {
    return name;
  }

  public PathElement withName(String name) {
    if (name.equals(this.name)) {
      return this;
    }
    return new PathElement(name, isDirectory);
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof PathElement other)) return false;
    return isDirectory == other.isDirectory && name.equals(other.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), isDirectory());
  }

  @Override
  public String toString() {
    return "PathElement{" + "name='" + name + '\'' + ", isDirectory=" + isDirectory + '}';
  }

  public static final class TreePathElement extends PathElement {
    private final EconomicMap<String, TreePathElement> children = EconomicMap.create();

    public TreePathElement(String name, boolean isDirectory) {
      super(name, isDirectory);
    }

    public TreePathElement putIfAbsent(String name, TreePathElement child) {
      children.putIfAbsent(name, child);
      return children.get(name);
    }

    /** Returns the element at path {@code basePath}, given a {@link Path}. */
    public @Nullable TreePathElement getElement(Path basePath) {
      var path = basePath.normalize();
      var element = this;
      for (var i = 0; i < path.getNameCount(); i++) {
        var part = path.getName(i).toString();
        element = element.getChildren().get(part);
        if (element == null) {
          return null;
        }
      }
      return element;
    }

    /** Returns the element at path {@code basePath}, given a path string. */
    public @Nullable TreePathElement getElement(String basePath) {
      return getElement(Path.of(basePath));
    }

    public EconomicMap<String, TreePathElement> getChildren() {
      return children;
    }

    public List<PathElement> getChildrenValues() {
      var ret = new ArrayList<PathElement>(children.size());
      for (var elem : children.getValues()) {
        ret.add(elem);
      }
      return ret;
    }
  }
}

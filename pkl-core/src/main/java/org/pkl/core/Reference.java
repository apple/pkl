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
package org.pkl.core;

import java.io.Serial;
import java.util.List;

public class Reference implements Value {
  @Serial private static final long serialVersionUID = 0L;

  private final Composite domain;
  private final Object data;
  private final List<Composite> path;
  private final PType referentType;

  /** Constructs a reference. */
  public Reference(Composite domain, Object data, List<Composite> path, PType referentType) {
    this.domain = domain;
    this.data = data;
    this.path = path;
    this.referentType = referentType;
  }

  /** Returns the domain object of this reference. */
  public Composite getDomain() {
    return domain;
  }

  /** Returns the data object of this reference. */
  public Object getData() {
    return data;
  }

  /**
   * Returns the access path of this reference.
   *
   * <p>All elements are exported {@code pkl.ref#Access} instances.
   */
  public List<Composite> getPath() {
    return path;
  }

  /** Returns the referent type of this reference. */
  public PType getReferentType() {
    return referentType;
  }

  @Override
  public void accept(ValueVisitor visitor) {
    visitor.visitReference(this);
  }

  @Override
  public <T> T accept(ValueConverter<T> converter) {
    return converter.convertReference(this);
  }

  @Override
  public final boolean equals(Object o) {
    if (!(o instanceof Reference reference)) {
      return false;
    }

    return domain.equals(reference.domain)
        && data.equals(reference.data)
        && path.equals(reference.path)
        && referentType.equals(reference.referentType);
  }

  @Override
  public PClassInfo<?> getClassInfo() {
    return PClassInfo.Reference;
  }

  @Override
  public int hashCode() {
    int result = domain.hashCode();
    result = 31 * result + data.hashCode();
    result = 31 * result + path.hashCode();
    result = 31 * result + referentType.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return super.toString();
  }
}

/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.runtime;

import java.util.Objects;

public interface VmValueVisitor {
  void visitString(String value);

  void visitBoolean(Boolean value);

  void visitInt(Long value);

  void visitFloat(Double value);

  void visitDuration(VmDuration value);

  void visitDataSize(VmDataSize value);

  void visitBytes(VmBytes value);

  void visitIntSeq(VmIntSeq value);

  void visitList(VmList value);

  void visitSet(VmSet value);

  void visitMap(VmMap value);

  void visitTyped(VmTyped value);

  void visitDynamic(VmDynamic value);

  void visitListing(VmListing value);

  void visitMapping(VmMapping value);

  void visitClass(VmClass value);

  void visitTypeAlias(VmTypeAlias value);

  void visitPair(VmPair value);

  void visitRegex(VmRegex value);

  void visitNull(VmNull value);

  void visitFunction(VmFunction value);

  default void visit(Object value) {
    Objects.requireNonNull(value, "Value to be visited must be non-null.");

    if (value instanceof VmValue vmValue) {
      vmValue.accept(this);
    } else if (value instanceof String string) {
      visitString(string);
    } else if (value instanceof Boolean b) {
      visitBoolean(b);
    } else if (value instanceof Long l) {
      visitInt(l);
    } else if (value instanceof Double d) {
      visitFloat(d);
    } else {
      throw new IllegalArgumentException("Unknown VM value type: " + value.getClass().getName());
    }
  }
}

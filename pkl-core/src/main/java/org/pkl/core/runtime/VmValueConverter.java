/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.pkl.core.ast.member.ClassProperty;
import org.pkl.core.util.Pair;

public interface VmValueConverter<T> {
  Object WILDCARD_PROPERTY =
      new Object() {
        @Override
        public String toString() {
          return "WILDCARD_PROPERTY";
        }
      };

  Object WILDCARD_ELEMENT =
      new Object() {
        @Override
        public String toString() {
          return "WILDCARD_ELEMENT";
        }
      };

  Object TOP_LEVEL_VALUE =
      new Object() {
        @Override
        public String toString() {
          return "TOP_LEVEL_VALUE";
        }
      };

  T convertString(String value, Iterable<Object> path);

  T convertBoolean(Boolean value, Iterable<Object> path);

  T convertInt(Long value, Iterable<Object> path);

  T convertFloat(Double value, Iterable<Object> path);

  T convertDuration(VmDuration value, Iterable<Object> path);

  T convertDataSize(VmDataSize value, Iterable<Object> path);

  T convertBytes(VmBytes vmBytes, Iterable<Object> path);

  T convertIntSeq(VmIntSeq value, Iterable<Object> path);

  T convertList(VmList value, Iterable<Object> path);

  T convertSet(VmSet value, Iterable<Object> path);

  T convertMap(VmMap value, Iterable<Object> path);

  T convertTyped(VmTyped value, Iterable<Object> path);

  T convertDynamic(VmDynamic value, Iterable<Object> path);

  T convertListing(VmListing value, Iterable<Object> path);

  T convertMapping(VmMapping value, Iterable<Object> path);

  T convertClass(VmClass value, Iterable<Object> path);

  T convertTypeAlias(VmTypeAlias value, Iterable<Object> path);

  T convertNull(VmNull value, Iterable<Object> path);

  T convertPair(VmPair value, Iterable<Object> path);

  T convertRegex(VmRegex value, Iterable<Object> path);

  T convertFunction(VmFunction value, Iterable<Object> path);

  T convertReference(VmReference value, Iterable<Object> path);

  T convertReferenceAccess(VmReference.Access value, Iterable<Object> path);

  default T convert(Object value, Iterable<Object> path) {
    if (value instanceof VmValue vmValue) {
      return vmValue.accept(this, path);
    }
    if (value instanceof String string) {
      return convertString(string, path);
    }
    if (value instanceof Boolean b) {
      return convertBoolean(b, path);
    }
    if (value instanceof Long l) {
      return convertInt(l, path);
    }
    if (value instanceof Double d) {
      return convertFloat(d, path);
    }

    throw new IllegalArgumentException("Cannot convert VM value with unexpected type: " + value);
  }
}

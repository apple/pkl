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

import java.util.*;

/** Predefined type mappings that can be registered with {@link ValueMapperBuilder}. */
public final class TypeMappings {
  private TypeMappings() {}

  /** Type mappings for collection types. */
  public static final Collection<TypeMapping<?, ?>> collections =
      List.of(
          TypeMapping.of(Iterable.class, ArrayList.class),
          TypeMapping.of(Collection.class, ArrayList.class),
          TypeMapping.of(List.class, ArrayList.class),
          TypeMapping.of(Set.class, HashSet.class),
          TypeMapping.of(Map.class, HashMap.class),
          TypeMapping.of(SortedSet.class, TreeSet.class),
          TypeMapping.of(SortedMap.class, TreeMap.class),
          TypeMapping.of(Queue.class, ArrayDeque.class),
          TypeMapping.of(Deque.class, ArrayDeque.class));

  /** All type mappings defined in this class. */
  public static final Collection<TypeMapping<?, ?>> all = collections;
}

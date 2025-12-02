/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util.paguro.type;

import static org.pkl.core.util.paguro.FunctionUtils.stringify;

import java.util.HashMap;
import java.util.Map;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.paguro.collections.ImList;
import org.pkl.core.util.paguro.collections.PersistentVector;

/**
 * Stores the classes from the compile-time generic type parameters in a vector in the *same order*
 * as the generics in the type signature of that class. Store them here using {@link
 * #registerClasses(Class[])} to avoid duplication. For example:
 *
 * <pre><code>private static final ImList<Class> CLASS_STRING_INTEGER =
 *    RuntimeTypes.registerClasses(vec(String.class, Integer.class));</code></pre>
 *
 * Now you if you use CLASS_STRING_INTEGER, you are never creating a new vector. For a full example
 * of how to use these RuntimeTypes, see {@link org.pkl.core.util.paguro.oneOf.OneOf2}.
 *
 * <p>This is an experiment in runtime types for Java. Constructive criticism is appreciated! If you
 * write a programming language, your compiler can manage these vectors so that humans don't have to
 * ever think about them, except to query them when they want to.
 *
 * <p>I believe this class is thread-safe.
 */
public final class RuntimeTypes {

  // This is a static (mutable) class.  Don't instantiate.
  @Deprecated
  private RuntimeTypes() {
    throw new UnsupportedOperationException("No instantiation");
  }

  // Keep a single copy of combinations of generic parameters at runtime in a trie.
  private static final ListAndMap root = new ListAndMap(PersistentVector.empty());

  // This is NOT thread-safe - for testing only!
  //    static int size = 0;

  /**
   * Use this to register runtime type signatures
   *
   * @param cs an array of types
   * @return An immutable vector of those types. Given the same types, always returns the same
   *     vector.
   */
  @SuppressWarnings("rawtypes")
  public static ImList<Class> registerClasses(Class... cs) {
    // Walk the trie to find the ImList corresponding to this array.
    ListAndMap node = root;
    for (Class currClass : cs) {
      node = node.next(currClass);
    }
    return node.list;
  }

  @SuppressWarnings("rawtypes")
  public static String name(@Nullable Class c) {
    return (c == null) ? "null" : c.getSimpleName();
  }

  @SuppressWarnings("rawtypes")
  public static String union2Str(Object item, ImList<Class> types) {
    StringBuilder sB = new StringBuilder();
    sB.append(stringify(item)).append(":");
    boolean isFirst = true;
    for (Class c : types) {
      if (isFirst) {
        isFirst = false;
      } else {
        sB.append("|");
      }
      sB.append(RuntimeTypes.name(c));
    }
    return sB.toString();
  }

  // Thanks to martynas on StackOverflow for the HashMap-based trie implementation!
  // https://stackoverflow.com/a/27378976/1128668
  @SuppressWarnings("rawtypes")
  private static class ListAndMap {
    public final ImList<Class> list;
    // Mutable field is private.
    private final Map<Class, ListAndMap> map = new HashMap<>();

    public ListAndMap(ImList<Class> l) {
      list = l;
    }

    // Synchronize on this node to inspect it or add children.
    // This avoids contention with other threads accessing any other nodes in this trie.
    // The list is immutable and threadsafe, the HashMap is neither.
    // That's why we access the HashMap in a tiny, synchronized method.
    // Everything from the start of the read to the end of the modification is therefore atomic.
    public synchronized ListAndMap next(Class currClass) {
      ListAndMap next = map.get(currClass);
      if (next == null) {
        // next is null when there is a new class in an existing sequence, or an entirely new
        // sequence.
        // Make the next node in this sequence and return it.
        //
        // Thread-safety:
        // list.append() creates a threadsafe, lightweight, modified copy of the list.
        // Still need to be synchronized between reading and writing the hashmap,
        // but the old and new immutable lists can be shared freely across threads throughout.
        next = new ListAndMap(list.append(currClass));
        map.put(currClass, next);
        //                    size++;
      }
      return next;
    }
  }
}

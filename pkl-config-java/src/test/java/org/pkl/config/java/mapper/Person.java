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
package org.pkl.config.java.mapper;

import org.pkl.core.util.Nullable;

public class Person {
  public final String name;
  public final int age;

  public Person(@Named("name") String name, @Named("age") int age) {
    this.name = name;
    this.age = age;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Person)) return false;

    var other = (Person) obj;
    return name.equals(other.name) && age == other.age;
  }

  @Override
  public int hashCode() {
    return name.hashCode() * 31 + Integer.hashCode(age);
  }
}

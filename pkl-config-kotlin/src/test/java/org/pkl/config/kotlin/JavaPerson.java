/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.config.kotlin;

import java.util.List;
import org.pkl.config.java.mapper.Named;

public class JavaPerson {
  private final String name;
  private final int age;
  private final List<String> hobbies;

  public JavaPerson(@Named("name") String name) {
    this.name = name;
    age = 0;
    hobbies = List.of();
  }

  public JavaPerson(
      @Named("name") String name, @Named("age") int age, @Named("hobbies") List<String> hobbies) {
    this.name = name;
    this.age = age;
    this.hobbies = hobbies;
  }

  public JavaPerson(@Named("age") int age) {
    this.age = age;
    name = "Default";
    hobbies = List.of();
  }

  public String getName() {
    return name;
  }

  public int getAge() {
    return age;
  }

  public List<String> getHobbies() {
    return hobbies;
  }
}

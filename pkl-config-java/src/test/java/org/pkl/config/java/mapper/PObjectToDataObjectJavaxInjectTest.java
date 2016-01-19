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
package org.pkl.config.java.mapper;

import static org.assertj.core.api.Assertions.*;
import static org.pkl.core.ModuleSource.modulePath;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import javax.inject.Named;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.core.Evaluator;
import org.pkl.core.PModule;
import org.pkl.core.util.Nullable;

public class PObjectToDataObjectJavaxInjectTest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PObjectToDataObjectTest.pkl"));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  private static final Person pigeon =
      new Person(
          "pigeon",
          40,
          EnumSet.of(Hobby.SURFING, Hobby.SWIMMING),
          new Address("sesame street", 94105));

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  @Test
  public void ex1() {
    var ex1 = module.getProperty("ex1");

    assertThat(mapper.map(ex1, Person.class)).isEqualTo(pigeon);
  }

  @Test
  public void ex2() {
    var ex2 = module.getProperty("ex2");

    assertThat(mapper.map(ex2, Person.class)).isEqualTo(pigeon);
  }

  @Test
  public void ex3() {
    var ex3 = module.getProperty("ex3");
    Object mapped =
        mapper.map(ex3, Types.parameterizedType(Pair.class, String.class, Integer.class));

    assertThat(mapped).isEqualTo(new Pair<>("foo", 42));
  }

  static class Person {
    final String name;
    final int age;
    final Set<Hobby> hobbies;
    final Address address;

    Person(
        @Named("name") String name,
        @Named("age") int age,
        @Named("hobbies") Set<Hobby> hobbies,
        @Named("address") Address address) {
      this.name = name;
      this.age = age;
      this.hobbies = hobbies;
      this.address = address;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Person)) return false;

      var other = (Person) obj;
      return name.equals(other.name)
          && age == other.age
          && hobbies.equals(other.hobbies)
          && address.equals(other.address);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, age, hobbies, address);
    }
  }

  static class Address {
    final String street;
    final int zip;

    Address(@Named("street") String street, @Named("zip") int zip) {
      this.street = street;
      this.zip = zip;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Address)) return false;

      var other = (Address) obj;
      return street.equals(other.street) && zip == other.zip;
    }

    @Override
    public int hashCode() {
      return Objects.hash(street, zip);
    }
  }

  public enum Hobby {
    SWIMMING,
    SURFING,
    READING
  }

  public static class Pair<S, T> {
    public final S first;
    public final T second;

    public Pair(@Named("first") S first, @Named("second") T second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Pair)) return false;

      var other = (Pair<?, ?>) obj;
      return first.equals(other.first) && second.equals(other.second);
    }

    @Override
    public int hashCode() {
      return Objects.hash(first, second);
    }
  }
}

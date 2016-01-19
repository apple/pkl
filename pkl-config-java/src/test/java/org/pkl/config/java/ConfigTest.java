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
package org.pkl.config.java;

import static org.assertj.core.api.Assertions.*;
import static org.pkl.core.ModuleSource.text;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pkl.config.java.mapper.Named;
import org.pkl.config.java.mapper.Types;
import org.pkl.core.PObject;

public class ConfigTest {
  private final ConfigEvaluator evaluator = ConfigEvaluator.preconfigured();

  private final Config pigeonConfig =
      evaluator.evaluate(
          text(
              "pigeon { age = 30; friends = List(\"john\", \"mary\"); address { street = \"Fuzzy St.\" } }"));

  private final Config pigeonModuleConfig =
      evaluator.evaluate(
          text("age = 30; friends = List(\"john\", \"mary\"); address { street = \"Fuzzy St.\" }"));

  private final Config pairConfig =
      evaluator.evaluate(text("x { first = \"file/path\"; second = 42 }"));

  private final Config mapConfig = evaluator.evaluate(text("x = Map(\"one\", 1, \"two\", 2)"));

  @Test
  public void navigate() {
    var pigeon = pigeonConfig.get("pigeon");
    assertThat(pigeon.getQualifiedName()).isEqualTo("pigeon");
    assertThat(pigeon.getRawValue()).isInstanceOf(PObject.class);

    var address = pigeon.get("address");
    assertThat(address.getQualifiedName()).isEqualTo("pigeon.address");
    assertThat(address.getRawValue()).isInstanceOf(PObject.class);

    var street = address.get("street");
    assertThat(street.getQualifiedName()).isEqualTo("pigeon.address.street");
    assertThat(street.getRawValue()).isInstanceOf(String.class);

    assertThat(street.as(String.class)).isEqualTo("Fuzzy St.");
  }

  @Test
  public void navigateToNonExistingObjectChild() {
    var pigeon = pigeonConfig.get("pigeon");
    var t = catchThrowable(() -> pigeon.get("non-existing"));

    assertThat(t)
        .isInstanceOf(NoSuchChildException.class)
        .hasMessageStartingWith(
            "Node `pigeon` of type `pkl.base#Dynamic` "
                + "does not have a property named `non-existing`.");
  }

  @Test
  public void navigateToNonExistingMapChild() {
    var map = mapConfig.get("x");
    var t = catchThrowable(() -> map.get("non-existing"));

    assertThat(t)
        .isInstanceOf(NoSuchChildException.class)
        .hasMessageStartingWith(
            "Node `x` of type `pkl.base#Map` " + "does not have a key named `non-existing`.");
  }

  @Test
  public void navigateToNonExistingLeafChild() {
    var age = pigeonConfig.get("pigeon").get("age");
    var t = catchThrowable(() -> age.get("non-existing"));

    assertThat(t)
        .isInstanceOf(NoSuchChildException.class)
        .hasMessageStartingWith(
            "Leaf node `pigeon.age` of type `pkl.base#Int` "
                + "does not have a child named `non-existing`.");
  }

  @Test
  public void convertObjectToPojoByType() {
    Person pigeon = pigeonConfig.get("pigeon").as(Person.class);
    checkPigeon(pigeon);
  }

  @Test
  public void convertObjectToPojoByJavaType() {
    var pigeon = pigeonConfig.get("pigeon").as(JavaType.of(Person.class));
    checkPigeon(pigeon);
  }

  @Test
  public void convertModuleToPojoByType() {
    var pigeon = pigeonModuleConfig.as(Person.class);
    checkPigeon(pigeon);
  }

  @Test
  public void convertModuleToPojoByJavaType() {
    var pigeon = pigeonModuleConfig.as(JavaType.of(Person.class));
    checkPigeon(pigeon);
  }

  private void checkPigeon(Person pigeon) {
    assertThat(pigeon).isNotNull();
    assertThat(pigeon.age).isEqualTo(30);
    assertThat(pigeon.friends).containsExactly("john", "mary");
    assertThat(pigeon.address.street).isEqualTo("Fuzzy St.");
  }

  @Test
  public void convertToParameterizedTypeByType() {
    Pair<Path, Integer> pair =
        pairConfig.get("x").as(Types.parameterizedType(Pair.class, Path.class, Integer.class));
    checkPair(pair);
  }

  @Test
  public void convertToParameterizedTypeByJavaType() {
    var pair = pairConfig.get("x").as(new JavaType<Pair<Path, Integer>>() {});
    checkPair(pair);
  }

  private void checkPair(Pair<?, ?> pair) {
    assertThat(pair).isNotNull();
    assertThat(pair.first).isEqualTo(Path.of("file/path"));
    assertThat(pair.second).isEqualTo(42);
  }

  public static class Person {
    final int age;
    final List<String> friends;
    final Address address;

    public Person(
        @Named("age") int age,
        @Named("friends") List<String> friends,
        @Named("address") Address address) {
      this.age = age;
      this.friends = friends;
      this.address = address;
    }
  }

  public static class Address {
    final String street;

    public Address(@Named("street") String street) {
      this.street = street;
    }
  }

  public static class Pair<S, T> {
    final S first;
    final T second;

    public Pair(@Named("first") S first, @Named("second") T second) {
      this.first = first;
      this.second = second;
    }
  }
}

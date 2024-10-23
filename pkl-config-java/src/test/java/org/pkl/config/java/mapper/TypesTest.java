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

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.*;
import org.junit.jupiter.api.Test;

public class TypesTest {
  @Test
  public void createParameterizedType() {}

  @Test
  public void createParameterizedTypeForClassWithoutTypeParameters() {
    var t = catchThrowable(() -> Types.parameterizedType(String.class));

    assertThat(t)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Cannot parameterize `java.lang.String` "
                + "because it does not have any type parameters.");
  }

  @Test
  public void createParameterizedTypeWithWrongNumberOfTypeArguments() {
    var t =
        catchThrowable(
            () -> Types.parameterizedType(Map.class, Integer.class, String.class, URL.class));

    assertThat(t)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected 2 type arguments for `java.util.Map`, but got 3.");
  }

  @Test
  public void createParameterizedTypeWithPrimitiveTypeArgument() {
    Throwable t = catchThrowable(() -> Types.parameterizedType(List.class, int.class));

    assertThat(t)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("`int.class` is not a valid type argument. Did you mean `Integer.class`?");
  }

  @SuppressWarnings("unused")
  static class Foo<T extends Bar> {}

  static class Bar {}

  @Test
  public void createParameterizedTypeWithIncompatibleTypeArgument() {
    Throwable t = catchThrowable(() -> Types.parameterizedType(Foo.class, String.class));

    assertThat(t)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Type argument `java.lang.String` for type parameter `T` is "
                + "not within bound `org.pkl.config.java.mapper.TypesTest$Bar`.");
  }

  @Test
  public void createPrimitiveArrayType() {
    assertThat(Types.arrayOf(int.class)).isEqualTo(int[].class);
  }

  static class Person {}

  @Test
  public void createObjectArrayType() {
    assertThat(Types.arrayOf(Person.class)).isEqualTo(Person[].class);
  }

  @Test
  public void createIterableType() {
    ParameterizedType type = Types.iterableOf(Person.class);
    assertThat(type.getRawType()).isEqualTo(Iterable.class);
    assertThat(type.getActualTypeArguments()).isEqualTo(new Type[] {Person.class});
  }

  @Test
  public void createCollectionType() {
    ParameterizedType type = Types.collectionOf(Person.class);
    assertThat(type.getRawType()).isEqualTo(Collection.class);
    assertThat(type.getActualTypeArguments()).isEqualTo(new Type[] {Person.class});
  }

  @Test
  public void createListType() {
    ParameterizedType type = Types.listOf(Person.class);
    assertThat(type.getRawType()).isEqualTo(List.class);
    assertThat(type.getActualTypeArguments()).isEqualTo(new Type[] {Person.class});
  }

  @Test
  public void createSetType() {
    ParameterizedType type = Types.setOf(Person.class);
    assertThat(type.getRawType()).isEqualTo(Set.class);
    assertThat(type.getActualTypeArguments()).isEqualTo(new Type[] {Person.class});
  }

  @Test
  public void createMapType() {
    ParameterizedType type = Types.mapOf(String.class, Person.class);
    assertThat(type.getRawType()).isEqualTo(Map.class);
    assertThat(type.getActualTypeArguments()).isEqualTo(new Type[] {String.class, Person.class});
  }
}

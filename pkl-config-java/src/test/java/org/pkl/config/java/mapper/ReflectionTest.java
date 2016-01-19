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

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ReflectionTest {
  @SuppressWarnings("unused")
  static class Container<T> {
    Container(T element) {}

    void setElement(T element) {}
  }

  static class Person {}

  @Test
  public void isMissingTypeArguments() {
    assertThat(
            Reflection.isMissingTypeArguments(
                Types.parameterizedType(Container.class, Person.class)))
        .isFalse();
    assertThat(Reflection.isMissingTypeArguments(Container.class)).isTrue();
    assertThat(Reflection.isMissingTypeArguments(Person.class)).isFalse();
  }

  @Test
  public void toRawType() {
    var type = Types.listOf(Person.class);

    assertThat(Reflection.toRawType(type)).isEqualTo(List.class);
    assertThat(Reflection.toRawType(List.class)).isEqualTo(List.class);
  }

  @Test
  public void toWrapperType() {
    assertThat(Reflection.toWrapperType(float.class)).isEqualTo(Float.class);
    assertThat(Reflection.toWrapperType(Person.class)).isEqualTo(Person.class);
  }

  @Test
  public void getArrayElementType() {
    assertThat(Reflection.getArrayElementType(int[].class)).isEqualTo(int.class);
    assertThat(Reflection.getArrayElementType(Person[].class)).isEqualTo(Person.class);

    var containerOfPerson = Types.parameterizedType(Container.class, Person.class);
    assertThat(Reflection.getArrayElementType(Types.arrayOf(containerOfPerson)))
        .isEqualTo(containerOfPerson);
  }

  @Test
  public void getExactSupertype() {
    assertThat(
            Reflection.getExactSupertype(
                Types.parameterizedType(ArrayList.class, Person.class), Collection.class))
        .isEqualTo(Types.parameterizedType(Collection.class, Person.class));
  }

  @Test
  public void getExactSubtype() {
    assertThat(
            Reflection.getExactSubtype(
                Types.parameterizedType(Collection.class, Person.class), ArrayList.class))
        .isEqualTo(Types.parameterizedType(ArrayList.class, Person.class));
  }

  @Test
  public void getExactParameterTypes() {
    var type = Types.parameterizedType(Container.class, Person.class);

    var ctor = Container.class.getDeclaredConstructors()[0];
    assertThat(Reflection.getExactParameterTypes(ctor, type)).isEqualTo(new Type[] {Person.class});

    var method = Container.class.getDeclaredMethods()[0];
    assertThat(Reflection.getExactParameterTypes(method, type))
        .isEqualTo(new Type[] {Person.class});
  }
}

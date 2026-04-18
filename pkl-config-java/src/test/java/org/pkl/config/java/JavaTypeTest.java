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
package org.pkl.config.java;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.pkl.config.java.mapper.Reflection;
import org.pkl.config.java.mapper.Types;
import org.pkl.core.Pair;

public final class JavaTypeTest {
  @Test
  public void constructSimpleType() {
    assertThat(JavaType.of(String.class)).isEqualTo(new JavaType<String>() {});
    //noinspection AssertBetweenInconvertibleTypes
    assertThat(JavaType.of((Type) String.class)).isEqualTo(new JavaType<String>() {});
  }

  @Test
  public void constructSimpleNullableType() {
    assertThat(JavaType.ofNullable(String.class)).isEqualTo(new JavaType<@Nullable String>() {});
    //noinspection AssertBetweenInconvertibleTypes
    assertThat(JavaType.ofNullable((Type) String.class))
        .isEqualTo(new JavaType<@Nullable String>() {});
  }

  @Test
  public void constructOptionalType() {
    var type = JavaType.optionalOf(String.class);
    assertThat(type).isEqualTo(new JavaType<Optional<String>>() {});
    assertThat(type).isEqualTo(JavaType.optionalOf(JavaType.of(String.class)));
    assertThat(Reflection.toRawType(type.getType())).isEqualTo(Optional.class);
  }

  @Test
  public void constructPairType() {
    var type = JavaType.pairOf(String.class, Integer.class);
    assertThat(type).isEqualTo(new JavaType<Pair<String, Integer>>() {});
    assertThat(type)
        .isEqualTo(JavaType.pairOf(JavaType.of(String.class), JavaType.of(Integer.class)));
  }

  @Test
  public void constructArrayType() {
    var type = JavaType.arrayOf(String.class);
    assertThat(type).isEqualTo(new JavaType<String[]>() {});
    assertThat(type).isEqualTo(JavaType.arrayOf(JavaType.of(String.class)));
    assertThat(Reflection.toRawType(type.getType()).isArray()).isTrue();
  }

  @Test
  public void constructArrayOfNullableType() {
    var type = JavaType.arrayOfNullable(String.class);
    assertThat(type).isEqualTo(new JavaType<@Nullable String[]>() {});
    assertThat(type).isEqualTo(JavaType.arrayOf(JavaType.ofNullable(String.class)));
    assertThat(Reflection.toRawType(type.getType()).isArray()).isTrue();
  }

  @Test
  public void constructIterableType() {
    var type = JavaType.iterableOf(String.class);
    assertThat(type).isEqualTo(new JavaType<Iterable<String>>() {});
    assertThat(type).isEqualTo(JavaType.iterableOf(JavaType.of(String.class)));
    assertThat(Reflection.toRawType(type.getType())).isEqualTo(Iterable.class);
  }

  @Test
  public void constructIterableOfNullableType() {
    var type = JavaType.iterableOfNullable(String.class);
    assertThat(type).isEqualTo(new JavaType<Iterable<@Nullable String>>() {});
    assertThat(type).isEqualTo(JavaType.iterableOf(JavaType.ofNullable(String.class)));
    assertThat(Reflection.toRawType(type.getType())).isEqualTo(Iterable.class);
  }

  @Test
  public void constructCollectionType() {
    var type = JavaType.collectionOf(String.class);
    assertThat(type).isEqualTo(new JavaType<Collection<String>>() {});
    assertThat(type).isEqualTo(JavaType.collectionOf(JavaType.of(String.class)));
    assertThat(Reflection.toRawType(type.getType())).isEqualTo(Collection.class);
  }

  @Test
  public void constructCollectionOfNullableType() {
    var type = JavaType.collectionOfNullable(String.class);
    assertThat(type).isEqualTo(new JavaType<Collection<@Nullable String>>() {});
    assertThat(type).isEqualTo(JavaType.collectionOf(JavaType.ofNullable(String.class)));
    assertThat(Reflection.toRawType(type.getType())).isEqualTo(Collection.class);
  }

  @Test
  public void constructListType() {
    var type = JavaType.listOf(String.class);
    assertThat(type).isEqualTo(new JavaType<List<String>>() {});
    assertThat(type).isEqualTo(JavaType.listOf(JavaType.of(String.class)));
    assertThat(Reflection.toRawType(type.getType())).isEqualTo(List.class);
  }

  @Test
  public void constructListOfNullableType() {
    var type = JavaType.listOfNullable(String.class);
    assertThat(type).isEqualTo(new JavaType<List<@Nullable String>>() {});
    assertThat(type).isEqualTo(JavaType.listOf(JavaType.ofNullable(String.class)));
    assertThat(Reflection.toRawType(type.getType())).isEqualTo(List.class);
  }

  @Test
  public void constructSetType() {
    var type = JavaType.setOf(String.class);
    assertThat(type).isEqualTo(new JavaType<Set<String>>() {});
    assertThat(type).isEqualTo(JavaType.setOf(JavaType.of(String.class)));
    assertThat(Reflection.toRawType(type.getType())).isEqualTo(Set.class);
  }

  @Test
  public void constructSetOfNullableType() {
    var type = JavaType.setOfNullable(String.class);
    assertThat(type).isEqualTo(new JavaType<Set<@Nullable String>>() {});
    assertThat(type).isEqualTo(JavaType.setOf(JavaType.ofNullable(String.class)));
    assertThat(Reflection.toRawType(type.getType())).isEqualTo(Set.class);
  }

  @Test
  public void constructMapType() {
    var type = JavaType.mapOf(String.class, URI.class);
    assertThat(type).isEqualTo(new JavaType<Map<String, URI>>() {});
    assertThat(type).isEqualTo(JavaType.mapOf(JavaType.of(String.class), JavaType.of(URI.class)));
    assertThat(Reflection.toRawType(type.getType())).isEqualTo(Map.class);
  }

  @Test
  public void constructTypeToken() {
    var javaType = new JavaType<Map<String, List<URI>>>() {};

    assertThat(javaType.getType()).isEqualTo(Types.mapOf(String.class, Types.listOf(URI.class)));
  }

  @SuppressWarnings({"EqualsWithItself", "AssertBetweenInconvertibleTypes"})
  @Test
  public void sameTypesConstructedInDifferentWaysAreEqual() {
    var type1 = JavaType.mapOf(JavaType.of(String.class), JavaType.listOf(URI.class));
    var type2 = new JavaType<Map<String, List<URI>>>() {};
    var type3 = JavaType.of(Types.mapOf(String.class, Types.listOf(URI.class)));

    assertThat(type1).isEqualTo(type1);
    assertThat(type2).isEqualTo(type1);
    assertThat(type3).isEqualTo(type2);

    assertThat(type2.hashCode()).isEqualTo(type1.hashCode());
    assertThat(type3.hashCode()).isEqualTo(type2.hashCode());
  }

  @Test
  public void differentTypesAreNotEqual() {
    var type1 = JavaType.mapOf(JavaType.of(String.class), JavaType.listOf(URI.class));
    var type2 = new JavaType<Map<String, List<URL>>>() {};
    var type3 = JavaType.of(Types.mapOf(String.class, Types.listOf(Path.class)));

    //noinspection AssertBetweenInconvertibleTypes
    assertThat(type2).isNotEqualTo(type1);
    assertThat(type3).isNotEqualTo(type1);
    assertThat(type2).isNotEqualTo(type3);

    // hopefully
    assertThat(type2.hashCode()).isNotEqualTo(type1.hashCode());
    assertThat(type3.hashCode()).isNotEqualTo(type1.hashCode());
    assertThat(type3.hashCode()).isNotEqualTo(type2.hashCode());
  }

  @Test
  public void sameStringRepresentationAsJavaLangReflectType() {
    var type = JavaType.mapOf(String.class, URI.class);
    assertThat(type.toString()).isEqualTo("java.util.Map<java.lang.String, java.net.URI>");
    assertThat(type.toString()).isEqualTo(type.getType().toString());
  }
}

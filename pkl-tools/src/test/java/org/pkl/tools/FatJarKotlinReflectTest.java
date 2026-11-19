/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for https://github.com/apple/pkl/issues/794.
 *
 * <p>{@code kotlin-reflect} resolves implementation classes, the {@code kotlin.Metadata}
 * annotation, and {@code .kotlin_builtins} resources by their original names, so it must
 * not be relocated even though {@code kotlin-stdlib} is shaded into
 * {@code org.pkl.thirdparty.kotlin}.
 */
class FatJarKotlinReflectTest {
  @Test
  void kotlinReflectIsNotRelocated() {
    assertNotNull(
        getClass().getClassLoader().getResource("kotlin/reflect/jvm/internal/KClassImpl.class"),
        "kotlin-reflect classes must not be relocated");
    assertNotNull(
        getClass().getClassLoader().getResource("kotlin/kotlin.kotlin_builtins"),
        "kotlin/kotlin.kotlin_builtins must not be relocated");
    assertNotNull(
        getClass()
            .getClassLoader()
            .getResource("org/pkl/thirdparty/kotlin/jvm/internal/Reflection.class"),
        "kotlin-stdlib must stay relocated");
  }

  @Test
  void shadedKotlinReflectDeserializesKotlinClasses() throws Exception {
    // `SomeClass::class` in shaded Kotlin code resolves through kotlin-stdlib's
    // `Reflection` facade, which locates `ReflectionFactoryImpl` by name and
    // returns a `KClassImpl`. `getMembers()` then deserializes the class's
    // `kotlin.Metadata`, which needs the `.kotlin_builtins` resources and the
    // `kotlin.*` name tables (e.g. `JavaToKotlinClassMap`).
    Class<?> kotlinClass = Class.forName("kotlin.reflect.jvm.internal.KClassImpl");
    Class<?> reflection = Class.forName("org.pkl.thirdparty.kotlin.jvm.internal.Reflection");
    Object kClass =
        reflection.getMethod("getOrCreateKotlinClass", Class.class).invoke(null, kotlinClass);
    assertEquals(
        "kotlin.reflect.jvm.internal.KClassImpl",
        kClass.getClass().getName(),
        "kotlin-stdlib Reflection facade must return kotlin-reflect's KClassImpl, not a stub");
    assertTrue(
        Class.forName("kotlin.reflect.KClass").isInstance(kClass),
        "KClassImpl must implement the unrelocated kotlin.reflect.KClass API");
    Object qualifiedName = kClass.getClass().getMethod("getQualifiedName").invoke(kClass);
    assertEquals(
        "kotlin.reflect.jvm.internal.KClassImpl",
        qualifiedName,
        "KClass.getQualifiedName must resolve the original Kotlin name");
    Object members = kClass.getClass().getMethod("getMembers").invoke(kClass);
    assertFalse(
        (Boolean) members.getClass().getMethod("isEmpty").invoke(members),
        "kotlin-reflect must deserialize members from Kotlin metadata");
  }
}

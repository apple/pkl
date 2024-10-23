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
package org.pkl.config.kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.ModuleSource.text

class KotlinObjectMappingTest {
  data class TypedKey(val value: Int)

  data class KotlinGenericTypesTest(
    // Sets
    val stringSet: Set<String>,
    val intSet: Set<Int>,
    val booleanSetSet: Set<Set<Boolean>>,

    // Lists
    val stringList: List<String>,
    val intList: List<Int>,
    val booleanListList: List<List<Boolean>>,

    // Maps
    val intStringMap: Map<Int, String>,
    val booleanIntStringMapMap: Map<Boolean, Map<Int, String>>,
    val booleanIntMapStringMap: Map<Map<Boolean, Int>, String>,
    val intSetListStringMap: Map<List<Set<Int>>, String>,
    val typedStringMap: Map<TypedKey, String>,
    val dynamicStringMap: Map<Map<String, Any>, String>,

    // Listings
    val stringSetListing: List<Set<String>>,
    val intListingListing: List<List<Int>>,

    // Mapping
    val intStringMapping: Map<Int, String>,
    val stringStringSetMapping: Map<String, Set<String>>,

    // Map & Mapping with structured keys
    val intListingStringMapping: Map<List<Int>, String>,
    val intSetListStringMapping: Map<List<Set<Int>>, String>,
    val thisOneGoesToEleven: Map<List<Set<Int>>, Map<List<Int>, Map<Int, String>>>
  )

  @Test
  fun `generic types correspond`() {
    val code =
      """
      module KotlinGenericTypesTest
      
      class Foo {
        value: Int
      }
      
      // Sets
      stringSet: Set<String> = Set("in set")
      intSet: Set<Int> = Set(1,2,4,8,16,32)
      booleanSetSet: Set<Set<Boolean>> = Set(Set(false), Set(true), Set(true, false))
      
      // Lists
      stringList: List<String> = List("in list")
      intList: List<Int> = List(1,2,3,5,7,11)
      booleanListList: List<List<Boolean>> = List(List(false), List(true), List(true, false))
      
      // Maps
      intStringMap: Map<Int, String> = Map(0, "in map")
      booleanIntStringMapMap: Map<Boolean, Map<Int, String>> = Map(false, Map(0, "in map in map"))
      booleanIntMapStringMap: Map<Map<Boolean, Int>, String> = Map(Map(true, 42), "in map with map keys")
      
      // Listings
      stringSetListing: Listing<Set<String>> = new { Set("in set in listing") }
      intListingListing: Listing<Listing<Int>> = new { new { 1337 } new { 100 } }
      
      // Mappings
      intStringMapping: Mapping<Int, String> = new { [42] = "in map" }
      stringStringSetMapping: Mapping<String, Set<String>> = new { ["key"] = Set("in set in map") }
      
      // Map & Mappings with structured keys
      intSetListStringMap: Map<List<Set<Int>>, String> = Map(List(Set(27)), "in map with structured key")
      typedStringMap: Map<Foo, String> = Map(
        new Foo { value = 1 }, "using typed objects",
        new Foo { value = 2 }, "also works")
      dynamicStringMap: Map<Dynamic, String> = Map(
        new Dynamic { value = 42 }, "using Dynamics",
        new Dynamic { hello = "world" }, "also works")
      
      intListingStringMapping: Mapping<Listing<Int>, String> = new {
        [new Listing { 42 1337 }] = "structured key works"
      }
      intSetListStringMapping: Mapping<List<Set<Int>>, String> = new {
        [List(Set(27))] = "in mapping with structured key"
      }
      local intListing: Listing<Int> = new { 0 0 7 }
      thisOneGoesToEleven: Mapping<List<Set<Int>>, Map<Listing<Int>, Mapping<Int, String>>> = new {
        [List(Set(0), Set(0), Set(7))] = Map(intListing, intStringMapping)
      }
    """
        .trimIndent()
    val result = ConfigEvaluator.preconfigured().forKotlin().evaluate(text(code))
    assertDoesNotThrow { result.to<KotlinGenericTypesTest>() }
      .apply {
        assertThat(typedStringMap.keys).isEqualTo(setOf(TypedKey(1), TypedKey(2)))
        assertThat(dynamicStringMap.keys)
          .isEqualTo(setOf(hashMapOf("hello" to "world"), hashMapOf("value" to 42.toLong())))
      }
  }
}

package org.pkl.core.stdlib

import org.pkl.core.runtime.Identifier
import org.junit.jupiter.api.Assertions.assertTrue

import org.junit.jupiter.api.Test

class PathConverterSupportTest {
  @Test
  fun `exact path matches`() {
    val pathSpec = listOf(Identifier.get("foo"), Identifier.get("bar"), Identifier.get("baz"))
    val pathPartSpec = listOf(Identifier.get("foo"), Identifier.get("bar"), Identifier.get("baz"))
    assertTrue(PathConverterSupport.pathMatches(pathSpec, pathPartSpec))
  }

  @Test
  fun `wildcard properties`() {
    val pathSpec = listOf(Identifier.get("foo"), PklConverter.WILDCARD_PROPERTY, Identifier.get("baz"))
    val pathPartSpec = listOf(Identifier.get("foo"), Identifier.get("bar"), Identifier.get("baz"))
    assertTrue(PathConverterSupport.pathMatches(pathSpec, pathPartSpec))
  }

  @Test
  fun `wildcard elements`() {
    val pathSpec = listOf(Identifier.get("foo"), PklConverter.WILDCARD_ELEMENT, Identifier.get("baz"))
    val pathPartSpec = listOf(Identifier.get("foo"), 0, Identifier.get("baz"))
    assertTrue(PathConverterSupport.pathMatches(pathSpec, pathPartSpec))
  }
}

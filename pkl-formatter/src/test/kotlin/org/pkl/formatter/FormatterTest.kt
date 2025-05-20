/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.formatter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FormatterTest {
  
  @Test
  fun `rule 0001 - Line width`() {
    checkFormat(
      code =
        """
        module reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaly.loooooooooooooooooooooooooooooooog.naaaaaaaaaaaaaaaaaaaaaaaaaaaaame
        extends "reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaalyLongModule.pkl"
        
        /// this property has a reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaly long doc comment
        property = if (reallyLongVariableName) anotherReallyLongName else evenLongerVariableName + anotherReallyLongName
        
        longString = "reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaly long string"
        
        shortProperty = 1
        
        reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaalyLongVariableName = 10
        
        reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaalyLongVariableName2 =
          if (reallyLongVariableName) anotherReallyLongName else evenLongerVariableName + anotherReallyLongName
        """,
      expected =
        """
        module
          reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaly
            .loooooooooooooooooooooooooooooooog
            .naaaaaaaaaaaaaaaaaaaaaaaaaaaaame
        extends
          "reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaalyLongModule.pkl"
        
        /// this property has a reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaly long doc comment
        property =
          if (reallyLongVariableName)
            anotherReallyLongName
          else
            evenLongerVariableName + anotherReallyLongName
        
        longString =
          "reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaly long string"
        
        shortProperty = 1
        
        reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaalyLongVariableName
          = 10
        
        reaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaalyLongVariableName2
          =
          if (reallyLongVariableName)
            anotherReallyLongName
          else
            evenLongerVariableName + anotherReallyLongName
        
        """,
    )
  }

  @Test
  fun `rule 0005 - Module definitions`() {
    checkFormat(
      code =
        """
        // comment
        // one
        
        
        /// doc comment
        
        open
          module
            foo
            .bar
        
        amends
          "baz.pkl"
        """,
      expected =
        """
        // comment
        // one
        
        /// doc comment
        open module foo.bar
        amends "baz.pkl"
        
        """,
    )
  }

  @Test
  fun `rule 0006 - Imports`() {
    checkFormat(
      code =
        """
        // top level comment
        
        import "@foo/Foo.pkl" as foo
        import* "**.pkl"
        
        import "package://example.com/myPackage@1.0.0#/Qux.pkl"
        
        
        import "https://example.com/baz.pkl"
        import "..."
        import "@bar/Bar.pkl"
        """,
      expected =
        """
        // top level comment
        
        import "https://example.com/baz.pkl"
        import "package://example.com/myPackage@1.0.0#/Qux.pkl"
        
        import "@bar/Bar.pkl"
        import "@foo/Foo.pkl" as foo
        
        import* "**.pkl"
        import "..."
        
        """,
    )
  }

  @Test
  fun `rule 0009 - Modifiers`() {
    checkFormat(
      code =
        """
        hidden const foo = 1
        
        open local class Foo {}
        """,
      expected =
        """
        const hidden foo = 1
        
        local open class Foo {}
        
        """,
    )
  }
  
  @Test
  fun `rule 0012 - Object elements`() {
    checkFormat(
      code =
        """
        foo: Listing<Int> = new {1 2   3; 4;5 6  7}
        
        bar: Listing<Int> = new { 1 2
          3
          4
        }
        
        lineIsTooBig: Listing<Int> = new { 999999; 1000000; 1000001; 1000002; 1000003; 1000004; 1000005; 1000006; 1000007; 1000008; 1000009 }
        
        lineIsTooBig2 { 999999; 1000000; 1000001; 1000002; 1000003; 1000004; 1000005; 1000006; 1000007; 1000008; 1000009; 1000010 }
        """,
      expected =
        """
        foo: Listing<Int> = new { 1; 2; 3; 4; 5; 6; 7 }
        
        bar: Listing<Int> = new {
          1
          2
          3
          4
        }
        
        lineIsTooBig: Listing<Int> = new {
          999999
          1000000
          1000001
          1000002
          1000003
          1000004
          1000005
          1000006
          1000007
          1000008
          1000009
        }
        
        lineIsTooBig2 {
          999999
          1000000
          1000001
          1000002
          1000003
          1000004
          1000005
          1000006
          1000007
          1000008
          1000009
          1000010
        }
        
        """,
    )
  }

  @Test
  fun `rule 0019 - Type aliases`() {
    checkFormat(
      code =
        """
        typealias
          Foo
          =
          String
        """,
      expected =
        """
        typealias Foo = String
        
        """,
    )
  }

  private fun checkFormat(code: String, expected: String) {
    val formatter = Formatter()
    val actual = formatter.format(code.trimIndent())
    assertThat(actual).isEqualTo(expected.trimIndent())
  }
}

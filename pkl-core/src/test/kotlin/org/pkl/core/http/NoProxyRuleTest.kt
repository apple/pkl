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
package org.pkl.core.http

import java.net.URI
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("HttpUrlsUsage")
class NoProxyRuleTest {
  @Test
  fun wildcard() {
    val noProxyRule = NoProxyRule("*")
    assertTrue(noProxyRule.matches(URI("https://foo.com")))
    assertTrue(noProxyRule.matches(URI("https://bar.com")))
    assertTrue(noProxyRule.matches(URI("https://foo:5000")))
  }

  @Test
  fun `hostname matching`() {
    val noProxyRule = NoProxyRule("foo.com")
    assertTrue(noProxyRule.matches(URI("https://foo.com")))
    assertTrue(noProxyRule.matches(URI("http://foo.com")))
    assertTrue(noProxyRule.matches(URI("https://foo.com:5000")))
    assertTrue(noProxyRule.matches(URI("https://FOO.COM")))
    assertTrue(noProxyRule.matches(URI("https://bar.foo.com")))
    assertFalse(noProxyRule.matches(URI("https://bar.foo.com.bar")))
    assertFalse(noProxyRule.matches(URI("https://bar.foocom")))
    assertFalse(noProxyRule.matches(URI("https://fooo.com")))
    assertFalse(noProxyRule.matches(URI("https://ooofoo.com")))
    assertFalse(noProxyRule.matches(URI("pkl:foo.com")))
  }

  @Test
  fun `hostname matching, leading dot`() {
    val noProxyRule = NoProxyRule(".foo.com")
    assertTrue(noProxyRule.matches(URI("https://foo.com")))
    assertTrue(noProxyRule.matches(URI("http://foo.com")))
    assertTrue(noProxyRule.matches(URI("https://foo.com:5000")))
    assertTrue(noProxyRule.matches(URI("https://FOO.COM")))
    assertTrue(noProxyRule.matches(URI("https://bar.foo.com")))
    assertFalse(noProxyRule.matches(URI("https://bar.foo.com.bar")))
    assertFalse(noProxyRule.matches(URI("https://bar.foocom")))
    assertFalse(noProxyRule.matches(URI("https://fooo.com")))
    assertFalse(noProxyRule.matches(URI("https://ooofoo.com")))
    assertFalse(noProxyRule.matches(URI("pkl:foo.com")))
  }

  @Test
  fun `hostname matching, with port`() {
    val noProxyRule = NoProxyRule("foo.com:5000")
    assertTrue(noProxyRule.matches(URI("https://foo.com:5000")))
    assertFalse(noProxyRule.matches(URI("https://foo.com")))
    assertFalse(noProxyRule.matches(URI("https://foo.com:3000")))
  }

  @Test
  fun `ipv4 address literal matching`() {
    val noProxyRule = NoProxyRule("192.168.1.1")
    assertTrue(noProxyRule.matches(URI("http://192.168.1.1:5000")))
    assertTrue(noProxyRule.matches(URI("http://192.168.1.1")))
    assertTrue(noProxyRule.matches(URI("https://192.168.1.1")))
    assertFalse(noProxyRule.matches(URI("https://192.168.1.0")))
    assertFalse(noProxyRule.matches(URI("https://192.168.1.0:5000")))
  }

  @Test
  fun `ipv4 address literal matching, with port`() {
    val noProxyRule = NoProxyRule("192.168.1.1:5000")
    assertTrue(noProxyRule.matches(URI("http://192.168.1.1:5000")))
    assertTrue(noProxyRule.matches(URI("https://192.168.1.1:5000")))
    assertFalse(noProxyRule.matches(URI("http://192.168.1.1")))
    assertFalse(noProxyRule.matches(URI("https://192.168.1.1")))
    assertFalse(noProxyRule.matches(URI("http://192.168.1.1:3000")))
    assertFalse(noProxyRule.matches(URI("https://192.168.1.1:3000")))
  }

  @Test
  fun `ipv6 address literal matching`() {
    val noProxyRule = NoProxyRule("::1")
    assertTrue(noProxyRule.matches(URI("http://[::1]")))
    assertTrue(noProxyRule.matches(URI("http://[::1]:5000")))
    assertTrue(noProxyRule.matches(URI("https://[::1]")))
    assertTrue(noProxyRule.matches(URI("https://[0000:0000:0000:0000:0000:0000:0000:0001]")))
    assertFalse(noProxyRule.matches(URI("https://[::2]")))
    assertFalse(noProxyRule.matches(URI("https://[::2]:5000")))
  }

  @Test
  fun `ipv6 address literal matching, with port`() {
    val noProxyRule = NoProxyRule("[::1]:5000")
    assertTrue(noProxyRule.matches(URI("http://[::1]:5000")))
    assertTrue(noProxyRule.matches(URI("https://[0000:0000:0000:0000:0000:0000:0000:0001]:5000")))
    assertFalse(noProxyRule.matches(URI("http://[::1]")))
    assertFalse(noProxyRule.matches(URI("https://[::1]")))
    assertFalse(noProxyRule.matches(URI("https://[::2]")))
    assertFalse(noProxyRule.matches(URI("https://[::2]:5000")))
  }

  @Test
  fun `ipv4 port from protocol`() {
    val noProxyRuleHttp = NoProxyRule("192.168.1.1:80")
    assertTrue(noProxyRuleHttp.matches(URI("http://192.168.1.1")))
    assertTrue(noProxyRuleHttp.matches(URI("http://192.168.1.1:80")))
    assertTrue(noProxyRuleHttp.matches(URI("https://192.168.1.1:80")))
    assertFalse(noProxyRuleHttp.matches(URI("https://192.168.1.1")))
    assertFalse(noProxyRuleHttp.matches(URI("https://192.168.1.1:5000")))

    val noProxyRuleHttps = NoProxyRule("192.168.1.1:443")
    assertTrue(noProxyRuleHttps.matches(URI("https://192.168.1.1")))
    assertTrue(noProxyRuleHttps.matches(URI("http://192.168.1.1:443")))
    assertFalse(noProxyRuleHttps.matches(URI("http://192.168.1.1")))
    assertFalse(noProxyRuleHttps.matches(URI("https://192.168.1.1:80")))
  }

  @Test
  fun `ipv4 cidr block matching`() {
    val noProxyRule1 = NoProxyRule("10.0.0.0/16")
    assertTrue(noProxyRule1.matches(URI("https://10.0.0.0")))
    assertTrue(noProxyRule1.matches(URI("https://10.0.255.255")))
    assertTrue(noProxyRule1.matches(URI("https://10.0.255.255:5000")))
    assertFalse(noProxyRule1.matches(URI("https://10.1.0.0")))
    assertFalse(noProxyRule1.matches(URI("https://11.0.0.0")))
    assertFalse(noProxyRule1.matches(URI("https://9.255.255.255")))
    assertFalse(noProxyRule1.matches(URI("https://9.255.255.255:5000")))

    val noProxyRule2 = NoProxyRule("10.0.0.0/32")
    assertTrue(noProxyRule2.matches(URI("https://10.0.0.0")))
    assertTrue(noProxyRule2.matches(URI("https://10.0.0.0:5000")))
    assertFalse(noProxyRule2.matches(URI("https://10.0.0.1")))
    assertFalse(noProxyRule2.matches(URI("https://9.255.255.55:5000")))
  }

  @Test
  fun `ipv6 cidr block matching`() {
    val noProxyRule1 = NoProxyRule("1000::ff/32")
    assertTrue(noProxyRule1.matches(URI("https://[1000::]")))
    assertTrue(noProxyRule1.matches(URI("https://[1000:0:ffff:ffff:ffff:ffff:ffff:ffff]")))
    assertFalse(noProxyRule1.matches(URI("https://[999::]")))
    assertFalse(noProxyRule1.matches(URI("https://[1000:1::]")))

    val noProxyRule2 = NoProxyRule("1000::ff/128")
    assertTrue(noProxyRule2.matches(URI("https://[1000::ff]")))
    assertFalse(noProxyRule2.matches(URI("https://[999::]")))
    assertFalse(noProxyRule2.matches(URI("https://[1001::]")))
  }
}

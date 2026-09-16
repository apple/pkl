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
package org.pkl.config.java.mapper;

import static org.assertj.core.api.Assertions.*;
import static org.pkl.core.ModuleSource.modulePath;

import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.pkl.core.Evaluator;
import org.pkl.core.PModule;

public class PUrlToURITest {
  private static final Evaluator evaluator = Evaluator.preconfigured();

  private static final PModule module =
      evaluator.evaluate(modulePath("org/pkl/config/java/mapper/PUrlToURITest.pkl"));

  private static final ValueMapper mapper = ValueMapperBuilder.preconfigured().build();

  @AfterAll
  public static void afterAll() {
    evaluator.close();
  }

  @Test
  public void absolute() {
    assertThat(map("absolute"))
        .isEqualTo(URI.create("https://user:pw@example.com:8080/a/b?q=1#frag"));
  }

  @Test
  public void percentEncoding() {
    // characters that cannot appear literally are encoded, and what is already encoded is kept
    assertThat(map("literalSpace")).isEqualTo(URI.create("https://example.com/a%20b"));
    assertThat(map("encodedSpace")).isEqualTo(URI.create("https://example.com/a%20b"));
    assertThat(map("encodedSlash")).isEqualTo(URI.create("https://example.com/a%2Fb"));
    assertThat(map("nonAscii")).isEqualTo(URI.create("https://example.com/%C3%A9"));
  }

  @Test
  public void relative() {
    assertThat(map("relative")).isEqualTo(URI.create("/foo/bar"));
    // a first segment holding a ":" is preceded by a dot-segment so that it isn't read as a scheme
    assertThat(map("colonSegment")).isEqualTo(URI.create("./a:b"));
  }

  @Test
  public void authority() {
    assertThat(map("rootless")).isEqualTo(URI.create("mailto:user@example.com"));
    assertThat(map("noAuthority")).isEqualTo(URI.create("file:/tmp"));
    assertThat(map("emptyAuthority")).isEqualTo(URI.create("file:///tmp"));
    assertThat(map("emptyQuery")).isEqualTo(URI.create("https://example.com/?"));
    assertThat(map("ipv6Zone")).isEqualTo(URI.create("http://[fe80::1%25eth0]/p"));
  }

  @Test
  public void constructed() {
    assertThat(map("constructed")).isEqualTo(URI.create("https://example.com/a%20b?x%20y"));
  }

  @Test
  public void ipvFuture() {
    // `java.net.URI` doesn't know IPvFuture addresses
    assertThatThrownBy(() -> map("ipvFuture"))
        .isInstanceOf(ConversionException.class)
        .hasMessageContaining("http://[v1.fe80::a+en1]/")
        .hasCauseInstanceOf(URISyntaxException.class);
  }

  private static URI map(String propertyName) {
    var url = module.get(propertyName);
    assert url != null;
    return mapper.map(url, URI.class);
  }
}

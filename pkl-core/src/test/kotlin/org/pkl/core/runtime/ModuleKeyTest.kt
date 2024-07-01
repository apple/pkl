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
package org.pkl.core.runtime

//
// import java.io.File;
// import java.io.IOException;
// import java.net.URI;
// import java.net.URISyntaxException;
//
// import org.pkl.core.resolve.ModuleKey;
// import org.pkl.core.resolve.ModuleKeys;
// import org.junit.Rule;
// import org.junit.Test;
// import org.junit.rules.TemporaryFolder;
//
// import static org.junit.Assert.assertEquals;
// import static org.junit.Assert.assertFalse;
// import static org.junit.Assert.assertTrue;
//
//// some parts of ModuleKey are tested as part of DefaultModuleResolverTest
// public class ModuleKeyTest {
//  @Rule
//  public TemporaryFolder folder = new TemporaryFolder();
//
//  @Test
//  public void stdLibModule() {
//    ModuleKey module = ModuleKeys.standardLibrary("base");
//
//    assertEquals("pkl", module.getUri().getScheme());
//    assertEquals("base", module.getName());
//    assertEquals("pkl:base", module.getUri().toString());
//    assertTrue(ModuleKeys.isBaseModule(module));
//
//    ModuleKeys.StandardLibrary other = ModuleKeys.standardLibrary(URI("pkl:test"));
//    assertFalse(ModuleKeys.isBaseModule(other));
//  }
//
//  @Test
//  public void fileModule() throws IOException {
//    File file = folder.newFile("baz.pkl");
//
//    ModuleKey module = new ModuleKeys.Gene/*/ricUrl()File(file.toPath());
//
//    assertEquals("file", module.getScheme());
//    assertEquals("baz", module.getName());
//    assertEquals("file://" + file.getAbsolutePath(), module.getUri());
//    assertFalse(module.isBaseModule());
//  }
//
//  @Test
//  public void urlModule() throws URISyntaxException {
//    ModuleKey module = new ModuleKey.Url(new URI("https://apple.com/baz.pkl"));
//    assertEquals("https", module.getScheme());
//    assertEquals("baz", module.getName());
//    assertEquals("https://apple.com/baz.pkl", module.getUri());
//    assertFalse(module.isBaseModule());
//  }
//
//  @Test
//  public void libraryPathModule() {
//    ModuleKey module = new ModuleKey.Lib("foo.bar.baz");
//
//    assertEquals("lib", module.getScheme());
//    assertEquals("baz", module.getName());
//    assertEquals("module:foo.bar.baz", module.getUri());
//    assertFalse(module.isBaseModule());
//  }
//
//  @Test
//  public void literalModule() {
//    ModuleKey module = new ModuleKey.Virtual("name", "x=1");
//
//    assertEquals("literal", module.getScheme());
//    assertEquals("name", module.getName());
//    assertEquals("literal:name", module.getUri());
//    assertFalse(module.isBaseModule());
//  }
// }

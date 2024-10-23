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
package org.pkl.core.runtime

//
// import java.io.IOException;
// import java.net.URI;
// import java.net.URISyntaxException;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.util.Arrays;
// import java.util.Collections;
//
// import org.pkl.core.EvalOptions;
// import org.pkl.core.EvalException;
// import org.pkl.core.resolve.ModuleKey;
// import org.pkl.core.resolve.ModuleKeyFactories;
// import org.pkl.core.resolve.ModuleKeys;
// import com.oracle.truffle.api.source.SourceSection;
// import org.junit.Ignore;
// import org.junit.Test;
//
// import static org.junit.Assert.assertEquals;
// import static org.junit.Assert.assertTrue;
//
// public class DefaultModuleResolverTest {
//  private final SourceSection sourceSection = VmUtils.unavailableSourceSection();
//  private final ModuleResolver resolver =
//      new ModuleResolver(ModuleKeyFactories.namedModuleOnClassPath,
// EvalOptions.namedModuleOnClassPath.getAllowedModules());
//  private final ModuleKey fileUrlModule;
//  private final ModuleKey httpsUrlModule;
//  private final ModuleKey literalUrlModule;
//
//  {
//    try {
//      fileUrlModule = ModuleKeys.genericUrl(new URI("file:///path/script.pkl"),
// ModuleKeys.FULL_TRUST);
//      httpsUrlModule = ModuleKeys.genericUrl(new URI("https://some.domain.com/path/script.pkl"),
// ModuleKeys.FULL_TRUST);
//      literalUrlModule = ModuleKeys.synthetic("myLiteralModule", "my literal source code",
// ModuleKeys.FULL_TRUST);
//    } catch (URISyntaxException e) {
//      throw new RuntimeException(e);
//    }
//  }
//
//  @Test
//  public void importAbsolutePathFromFileUrl() throws IOException {
//    ModuleKey result = resolver.resolve("/import/file.pkl", fileUrlModule, sourceSection);
//    assertTrue(result instanceof ModuleKey.Url);
//    assertEquals("file:/import/file.pkl", result.toString());
//  }
//
//  @Test
//  public void importRelativePathFromFileUrl() throws IOException {
//    ModuleKey result = resolver.resolve("import/file.pkl", fileUrlModule, sourceSection);
//    assertTrue(result instanceof ModuleKey.Url);
//    assertEquals("file:/path/import/file.pkl", result.toString());
//  }
//
//  @Test
//  public void importFileUrlFromFileUrl() throws IOException {
//    ModuleKey result = resolver.resolve("file:///import/file.pkl", fileUrlModule, sourceSection);
//    assertTrue(result instanceof ModuleKey.Url);
//    assertEquals("file:///import/file.pkl", result.toString());
//  }
//
//  @Test
//  public void importHttpsUrlFromFileUrl() throws IOException {
//    ModuleKey result = resolver.resolve("https://other.domain.com/path2/script2.pkl",
// fileUrlModule, sourceSection);
//    assertTrue(result instanceof ModuleKey.Url);
//    assertEquals("https://other.domain.com/path2/script2.pkl", result.toString());
//  }
//
//  @Test
//  public void importStdLibModuleFromFileUrl() {
//    ModuleKey result = resolver.resolve("pkl:base", fileUrlModule, sourceSection);
//    assertTrue(result instanceof ModuleKey.StandardLibrary);
//    assertEquals("pkl:base", result.toString());
//  }
//
//  @Test
//  public void importAbsolutePathFromHttpsUrl() throws IOException {
//    ModuleKey result = resolver.resolve("/import/file.pkl", httpsUrlModule, sourceSection);
//    assertTrue(result instanceof ModuleKey.Url);
//    assertEquals("https://some.domain.com/import/file.pkl", result.toString());
//  }
//
//  @Test
//  public void importRelativePathFromHttpsUrl() throws IOException {
//    ModuleKey result = resolver.resolve("import/file.pkl", httpsUrlModule, sourceSection);
//    assertTrue(result instanceof ModuleKey.Url);
//    assertEquals("https://some.domain.com/path/import/file.pkl", result.toString());
//  }
//
//  @Test(expected = EvalException.class)
//  public void importFileUrlFromHttpsUrl() throws IOException {
//    resolver.resolve("file:///import/file.pkl", httpsUrlModule, sourceSection);
//  }
//
//  @Test
//  public void importHttpsUrlFromHttpsUrl() throws IOException {
//    ModuleKey result = resolver.resolve("https://other.domain.com/path2/script2.pkl",
// httpsUrlModule, sourceSection);
//    assertTrue(result instanceof ModuleKey.Url);
//    assertEquals("https://other.domain.com/path2/script2.pkl", result.toString());
//  }
//
//  @Test
//  public void importStdLibModuleFromHttpsUrl() {
//    ModuleKey result = resolver.resolve("pkl:base", httpsUrlModule, sourceSection);
//    assertTrue(result instanceof ModuleKey.StandardLibrary);
//    assertEquals("pkl:base", result.toString());
//  }
//
//  @Test
//  public void importAbsolutePathFromLiteral() throws IOException {
//    Path path = Files.createTempFile("file", ".pkl");
//    try {
//      ModuleKey result = resolver.resolve(path.toString(), literalUrlModule, sourceSection);
//      assertTrue(result instanceof ModuleKey.File);
//      assertEquals(path.toString(), result.toString());
//    } finally {
//      //noinspection ThrowFromFinallyBlock
//      Files.delete(path);
//    }
//  }
//
//  @Ignore("not sure how to test relative path behavior")
//  @Test(expected = EvalException.class)
//  public void importRelativePathFromLiteral() throws IOException {
//    resolver.resolve("import/file.pkl", literalUrlModule, sourceSection);
//  }
//
//  @Test
//  public void importFileUrlFromLiteral() throws IOException {
//    ModuleKey result = resolver.resolve("file:///import/file.pkl", literalUrlModule,
// sourceSection);
//    assertTrue(result instanceof ModuleKey.Url);
//    assertEquals("file:///import/file.pkl", result.toString());
//  }
//
//  @Test
//  public void importHttpsUrlFromLiteral() throws IOException {
//    ModuleKey result = resolver.resolve("https://other.domain.com/path2/script2.pkl",
// literalUrlModule, sourceSection);
//    assertTrue(result instanceof ModuleKey.Url);
//    assertEquals("https://other.domain.com/path2/script2.pkl", result.toString());
//  }
//
//  @Test
//  public void importStdLibModuleFromLiteral() {
//    ModuleKey result = resolver.resolve("pkl:base", literalUrlModule, sourceSection);
//    assertTrue(result instanceof ModuleKey.StandardLibrary);
//    assertEquals("pkl:base", result.toString());
//  }
//
//  @Test(expected = EvalException.class)
//  public void importWithNotAllowedScheme() {
//    resolver.resolve("http://some.domain.com/path/script.pkl", fileUrlModule, sourceSection);
//  }
//
//  @Test(expected = EvalException.class)
//  public void invalidImportUrl() {
//    resolver.resolve("unknown://bar", fileUrlModule, sourceSection);
//  }
//
//  @Test
//  public void importMatchesAllowedModules() {
//    ModuleResolver resolver =
//        new ModuleResolver(
//            Arrays.asList("file:///pkl/.*", "https://.*\\.apple\\.com"));
//
//    resolver.resolve("file:///pkl/foo.pkl", fileUrlModule, sourceSection);
//    resolver.resolve("https://example.com/foo.pkl", fileUrlModule, sourceSection);
//  }
//
//  @Test(expected = EvalException.class)
//  public void importDoesNotMatchAllowedModules() {
//    ModuleResolver resolver =
//        new ModuleResolver(
//            Collections.singletonList("https://.*\\.apple\\.com"));
//
//    resolver.resolve("https://evil.corp/foo.pkl", fileUrlModule, sourceSection);
//  }
//
//  @Test(expected = EvalException.class)
//  public void emptyAllowedModulesMatchesNothing() {
//    ModuleResolver resolver =
//        new ModuleResolver(
//            Collections.emptyList());
//
//    resolver.resolve("pkl:base", fileUrlModule, sourceSection);
//  }
// }

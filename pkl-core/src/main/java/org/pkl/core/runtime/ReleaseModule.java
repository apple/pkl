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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import java.net.URI;

public final class ReleaseModule extends StdLibModule {
  private static final VmTyped instance = VmUtils.createEmptyModule();

  static {
    loadModule(URI.create("pkl:release"), instance);
  }

  public static VmTyped getModule() {
    return instance;
  }

  public static VmClass getReleaseClass() {
    return ReleaseModule.ReleaseClass.instance;
  }

  public static VmClass getSourceCodeClass() {
    return ReleaseModule.SourceCodeClass.instance;
  }

  public static VmClass getDocumentationClass() {
    return ReleaseModule.DocumentationClass.instance;
  }

  public static VmClass getStandardLibraryClass() {
    return ReleaseModule.StandardLibraryClass.instance;
  }

  private static final class ReleaseClass {
    static final VmClass instance = loadClass("Release");
  }

  private static final class SourceCodeClass {
    static final VmClass instance = loadClass("SourceCode");
  }

  private static final class DocumentationClass {
    static final VmClass instance = loadClass("Documentation");
  }

  private static final class StandardLibraryClass {
    static final VmClass instance = loadClass("StandardLibrary");
  }

  @CompilerDirectives.TruffleBoundary
  private static VmClass loadClass(String className) {
    var theModule = getModule();
    return (VmClass) VmUtils.readMember(theModule, Identifier.get(className));
  }
}

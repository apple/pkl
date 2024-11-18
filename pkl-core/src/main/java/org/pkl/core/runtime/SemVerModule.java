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
package org.pkl.core.runtime;

import static org.pkl.core.PClassInfo.pklSemverUri;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class SemVerModule extends StdLibModule {
  private static final VmTyped instance = VmUtils.createEmptyModule();

  static {
    loadModule(pklSemverUri, instance);
  }

  private SemVerModule() {}

  public static VmTyped getModule() {
    return instance;
  }

  public static VmClass getVersionClass() {
    return VersionClass.instance;
  }

  private static final class VersionClass {
    static final VmClass instance = loadClass("Version");
  }

  @TruffleBoundary
  private static VmClass loadClass(String className) {
    var theModule = getModule();
    return (VmClass) VmUtils.readMember(theModule, Identifier.get(className));
  }
}

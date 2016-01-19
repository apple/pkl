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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.net.URI;

public final class XmlModule extends StdLibModule {
  private static final VmTyped instance = VmUtils.createEmptyModule();

  static {
    loadModule(URI.create("pkl:xml"), instance);
  }

  public static VmTyped getModule() {
    return instance;
  }

  public static VmClass getInlineClass() {
    return InlineClass.instance;
  }

  public static VmClass getCommentClass() {
    return CommentClass.instance;
  }

  public static VmClass getCDataClass() {
    return CDataClass.instance;
  }

  private static final class InlineClass {
    static final VmClass instance = loadClass("Inline");
  }

  private static final class CommentClass {
    static final VmClass instance = loadClass("Comment");
  }

  private static final class CDataClass {
    static final VmClass instance = loadClass("CData");
  }

  @TruffleBoundary
  private static VmClass loadClass(String className) {
    var theModule = getModule();
    return (VmClass) VmUtils.readMember(theModule, Identifier.get(className));
  }
}

/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

public final class SyntaxModule extends StdLibModule {
  private static final VmTyped instance = VmUtils.createEmptyModule();

  static {
    loadModule(URI.create("pkl:syntax"), instance);
  }

  public static VmTyped getModule() {
    return instance;
  }

  public static VmClass getNodeClass() {
    return NodeClass.instance;
  }

  public static VmClass getSpanClass() {
    return SpanClass.instance;
  }

  public static VmClass getParserErrorClass() {
    return ParserErrorClass.instance;
  }

  private static final class NodeClass {
    static final VmClass instance = loadClass("Node");
  }

  private static final class SpanClass {
    static final VmClass instance = loadClass("Span");
  }

  private static final class ParserErrorClass {
    static final VmClass instance = loadClass("ParserError");
  }

  @CompilerDirectives.TruffleBoundary
  private static VmClass loadClass(String className) {
    var theModule = getModule();
    return (VmClass) VmUtils.readMember(theModule, Identifier.get(className));
  }
}

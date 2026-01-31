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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.net.URI;

public final class CommandModule extends StdLibModule {
  static final VmTyped instance = VmUtils.createEmptyModule();

  static {
    loadModule(URI.create("pkl:Command"), instance);
  }

  private CommandModule() {}

  public static VmTyped getModule() {
    return instance;
  }

  public static VmClass getCommandInfoClass() {
    return CommandInfoClass.instance;
  }

  public static VmClass getBaseFlagClass() {
    return BaseFlagClass.instance;
  }

  public static VmClass getFlagClass() {
    return FlagClass.instance;
  }

  public static VmClass getBooleanFlagClass() {
    return BooleanFlagClass.instance;
  }

  public static VmClass getCountedFlagClass() {
    return CountedFlagClass.instance;
  }

  public static VmClass getArgumentClass() {
    return ArgumentClass.instance;
  }

  public static VmClass getImportClass() {
    return ImportClass.instance;
  }

  private static final class CommandInfoClass {
    static final VmClass instance = loadClass("CommandInfo");
  }

  private static final class BaseFlagClass {
    static final VmClass instance = loadClass("BaseFlag");
  }

  private static final class FlagClass {
    static final VmClass instance = loadClass("Flag");
  }

  private static final class BooleanFlagClass {
    static final VmClass instance = loadClass("BooleanFlag");
  }

  private static final class CountedFlagClass {
    static final VmClass instance = loadClass("CountedFlag");
  }

  private static final class ArgumentClass {
    static final VmClass instance = loadClass("Argument");
  }

  private static final class ImportClass {
    static final VmClass instance = loadClass("Import");
  }

  @TruffleBoundary
  private static VmClass loadClass(String className) {
    var theModule = getModule();
    return (VmClass) VmUtils.readMember(theModule, Identifier.get(className));
  }
}

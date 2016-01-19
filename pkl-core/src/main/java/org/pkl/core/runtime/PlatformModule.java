/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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

public final class PlatformModule extends StdLibModule {
  private static final VmTyped instance = VmUtils.createEmptyModule();

  static {
    loadModule(URI.create("pkl:platform"), instance);
  }

  public static VmTyped getModule() {
    return instance;
  }

  public static VmClass getPlatformClass() {
    return PlatformClass.instance;
  }

  public static VmClass getLanguageClass() {
    return LanguageClass.instance;
  }

  public static VmClass getRuntimeClass() {
    return RuntimeClass.instance;
  }

  public static VmClass getVirtualMachineClass() {
    return VirtualMachineClass.instance;
  }

  public static VmClass getOperatingSystemClass() {
    return OperatingSystemClass.instance;
  }

  public static VmClass getProcessorClass() {
    return ProcessorClass.instance;
  }

  private static final class PlatformClass {
    static final VmClass instance = loadClass("Platform");
  }

  private static final class LanguageClass {
    static final VmClass instance = loadClass("Language");
  }

  private static final class RuntimeClass {
    static final VmClass instance = loadClass("Runtime");
  }

  private static final class VirtualMachineClass {
    static final VmClass instance = loadClass("VirtualMachine");
  }

  private static final class OperatingSystemClass {
    static final VmClass instance = loadClass("OperatingSystem");
  }

  private static final class ProcessorClass {
    static final VmClass instance = loadClass("Processor");
  }

  @CompilerDirectives.TruffleBoundary
  private static VmClass loadClass(String className) {
    var theModule = getModule();
    return (VmClass) VmUtils.readMember(theModule, Identifier.get(className));
  }
}

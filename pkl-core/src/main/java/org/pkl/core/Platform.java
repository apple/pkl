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
package org.pkl.core;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import org.graalvm.home.Version;

/**
 * Information about the Pkl release that the current program runs on. This class is the Java
 * equivalent of standard library module {@code pkl.platform}.
 */
public record Platform(
    Language language,
    Runtime runtime,
    VirtualMachine virtualMachine,
    OperatingSystem operatingSystem,
    Processor processor) {
  private static final Platform CURRENT;

  static {
    var pklVersion = Release.current().version().toString();
    var osName = System.getProperty("os.name");
    if (osName.equals("Mac OS X")) osName = "macOS";
    if (osName.contains("Windows")) osName = "Windows";
    var osVersion = System.getProperty("os.version");
    var architecture = System.getProperty("os.arch");

    var runtimeName = Truffle.getRuntime().getName();
    var runtimeVersion = Version.getCurrent().toString();
    var vmName = TruffleOptions.AOT ? runtimeName : System.getProperty("java.vm.name");
    var vmVersion = TruffleOptions.AOT ? runtimeVersion : System.getProperty("java.vm.version");

    CURRENT =
        new Platform(
            new Language(pklVersion),
            new Runtime(runtimeName, runtimeVersion),
            new VirtualMachine(vmName, vmVersion),
            new OperatingSystem(osName, osVersion),
            new Processor(architecture));
  }

  /** The Pkl release that the current program runs on. */
  public static Platform current() {
    return CURRENT;
  }

  /**
   * The language implementation of a platform.
   *
   * @param version the version of this language implementation
   */
  public record Language(String version) {}

  /**
   * The language runtime of a platform.
   *
   * @param name the name of this language runtime.
   * @param version the version of this language runtime.
   */
  public record Runtime(String name, String version) {}

  /**
   * The virtual machine of a platform.
   *
   * @param name the name of this virtual machine.
   * @param version the version of this virtual machine.
   */
  public record VirtualMachine(String name, String version) {}

  /**
   * The operating system of a platform.
   *
   * @param name the name of this operating system.
   * @param version the version of this operating system.
   */
  public record OperatingSystem(String name, String version) {}

  /**
   * The processor of a platform.
   *
   * @param architecture the instruction set architecture of this processor.
   */
  public record Processor(String architecture) {}
}

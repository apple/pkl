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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.net.URI;

public final class NetModule extends StdLibModule {
  private static final VmTyped instance = VmUtils.createEmptyModule();

  static {
    loadModule(URI.create("pkl:net"), instance);
  }

  private NetModule() {}

  public static VmTyped getModule() {
    return instance;
  }

  public static VmClass getUrlClass() {
    return UrlClass.instance;
  }

  public static VmClass getIpv4AddressClass() {
    return Ipv4AddressClass.instance;
  }

  public static VmClass getIpv6AddressClass() {
    return Ipv6AddressClass.instance;
  }

  public static VmClass getIpv4NetworkClass() {
    return Ipv4NetworkClass.instance;
  }

  public static VmClass getIpv6NetworkClass() {
    return Ipv6NetworkClass.instance;
  }

  public static VmClass getMacAddressClass() {
    return MacAddressClass.instance;
  }

  private static final class UrlClass {
    static final VmClass instance = loadClass("Url");
  }

  private static final class Ipv4AddressClass {
    static final VmClass instance = loadClass("Ipv4Address");
  }

  private static final class Ipv6AddressClass {
    static final VmClass instance = loadClass("Ipv6Address");
  }

  private static final class Ipv4NetworkClass {
    static final VmClass instance = loadClass("Ipv4Network");
  }

  private static final class Ipv6NetworkClass {
    static final VmClass instance = loadClass("Ipv6Network");
  }

  private static final class MacAddressClass {
    static final VmClass instance = loadClass("MacAddress");
  }

  @TruffleBoundary
  private static VmClass loadClass(String className) {
    var theModule = getModule();
    return (VmClass) VmUtils.readMember(theModule, Identifier.get(className));
  }
}

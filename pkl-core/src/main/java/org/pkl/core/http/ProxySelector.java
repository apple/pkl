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
package org.pkl.core.http;

import com.oracle.truffle.api.nodes.ExplodeLoop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;

final class ProxySelector extends java.net.ProxySelector {

  public static final List<Proxy> NO_PROXY = List.of(Proxy.NO_PROXY);

  private final @Nullable List<Proxy> myProxy;
  private final List<NoProxyRule> noProxyRules;
  private final @Nullable java.net.ProxySelector delegate;

  ProxySelector(@Nullable URI proxyAddress, List<String> noProxyRules) {
    this.noProxyRules = noProxyRules.stream().map(NoProxyRule::new).toList();
    if (proxyAddress == null) {
      this.delegate = java.net.ProxySelector.getDefault();
      this.myProxy = null;
    } else {
      if (!proxyAddress.getScheme().equalsIgnoreCase("http")
          || proxyAddress.getHost() == null
          || !proxyAddress.getPath().isEmpty()
          || proxyAddress.getUserInfo() != null) {
        throw new IllegalArgumentException(
            ErrorMessages.create("malformedProxyAddress", proxyAddress));
      }
      this.delegate = null;
      var port = proxyAddress.getPort();
      if (port == -1) {
        port = 80;
      }
      this.myProxy =
          List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyAddress.getHost(), port)));
    }
  }

  @Override
  @ExplodeLoop
  public List<Proxy> select(URI uri) {
    for (var proxyRule : noProxyRules) {
      if (proxyRule.matches(uri)) {
        return NO_PROXY;
      }
    }
    if (delegate != null) {
      return delegate.select(uri);
    }
    assert myProxy != null;
    return myProxy;
  }

  @Override
  public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    /* ignore */
  }
}

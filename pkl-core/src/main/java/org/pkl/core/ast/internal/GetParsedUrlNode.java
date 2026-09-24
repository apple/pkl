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
package org.pkl.core.ast.internal;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.pkl.core.ast.PklNode;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.util.url.UrlFactory;
import org.pkl.core.util.url.UrlParser.Parsed;

public abstract class GetParsedUrlNode extends PklNode {
  @NeverDefault
  public static GetParsedUrlNode create() {
    return GetParsedUrlNodeGen.create();
  }

  @Specialization(guards = "url.hasExtraStorage()")
  protected Parsed evalCached(VmTyped url) {
    return (Parsed) url.getExtraStorage();
  }

  @Specialization
  protected Parsed eval(VmTyped url, @Cached("create()") IndirectCallNode callNode) {
    return UrlFactory.read(url, callNode);
  }

  public abstract Parsed execute(VmTyped url);
}

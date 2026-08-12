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
package org.pkl.core.stdlib.syntax;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.util.ArrayList;
import org.jspecify.annotations.Nullable;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.syntax.SyntaxNodes.GenericNodeData;
import org.pkl.core.stdlib.syntax.SyntaxNodes.SpanData;
import org.pkl.parser.GenericParser;
import org.pkl.parser.GenericParserError;
import org.pkl.parser.syntax.generic.Node;
import org.pkl.parser.syntax.generic.NodeType;

/** Backs the {@code parse*} methods of {@code pkl.syntax#Parser}. */
public final class ParserNodes {
  private ParserNodes() {}

  public abstract static class parseModule extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object evalString(@SuppressWarnings("unused") VmTyped self, String source) {
      try {
        return parseModuleNode(source, null);
      } catch (GenericParserError e) {
        throw exceptionBuilder().evalError("parserError").withHint(e.toString()).build();
      }
    }

    @Specialization
    @TruffleBoundary
    protected Object evalResource(
        @SuppressWarnings("unused") VmTyped self,
        VmTyped source,
        @Cached("create()") IndirectCallNode callNode) {
      var text = (String) VmUtils.readMember(source, Identifier.TEXT, callNode);
      var uri = (String) VmUtils.readMember(source, Identifier.URI, callNode);
      try {
        return parseModuleNode(text, uri);
      } catch (GenericParserError e) {
        throw exceptionBuilder().evalError("parserError").withHint(e.toString()).build();
      }
    }
  }

  public abstract static class parseModuleOrNull extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object evalString(@SuppressWarnings("unused") VmTyped self, String source) {
      try {
        return parseModuleNode(source, null);
      } catch (GenericParserError e) {
        return VmNull.withoutDefault();
      }
    }

    @Specialization
    @TruffleBoundary
    protected Object evalResource(
        @SuppressWarnings("unused") VmTyped self,
        VmTyped source,
        @Cached("create()") IndirectCallNode callNode) {
      var text = (String) VmUtils.readMember(source, Identifier.TEXT);
      var uri = (String) VmUtils.readMember(source, Identifier.URI, callNode);
      try {
        return parseModuleNode(text, uri);
      } catch (GenericParserError e) {
        return VmNull.withoutDefault();
      }
    }
  }

  public abstract static class parseExpression extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object evalString(@SuppressWarnings("unused") VmTyped self, String source) {
      try {
        return parseExpressionNode(source, null);
      } catch (GenericParserError e) {
        throw exceptionBuilder().evalError("parserError").withHint(e.toString()).build();
      }
    }

    @Specialization
    @TruffleBoundary
    protected Object evalResource(
        @SuppressWarnings("unused") VmTyped self,
        VmTyped source,
        @Cached("create()") IndirectCallNode callNode) {
      var text = (String) VmUtils.readMember(source, Identifier.TEXT);
      var uri = (String) VmUtils.readMember(source, Identifier.URI, callNode);
      try {
        return parseExpressionNode(text, uri);
      } catch (GenericParserError e) {
        throw exceptionBuilder().evalError("parserError").withHint(e.toString()).build();
      }
    }
  }

  public abstract static class parseExpressionOrNull extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object evalString(@SuppressWarnings("unused") VmTyped self, String source) {
      try {
        return parseExpressionNode(source, null);
      } catch (GenericParserError e) {
        return VmNull.withoutDefault();
      }
    }

    @Specialization
    @TruffleBoundary
    protected Object evalResource(
        @SuppressWarnings("unused") VmTyped self,
        VmTyped source,
        @Cached("create()") IndirectCallNode callNode) {
      var text = (String) VmUtils.readMember(source, Identifier.TEXT);
      var uri = (String) VmUtils.readMember(source, Identifier.URI, callNode);
      try {
        return parseExpressionNode(text, uri);
      } catch (GenericParserError e) {
        return VmNull.withoutDefault();
      }
    }
  }

  private static VmTyped parseModuleNode(String src, @Nullable String sourceUri) {
    var parser = new GenericParser();
    var root = parser.parseModule(src);
    return convertNode(root, src.toCharArray(), sourceUri);
  }

  private static VmTyped parseExpressionNode(String src, @Nullable String sourceUri) {
    var parser = new GenericParser();
    var root = parser.parseExpressionInput(src);
    return convertNode(root, src.toCharArray(), sourceUri);
  }

  private static VmTyped convertNode(
      Node genericNode, char[] sourceChars, @Nullable String sourceUri) {
    // convert children recursively
    var childrenList = new ArrayList<VmTyped>(genericNode.children.size());
    for (var child : genericNode.children) {
      childrenList.add(convertNode(child, sourceChars, sourceUri));
    }

    // materialize text now so that nodes reused verbatim by `transform`/`format` are
    // self-contained
    if (genericNode.children.isEmpty() || genericNode.type == NodeType.STRING_CHARS) {
      genericNode.text(sourceChars);
    }

    var childrenVm = VmList.create(childrenList.toArray());
    var spanVm = SyntaxNodes.spanFactory.create(new SpanData(genericNode.span, sourceUri));
    var data = new GenericNodeData(genericNode, sourceChars, childrenVm, spanVm);

    var result = SyntaxNodes.genericNodeFactory.create(data);

    // set parent back-reference on each child
    for (var childVm : childrenList) {
      var childData = (GenericNodeData) childVm.getExtraStorage();
      childData.parentVm = result;
    }

    return result;
  }
}

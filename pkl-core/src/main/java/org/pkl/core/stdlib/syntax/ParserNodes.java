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
import com.oracle.truffle.api.dsl.Specialization;
import java.util.ArrayList;
import java.util.Locale;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.SyntaxModule;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.stdlib.syntax.SyntaxNodes.NodeData;
import org.pkl.parser.GenericParser;
import org.pkl.parser.GenericParserError;
import org.pkl.parser.syntax.generic.FullSpan;
import org.pkl.parser.syntax.generic.Node;
import org.pkl.parser.syntax.generic.NodeType;

public class ParserNodes {
  private ParserNodes() {}

  private static final VmObjectFactory<FullSpan> spanFactory =
      new VmObjectFactory<FullSpan>(SyntaxModule::getSpanClass)
          .addIntProperty("lineStart", FullSpan::lineBegin)
          .addIntProperty("colStart", FullSpan::colBegin)
          .addIntProperty("lineEnd", FullSpan::lineEnd)
          .addIntProperty("colEnd", FullSpan::colEnd);

  private static final VmObjectFactory<NodeData> nodeFactory =
      new VmObjectFactory<NodeData>(SyntaxModule::getNodeClass)
          .addStringProperty("type", nd -> nd.node.type.name().toLowerCase(Locale.ROOT))
          .addListProperty("children", nd -> nd.childrenVm)
          .addProperty("parent", nd -> VmNull.lift(nd.parentVm))
          .addProperty(
              "text",
              nd ->
                  nd.node.children.isEmpty() || nd.node.type == NodeType.STRING_CHARS
                      ? nd.node.text(nd.source)
                      : VmNull.withoutDefault())
          .addTypedProperty("span", nd -> nd.spanVm);

  private static final VmObjectFactory<VmTyped> moduleNodeFactory =
      new VmObjectFactory<VmTyped>(SyntaxModule::getModuleNodeClass).addProperty("node", vm -> vm);

  public abstract static class parseModule extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object evalString(@SuppressWarnings("unused") VmTyped self, String source) {
      return doParse(source);
    }

    @Specialization
    @TruffleBoundary
    protected Object evalResource(@SuppressWarnings("unused") VmTyped self, VmTyped source) {
      // `source` is a `pkl.base#Resource`
      var text = (String) VmUtils.readMember(source, Identifier.TEXT);
      return doParse(text);
    }
  }

  public abstract static class parseModuleOrNull extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object evalString(@SuppressWarnings("unused") VmTyped self, String source) {
      return doParseOrNull(source);
    }

    @Specialization
    @TruffleBoundary
    protected Object evalResource(
        @SuppressWarnings("unused") VmTyped self, VmTyped source) {
      // `source` is a `pkl.base#Resource`
      var text = (String) VmUtils.readMember(source, Identifier.TEXT);
      return doParseOrNull(text);
    }
  }

  private static Object doParse(String src) {
    var sourceChars = src.toCharArray();
    try {
      var parser = new GenericParser();
      var root = parser.parseModule(src);
      var genericNode = convertNode(root, sourceChars);
      return moduleNodeFactory.create(genericNode);
    } catch (GenericParserError e) {
      throw new VmExceptionBuilder().evalError("parserError").withHint(e.toString()).build();
    }
  }

  private static Object doParseOrNull(String src) {
    var sourceChars = src.toCharArray();
    try {
      var parser = new GenericParser();
      var root = parser.parseModule(src);
      var genericNode = convertNode(root, sourceChars);
      return moduleNodeFactory.create(genericNode);
    } catch (GenericParserError e) {
      return VmNull.withoutDefault();
    }
  }

  private static VmTyped convertNode(Node genericNode, char[] sourceChars) {
    // convert children recursively
    var childrenList = new ArrayList<VmTyped>(genericNode.children.size());
    for (var child : genericNode.children) {
      childrenList.add(convertNode(child, sourceChars));
    }

    // materialize text now so that nodes reused verbatim by `walk`/`format` are
    // self-contained
    if (genericNode.children.isEmpty() || genericNode.type == NodeType.STRING_CHARS) {
      genericNode.text(sourceChars);
    }

    var childrenVm = VmList.create(childrenList.toArray());
    var spanVm = spanFactory.create(genericNode.span);
    var data = new NodeData(genericNode, sourceChars, childrenVm, spanVm);

    var result = nodeFactory.create(data);

    // set parent back-reference on each child
    for (var childVm : childrenList) {
      var childData = (NodeData) childVm.getExtraStorage();
      childData.parentVm = result;
    }

    return result;
  }
}

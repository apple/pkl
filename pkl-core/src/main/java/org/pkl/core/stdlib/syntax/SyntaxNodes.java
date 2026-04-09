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
package org.pkl.core.stdlib.syntax;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import java.util.ArrayList;
import org.pkl.core.runtime.SyntaxModule;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.parser.GenericParser;
import org.pkl.parser.GenericParserError;
import org.pkl.parser.syntax.generic.FullSpan;
import org.pkl.parser.syntax.generic.Node;

public final class SyntaxNodes {
  private SyntaxNodes() {}

  /** Extra storage backing a Pkl {@code Node} instance. */
  static final class NodeData {
    final Node node;
    final char[] source;
    VmTyped parentVm;
    VmList childrenVm;
    VmTyped spanVm;

    NodeData(Node node, char[] source) {
      this.node = node;
      this.source = source;
    }
  }

  /** Extra storage backing a Pkl {@code ParserError} instance. */
  static final class ErrorData {
    final String text;
    final VmTyped spanVm;

    ErrorData(String text, VmTyped spanVm) {
      this.text = text;
      this.spanVm = spanVm;
    }
  }

  private static final VmObjectFactory<FullSpan> spanFactory =
      new VmObjectFactory<FullSpan>(SyntaxModule::getSpanClass)
          .addIntProperty("lineStart", FullSpan::lineBegin)
          .addIntProperty("colStart", FullSpan::colBegin)
          .addIntProperty("lineEnd", FullSpan::lineEnd)
          .addIntProperty("colEnd", FullSpan::colEnd);

  private static final VmObjectFactory<NodeData> nodeFactory =
      new VmObjectFactory<NodeData>(SyntaxModule::getNodeClass)
          .addStringProperty("type", nd -> nd.node.type.name().toLowerCase())
          .addListProperty("children", nd -> nd.childrenVm)
          .addProperty("parent", nd -> VmNull.lift(nd.parentVm))
          .addProperty(
              "text",
              nd -> nd.node.children.isEmpty() ? nd.node.text(nd.source) : VmNull.withoutDefault())
          .addTypedProperty("span", nd -> nd.spanVm);

  private static final VmObjectFactory<ErrorData> parserErrorFactory =
      new VmObjectFactory<ErrorData>(SyntaxModule::getParserErrorClass)
          .addStringProperty("text", ed -> ed.text)
          .addTypedProperty("span", ed -> ed.spanVm);

  public abstract static class parseNodes extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, String source) {
      var sourceChars = source.toCharArray();

      try {
        var parser = new GenericParser();
        var root = parser.parseModule(source);
        return convertNode(root, sourceChars);
      } catch (GenericParserError e) {
        var errorSpanVm = spanFactory.create(e.getSpan());
        var text = e.getMessage() != null ? e.getMessage() : "Parse error";
        return parserErrorFactory.create(new ErrorData(text, errorSpanVm));
      }
    }

    @TruffleBoundary
    private static VmTyped convertNode(Node genericNode, char[] sourceChars) {
      // Convert children recursively
      var childrenList = new ArrayList<VmTyped>(genericNode.children.size());
      for (var child : genericNode.children) {
        childrenList.add(convertNode(child, sourceChars));
      }

      // Build NodeData
      var data = new NodeData(genericNode, sourceChars);
      data.childrenVm = VmList.create(childrenList.toArray());
      data.spanVm = spanFactory.create(genericNode.span);

      // Create VmTyped node
      var result = nodeFactory.create(data);

      // Set parent back-reference on each child
      for (var childVm : childrenList) {
        var childData = (NodeData) childVm.getExtraStorage();
        childData.parentVm = result;
      }

      return result;
    }
  }
}

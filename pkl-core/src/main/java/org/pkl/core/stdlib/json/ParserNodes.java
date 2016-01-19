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
package org.pkl.core.stdlib.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.util.*;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.*;
import org.pkl.core.stdlib.ExternalMethod1Node;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.json.JsonHandler;
import org.pkl.core.util.json.JsonParser;
import org.pkl.core.util.json.ParseException;

public class ParserNodes {
  private ParserNodes() {}

  public abstract static class parse extends ExternalMethod1Node {
    @Specialization
    @TruffleBoundary
    protected Object eval(VmTyped self, String text) {
      return doParse(self, text);
    }

    @Specialization
    @TruffleBoundary
    protected Object eval(
        VmTyped self, VmTyped resource, @Cached("create()") IndirectCallNode callNode) {
      var text = (String) VmUtils.readMember(resource, Identifier.TEXT, callNode);
      return doParse(self, text);
    }

    private Object doParse(VmTyped self, String text) {
      var converter = createConverter(self);
      var useMapping = (boolean) VmUtils.readMember(self, Identifier.USE_MAPPING);
      var handler = new Handler(converter, useMapping);
      var parser = new JsonParser(handler);
      try {
        parser.parse(text);
      } catch (ParseException e) {
        throw exceptionBuilder().evalError("jsonParseError").withHint(e.getMessage()).build();
      }
      return converter.convert(handler.value, List.of(VmValueConverter.TOP_LEVEL_VALUE));
    }
  }

  private static PklConverter createConverter(VmTyped self) {
    var converters = (VmMapping) VmUtils.readMember(self, Identifier.CONVERTERS);
    return new PklConverter(converters);
  }

  private static class Handler
      extends JsonHandler<EconomicMap<Object, ObjectMember>, EconomicMap<Object, ObjectMember>> {
    private final PklConverter converter;
    private final boolean useMapping;

    private final Deque<Object> currPath = new ArrayDeque<>();

    public Handler(PklConverter converter, boolean useMapping) {
      this.converter = converter;
      this.useMapping = useMapping;

      currPath.push(VmValueConverter.TOP_LEVEL_VALUE);
    }

    private Object value;

    @Override
    public void endNull() {
      value = VmNull.withoutDefault();
    }

    @Override
    public void endBoolean(boolean value) {
      this.value = value;
    }

    @Override
    public void endString(String string) {
      value = string;
    }

    @Override
    public void endNumber(String string) {
      try {
        value = Long.valueOf(string);
      } catch (NumberFormatException e) {
        try {
          value = Double.valueOf(string);
        } catch (NumberFormatException e2) {
          throw new ParseException("Cannot parse `" + string + "` as number.", getLocation());
        }
      }
    }

    @Override
    public EconomicMap<Object, ObjectMember> startArray() {
      currPath.push(VmValueConverter.WILDCARD_ELEMENT);
      return EconomicMaps.create();
    }

    @Override
    public void endArray(@Nullable EconomicMap<Object, ObjectMember> members) {
      assert members != null;
      value =
          new VmListing(
              VmUtils.createEmptyMaterializedFrame(),
              BaseModule.getListingClass().getPrototype(),
              members,
              EconomicMaps.size(members));
      currPath.pop();
    }

    @Override
    public void endArrayValue(@Nullable EconomicMap<Object, ObjectMember> members) {
      assert members != null;
      var size = EconomicMaps.size(members);
      var member =
          new ObjectMember(
              VmUtils.unavailableSourceSection(),
              VmUtils.unavailableSourceSection(),
              VmModifier.ELEMENT,
              null,
              String.valueOf(size));
      member.initConstantValue(converter.convert(value, currPath));
      EconomicMaps.put(members, (long) size, member);
    }

    @Override
    public EconomicMap<Object, ObjectMember> startObject() {
      return EconomicMaps.create();
    }

    @Override
    public void endObject(@Nullable EconomicMap<Object, ObjectMember> members) {
      assert members != null;
      if (useMapping) {
        value =
            new VmMapping(
                VmUtils.createEmptyMaterializedFrame(),
                BaseModule.getMappingClass().getPrototype(),
                members);
      } else {
        value =
            new VmDynamic(
                VmUtils.createEmptyMaterializedFrame(),
                BaseModule.getDynamicClass().getPrototype(),
                members,
                0);
      }
    }

    @Override
    public void startObjectValue(@Nullable EconomicMap<Object, ObjectMember> members, String name) {
      currPath.push(Identifier.get(name));
    }

    @Override
    public void endObjectValue(@Nullable EconomicMap<Object, ObjectMember> members, String name) {
      assert members != null;
      var memberName = useMapping ? name : Identifier.get(name);
      var member =
          new ObjectMember(
              VmUtils.unavailableSourceSection(),
              VmUtils.unavailableSourceSection(),
              useMapping ? VmModifier.ENTRY : VmModifier.NONE,
              useMapping ? null : (Identifier) memberName,
              "generated");
      member.initConstantValue(converter.convert(value, currPath));
      EconomicMaps.put(members, memberName, member);
      currPath.pop();
    }
  }
}

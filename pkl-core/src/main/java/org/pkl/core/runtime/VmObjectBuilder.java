/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import org.graalvm.collections.EconomicMap;
import org.pkl.core.ast.VmModifier;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.ast.member.SharedMemberNode;
import org.pkl.core.util.EconomicMaps;

/** A builder for {@link VmObject}s whose {@link ObjectMember}s are determined at run time. */
@SuppressWarnings("UnusedReturnValue")
public final class VmObjectBuilder {
  private final EconomicMap<Object, ObjectMember> members;
  private int elementCount = 0;

  public VmObjectBuilder() {
    members = EconomicMaps.create();
  }

  public VmObjectBuilder(int initialSize) {
    members = EconomicMaps.create(initialSize);
  }

  public VmObjectBuilder addProperty(Identifier name, Object value) {
    EconomicMaps.put(members, name, VmUtils.createSyntheticObjectProperty(name, "", value));
    return this;
  }

  public VmObjectBuilder addElement(Object value) {
    EconomicMaps.put(
        members, (long) elementCount++, VmUtils.createSyntheticObjectElement("", value));
    return this;
  }

  public VmObjectBuilder addEntry(Object key, Object value) {
    EconomicMaps.put(members, key, VmUtils.createSyntheticObjectEntry("", value));
    return this;
  }

  public VmObjectBuilder addEntry(Object key, SharedMemberNode valueNode) {
    var entry =
        new ObjectMember(
            valueNode.getSourceSection(), valueNode.getHeaderSection(), VmModifier.ENTRY, null, "");
    entry.initMemberNode(valueNode);
    EconomicMaps.put(members, key, entry);
    return this;
  }

  public VmListing toListing() {
    var ret =
        new VmListing(
            VmUtils.createEmptyMaterializedFrame(),
            BaseModule.getListingClass().getPrototype(),
            members,
            elementCount);
    setCachedValues(ret);
    return ret;
  }

  public VmMapping toMapping() {
    var ret =
        new VmMapping(
            VmUtils.createEmptyMaterializedFrame(),
            BaseModule.getMappingClass().getPrototype(),
            members);
    setCachedValues(ret);
    return ret;
  }

  public VmMapping toMapping(Object extraStorage) {
    var result = toMapping();
    result.setExtraStorage(extraStorage);
    return result;
  }

  public VmDynamic toDynamic() {
    var ret =
        new VmDynamic(
            VmUtils.createEmptyMaterializedFrame(),
            BaseModule.getDynamicClass().getPrototype(),
            members,
            elementCount);
    setCachedValues(ret);
    return ret;
  }

  public VmTyped toTyped(VmClass clazz) {
    var ret =
        new VmTyped(VmUtils.createEmptyMaterializedFrame(), clazz.getPrototype(), clazz, members);
    setCachedValues(ret);
    return ret;
  }

  private void setCachedValues(VmObject object) {
    var cursor = EconomicMaps.getEntries(members);
    while (cursor.advance()) {
      var member = cursor.getValue();
      var constantValue = member.getConstantValue();
      if (constantValue != null) {
        object.setCachedValue(cursor.getKey(), constantValue);
      }
    }
  }
}

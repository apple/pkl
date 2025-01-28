/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.expression.generator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.VmObject;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.EconomicMaps;

/** Data collected by {@link GeneratorObjectLiteralNode} to generate a `VmObject`. */
public final class ObjectData {
  /** The object's members. */
  private final EconomicMap<Object, ObjectMember> members;

  /**
   * The frames that were active when `members` were generated. Only a subset of members have their
   * frames stored ({@link GeneratorMemberNode#isFrameStored}). Frames are stored in
   * `owner.extraStorage` and retrieved by `RestoreForBindingsNode` when members are executed
   */
  private final EconomicMap<Object, MaterializedFrame> generatorFrames;

  /** The object's number of elements. */
  private int length;

  ObjectData(int parentLength) {
    // optimize for memory usage by not estimating minimum size
    members = EconomicMaps.create();
    generatorFrames = EconomicMaps.create();
    length = parentLength;
  }

  UnmodifiableEconomicMap<Object, ObjectMember> members() {
    return members;
  }

  int length() {
    return length;
  }

  boolean hasNoGeneratorFrames() {
    return generatorFrames.isEmpty();
  }

  void addElement(VirtualFrame frame, ObjectMember member, GeneratorMemberNode node) {
    addMember(frame, (long) length, member, node);
    length += 1;
  }

  void addProperty(VirtualFrame frame, ObjectMember member, GeneratorMemberNode node) {
    addMember(frame, member.getName(), member, node);
  }

  void addMember(VirtualFrame frame, Object key, ObjectMember member, GeneratorMemberNode node) {
    if (EconomicMaps.put(members, key, member) != null) {
      CompilerDirectives.transferToInterpreter();
      throw node.duplicateDefinition(key, member);
    }
    if (node.isFrameStored) {
      EconomicMaps.put(generatorFrames, key, frame.materialize());
    }
  }

  <T extends VmObject> T storeGeneratorFrames(T object) {
    object.setExtraStorage(generatorFrames);
    return object;
  }

  static MaterializedFrame getGeneratorFrame(VirtualFrame frame) {
    @SuppressWarnings("unchecked")
    var map = (EconomicMap<Object, MaterializedFrame>) VmUtils.getOwner(frame).getExtraStorage();
    var result = EconomicMaps.get(map, VmUtils.getMemberKey(frame));
    assert result != null;
    return result;
  }
}

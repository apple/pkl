/**
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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.ast.PklRootNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.Nullable;

public final class VmFunction extends VmObjectLike {
  private final Object thisValue;
  private final int paramCount;
  private final PklRootNode rootNode;

  public VmFunction(
      MaterializedFrame enclosingFrame,
      Object thisValue,
      int paramCount,
      PklRootNode rootNode,
      @Nullable Object extraStorage) {
    super(enclosingFrame);
    this.thisValue = thisValue;
    this.paramCount = paramCount;
    this.rootNode = rootNode;
    this.extraStorage = extraStorage;
  }

  public RootCallTarget getCallTarget() {
    return rootNode.getCallTarget();
  }

  public int getParameterCount() {
    return paramCount;
  }

  // if call site is a node, use ApplyVmFunction1Node.execute() or DirectCallNode.call() instead of
  // this method
  public Object apply(Object arg1) {
    return getCallTarget().call(thisValue, this, arg1);
  }

  public String applyString(Object arg1) {
    var result = apply(arg1);
    if (result instanceof String string) return string;

    CompilerDirectives.transferToInterpreter();
    throw new VmExceptionBuilder().typeMismatch(result, BaseModule.getStringClass()).build();
  }

  // if call site is a node, use ApplyVmFunction2Node.execute() or DirectCallNode.call() instead of
  // this method
  public Object apply(Object arg1, Object arg2) {
    return getCallTarget().call(thisValue, this, arg1, arg2);
  }

  public VmFunction copy(
      int newParamCount, @Nullable PklRootNode newRootNode, @Nullable Object newExtraStorage) {
    return new VmFunction(
        enclosingFrame,
        thisValue,
        newParamCount,
        newRootNode == null ? rootNode : newRootNode,
        newExtraStorage);
  }

  public Object getThisValue() {
    return thisValue;
  }

  @Override
  public VmObjectLike getParent() {
    return getPrototype();
  }

  @Override
  public boolean hasMember(Object key) {
    return false;
  }

  @Override
  public @Nullable ObjectMember getMember(Object key) {
    return null;
  }

  @Override
  public UnmodifiableEconomicMap<Object, ObjectMember> getMembers() {
    return EconomicMaps.create();
  }

  @Override
  public @Nullable Object getCachedValue(Object key) {
    return null;
  }

  @Override
  @TruffleBoundary
  public void setCachedValue(Object key, Object value, ObjectMember objectMember) {
    throw new VmExceptionBuilder()
        .bug("Class `VmFunction` does not support method `setCachedValue()`.")
        .build();
  }

  @Override
  public boolean hasCachedValue(Object key) {
    throw new VmExceptionBuilder()
        .bug("Class `VmFunction` does not support method `hasCachedValue()`.")
        .build();
  }

  @Override
  public boolean iterateMemberValues(MemberValueConsumer consumer) {
    return true;
  }

  @Override
  public boolean forceAndIterateMemberValues(ForcedMemberValueConsumer consumer) {
    return true;
  }

  @Override
  public boolean iterateAlreadyForcedMemberValues(ForcedMemberValueConsumer consumer) {
    return true;
  }

  @Override
  public boolean iterateMembers(MemberConsumer consumer) {
    return true;
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getFunctionNClass(paramCount);
  }

  @Override
  public void force(boolean allowUndefinedValues, boolean recurse) {
    // do nothing
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    // do nothing
  }

  @Override
  public Object export() {
    throw new VmExceptionBuilder().evalError("cannotExportValue", getVmClass()).build();
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitFunction(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertFunction(this, path);
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}

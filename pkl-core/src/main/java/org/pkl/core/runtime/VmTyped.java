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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.pkl.core.Composite;
import org.pkl.core.PModule;
import org.pkl.core.PObject;
import org.pkl.core.ast.expression.unary.ImportNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.util.EconomicMaps;
import org.pkl.core.util.LateInit;
import org.pkl.core.util.Nullable;

public final class VmTyped extends VmObject {
  @CompilationFinal @LateInit private VmClass clazz;

  public VmTyped(
      MaterializedFrame enclosingFrame,
      @Nullable VmTyped parent,
      // null -> will be initialized using lateInitVmClass() later
      @Nullable VmClass clazz,
      UnmodifiableEconomicMap<Object, ObjectMember> members) {
    super(enclosingFrame, parent, members);
    this.clazz = clazz;
  }

  public void lateInitVmClass(VmClass clazz) {
    assert this.clazz == null : "VmTyped.clazz has already been initialized.";
    this.clazz = clazz;
  }

  public void addProperty(ObjectMember property) {
    EconomicMaps.put((EconomicMap<Object, ObjectMember>) members, property.getName(), property);
  }

  public void addProperties(UnmodifiableEconomicMap<Object, ObjectMember> properties) {
    EconomicMaps.putAll((EconomicMap<Object, ObjectMember>) members, properties);
  }

  public VmClass getVmClass() {
    assert clazz != null : "VmTyped.clazz was not initialized.";
    return clazz;
  }

  public @Nullable VmTyped getParent() {
    return (VmTyped) parent;
  }

  public boolean isAmending(VmTyped other) {
    if (this == other) {
      return true;
    }
    if (parent == null) {
      return false;
    }
    return ((VmTyped) parent).isAmending(other);
  }

  @Override
  public boolean isPrototype() {
    return this == getPrototype();
  }

  @Override
  public boolean isModuleObject() {
    return extraStorage instanceof ModuleInfo;
  }

  public ModuleInfo getModuleInfo() {
    assert isModuleObject();
    return (ModuleInfo) getExtraStorage();
  }

  public VmTyped getModuleMirror() {
    assert isModuleObject();
    return getModuleInfo().getMirror(this);
  }

  public VmValue getSupermoduleMirror() {
    assert isModuleObject();

    var parent = getParent();
    assert parent != null;

    return parent == BaseModule.getModuleClass().getPrototype()
        ? VmNull.withoutDefault()
        : parent.getModuleMirror();
  }

  public VmMap getImports() {
    assert isModuleObject();

    var builder = VmMap.builder();
    for (var member : members.getValues()) {
      if (member.isImport()) {
        var memberNode = member.getMemberNode();
        assert memberNode != null; // import is never a constant
        builder.add(
            member.getName().toString(),
            ((ImportNode) memberNode.getBodyNode()).getImportUri().toString());
      }
    }
    return builder.build();
  }

  public VmMap getClassMirrors() {
    assert isModuleObject();

    if (getModuleInfo().isAmend()) return VmMap.EMPTY;

    var builder = VmMap.builder();
    for (var member : members.getValues()) {
      if (member.isClass() && !member.isLocal()) {
        var className = member.getName();
        var clazz = (VmClass) getCachedValue(className);
        if (clazz == null) {
          clazz = (VmClass) VmUtils.doReadMember(this, this, className, member);
        }
        builder.add(className.toString(), clazz.getMirror());
      }
    }
    return builder.build();
  }

  public VmMap getTypeAliasMirrors() {
    assert isModuleObject();

    if (getModuleInfo().isAmend()) return VmMap.EMPTY;
    var builder = VmMap.builder();
    for (var member : members.getValues()) {
      if (member.isTypeAlias() && !member.isLocal()) {
        var typeAliasName = member.getName();
        var typeAlias = (VmTypeAlias) getCachedValue(typeAliasName);
        if (typeAlias == null) {
          typeAlias = (VmTypeAlias) VmUtils.doReadMember(this, this, typeAliasName, member);
        }
        builder.add(typeAliasName.toString(), typeAlias.getMirror());
      }
    }
    return builder.build();
  }

  @Override
  @TruffleBoundary
  public Composite export() {
    if (!isModuleObject()) {
      return new PObject(clazz.getPClassInfo(), exportMembers());
    }

    var moduleInfo = getModuleInfo();
    return new PModule(
        moduleInfo.getModuleKey().getUri(),
        moduleInfo.getModuleName(),
        clazz.getPClassInfo(),
        exportMembers());
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitTyped(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertTyped(this, path);
  }

  @Override
  @TruffleBoundary
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmTyped other)) return false;

    if (clazz != other.clazz) return false;
    // could use shallow force, but deep force is cached
    force(false);
    other.force(false);

    for (var key : clazz.getAllRegularPropertyNames()) {
      var value = getCachedValue(key);
      assert value != null;
      var otherValue = other.getCachedValue(key);
      if (!value.equals(otherValue)) return false;
    }

    return true;
  }

  @Override
  @TruffleBoundary
  public int hashCode() {
    if (cachedHash != 0) return cachedHash;

    force(false);
    var result = 0;

    for (var key : clazz.getAllRegularPropertyNames()) {
      var value = getCachedValue(key);
      assert value != null;
      result = 31 * result + value.hashCode();
    }

    cachedHash = result;
    return result;
  }
}

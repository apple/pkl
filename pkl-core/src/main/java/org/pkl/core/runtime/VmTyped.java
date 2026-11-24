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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Objects;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.jspecify.annotations.Nullable;
import org.pkl.core.Composite;
import org.pkl.core.PModule;
import org.pkl.core.PObject;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.expression.unary.ImportNode;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.VmObjectCursor.CursorOption;
import org.pkl.core.runtime.VmObjectCursor.EmptyCursor;
import org.pkl.core.runtime.VmTypedCursors.PropertyCursor;
import org.pkl.core.util.EconomicMaps;

public final class VmTyped extends VmObject {
  @CompilationFinal private @Nullable VmClass clazz;

  private static final ThreadLocal<IdentityHashMap<VmTyped, Object>> evaluatingKeys =
      ThreadLocal.withInitial(IdentityHashMap::new);

  public VmTyped(
      MaterializedFrame enclosingFrame,
      @Nullable VmTyped parent,
      VmClass clazz,
      UnmodifiableEconomicMap<Object, ObjectMember> members) {
    super(enclosingFrame, parent, members);
    this.clazz = clazz;
  }

  /** Constructs a VmTyped whose clazz will be initialized using lateInitVmClass() later. */
  public VmTyped(
      MaterializedFrame enclosingFrame,
      @Nullable VmTyped parent,
      UnmodifiableEconomicMap<Object, ObjectMember> members) {
    super(enclosingFrame, parent, members);
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
        var bodyNode = memberNode.getBodyNode();
        if (bodyNode instanceof WrapperNode wrapper) {
          bodyNode = (ExpressionNode) wrapper.getDelegateNode();
        }
        // Glob imports (`import*`) don't import a type; they resolve to a mapping.
        // Exclude them here, consistent with `imports` also not covering import expressions.
        if (bodyNode instanceof ImportNode importNode) {
          builder.add(member.getName().toString(), importNode.getImportUri().toString());
        }
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
    assert isDeepForced() : "Value was not forced prior to export";
    assert clazz != null;
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
  public VmObjectCursor properties() {
    return new PropertyCursor(this, true);
  }

  @Override
  public VmObjectCursor properties(CursorOption option) {
    // don't force module objects to avoid forcing types (too conservative?)
    if (option == CursorOption.ALL_VALUES && !isModuleObject()) {
      force(false, false);
    }
    return new PropertyCursor(this, true);
  }

  @Override
  public VmObjectCursor elements() {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor elements(CursorOption option) {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor elements(EnumSet<CursorOption> options) {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor entries() {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor entries(CursorOption option) {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor entries(EnumSet<CursorOption> options) {
    return EmptyCursor.INSTANCE;
  }

  @Override
  public VmObjectCursor members() {
    return new PropertyCursor(this, false);
  }

  @Override
  public VmObjectCursor members(CursorOption option) {
    if (option == CursorOption.ANY_ORDER) {
      force(false, false);
    }
    return new PropertyCursor(this, false);
  }

  @Override
  @TruffleBoundary
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmTyped other)) return false;

    assert clazz != null;
    if (clazz != other.clazz) return false;
    // could use shallow force, but deep force is cached
    force(false);
    other.force(false);

    for (var key : clazz.getAllRegularPropertyNames()) {
      var value = forcedPropertyValue(key);
      var otherValue = other.forcedPropertyValue(key);
      if (!value.equals(otherValue)) return false;
    }

    return true;
  }

  @Override
  @TruffleBoundary
  public int hashCode() {
    if (cachedHash != 0) return cachedHash;

    assert clazz != null;
    force(false);
    var result = 0;

    for (var key : clazz.getAllRegularPropertyNames()) {
      result = 31 * result + forcedPropertyValue(key).hashCode();
    }

    cachedHash = result;
    return result;
  }

  /**
   * Returns a regular property value for use in {@link #equals} and {@link #hashCode}.
   *
   * <p>{@link #force(boolean)} can return early if it is already running on this object (for
   * example when {@code hashCode} is called mid-force). A missing cached value is then evaluated
   * here. If that same property is already being evaluated on this object, the reference is
   * circular and is reported as a stack overflow.
   */
  private Object forcedPropertyValue(Object key) {
    var value = getCachedValue(key);
    if (value != null) return value;
    var inFlight = evaluatingKeys.get();
    if (inFlight.containsKey(this) && Objects.equals(inFlight.get(this), key)) {
      throw new VmExceptionBuilder().stackOverflowError().build();
    }
    var previous = inFlight.put(this, key);
    try {
      value = VmUtils.readMemberOrNull(this, key);
      if (value != null) return value;
      throw new VmExceptionBuilder().stackOverflowError().build();
    } finally {
      if (previous == null) {
        inFlight.remove(this);
      } else {
        inFlight.put(this, previous);
      }
    }
  }
}

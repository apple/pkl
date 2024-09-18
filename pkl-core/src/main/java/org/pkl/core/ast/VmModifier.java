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
package org.pkl.core.ast;

import java.util.EnumSet;
import java.util.Set;
import org.pkl.core.Modifier;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmSet;

public final class VmModifier {
  private VmModifier() {}

  // user-facing modifiers

  public static final int ABSTRACT = 0x1;

  public static final int OPEN = 0x2;

  public static final int LOCAL = 0x4;

  // absent from rendered output but present in module schema (e.g. for pkldoc purposes)
  public static final int HIDDEN = 0x8;

  public static final int EXTERNAL = 0x10;

  public static final int FIXED = 0x20;

  public static final int CONST = 0x40;

  // internal modifiers

  public static final int IMPORT = 0x80;

  public static final int CLASS = 0x100;

  public static final int TYPE_ALIAS = 0x200;

  public static final int ENTRY = 0x400;

  public static final int ELEMENT = 0x800;

  public static final int GLOB = 0x1000;

  public static final int DELETE = 0x2000;

  // modifier sets

  public static final int NONE = 0;

  public static final int VALID_MODULE_MODIFIERS = ABSTRACT | OPEN;

  public static final int VALID_AMENDING_MODULE_MODIFIERS = 0;

  public static final int VALID_CLASS_MODIFIERS = ABSTRACT | OPEN | LOCAL | EXTERNAL;

  public static final int VALID_TYPE_ALIAS_MODIFIERS = LOCAL | EXTERNAL;

  public static final int VALID_METHOD_MODIFIERS = ABSTRACT | LOCAL | EXTERNAL | CONST;

  public static final int VALID_PROPERTY_MODIFIERS =
      ABSTRACT | LOCAL | HIDDEN | EXTERNAL | FIXED | CONST;

  public static final int VALID_OBJECT_MEMBER_MODIFIERS = LOCAL;

  public static final int TYPEALIAS_OBJECT_MEMBER = TYPE_ALIAS | CONST;

  public static final int LOCAL_TYPEALIAS_OBJECT_MEMBER = LOCAL | TYPEALIAS_OBJECT_MEMBER;

  public static final int CLASS_OBJECT_MEMBER = CLASS | CONST;

  public static final int LOCAL_CLASS_OBJECT_MEMBER = LOCAL | CLASS_OBJECT_MEMBER;

  public static boolean isLocal(int modifiers) {
    return (modifiers & LOCAL) != 0;
  }

  public static boolean isAbstract(int modifiers) {
    return (modifiers & ABSTRACT) != 0;
  }

  public static boolean isFixed(int modifiers) {
    return (modifiers & FIXED) != 0;
  }

  public static boolean isOpen(int modifiers) {
    return (modifiers & OPEN) != 0;
  }

  public static boolean isHidden(int modifiers) {
    return (modifiers & HIDDEN) != 0;
  }

  public static boolean isExternal(int modifiers) {
    return (modifiers & EXTERNAL) != 0;
  }

  public static boolean isClass(int modifiers) {
    return (modifiers & CLASS) != 0;
  }

  public static boolean isTypeAlias(int modifiers) {
    return (modifiers & TYPE_ALIAS) != 0;
  }

  public static boolean isImport(int modifiers) {
    return (modifiers & IMPORT) != 0;
  }

  public static boolean isGlob(int modifiers) {
    return (modifiers & GLOB) != 0;
  }

  public static boolean isConst(int modifiers) {
    return (modifiers & CONST) != 0;
  }

  public static boolean isElement(int modifiers) {
    return (modifiers & ELEMENT) != 0;
  }

  public static boolean isEntry(int modifiers) {
    return (modifiers & ENTRY) != 0;
  }

  public static boolean isDelete(int modifiers) {
    return (modifiers & DELETE) != 0;
  }

  public static boolean isType(int modifiers) {
    return (modifiers & (CLASS | TYPE_ALIAS | IMPORT)) != 0 && (modifiers & (GLOB | DELETE)) == 0;
  }

  public static boolean isLocalOrExternalOrHidden(int modifiers) {
    return (modifiers & (LOCAL | EXTERNAL | HIDDEN)) != 0;
  }

  public static boolean isLocalOrExternalOrAbstractOrDelete(int modifiers) {
    return (modifiers & (LOCAL | EXTERNAL | ABSTRACT | DELETE)) != 0;
  }

  public static boolean isConstOrFixed(int modifiers) {
    return (modifiers & (CONST | FIXED)) != 0;
  }

  public static Set<Modifier> export(int modifiers, boolean isClass) {
    var result = EnumSet.noneOf(Modifier.class);

    if (isAbstract(modifiers)) result.add(Modifier.ABSTRACT);
    if (isOpen(modifiers)) result.add(Modifier.OPEN);
    if (isHidden(modifiers)) result.add(Modifier.HIDDEN);
    // `external` modifier is part of class contract but not part of property/method contract
    if (isExternal(modifiers) && isClass) result.add(Modifier.EXTERNAL);

    return result;
  }

  public static String toString(int modifier) {
    return switch (modifier) {
      case ABSTRACT -> "abstract";
      case OPEN -> "open";
      case LOCAL -> "local";
      case HIDDEN -> "hidden";
      case EXTERNAL -> "external";
      case FIXED -> "fixed";
      case CONST -> "const";
      default ->
          throw new VmExceptionBuilder()
              .bug("Cannot convert internal modifier `%s` to a string.", toString(modifier))
              .build();
    };
  }

  public static VmSet getMirrors(int modifiers, boolean isClass) {
    var builder = VmSet.EMPTY.builder();

    if (isAbstract(modifiers)) builder.add(toString(ABSTRACT));
    if (isOpen(modifiers)) builder.add(toString(OPEN));
    if (isHidden(modifiers)) builder.add(toString(HIDDEN));
    // `external` modifier is part of class contract but not part of property/method contract
    if (isExternal(modifiers) && isClass) builder.add(toString(EXTERNAL));
    if (isFixed(modifiers)) builder.add(toString(FIXED));
    if (isConst(modifiers)) builder.add(toString(CONST));

    return builder.build();
  }

  public static boolean isClosed(int modifiers) {
    return (modifiers & (ABSTRACT | OPEN)) == 0;
  }

  public static boolean isInstantiable(int modifiers) {
    return (modifiers & (ABSTRACT | EXTERNAL)) == 0;
  }
}

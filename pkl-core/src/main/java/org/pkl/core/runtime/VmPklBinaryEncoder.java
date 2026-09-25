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
package org.pkl.core.runtime;

import java.io.IOException;
import java.util.Deque;
import org.msgpack.core.MessageBufferPacker;
import org.pkl.core.PklBugException;
import org.pkl.core.stdlib.AbstractRenderer;
import org.pkl.core.stdlib.PklConverter;
import org.pkl.core.util.pklbinary.PklBinaryCode;

/** An encoder/renderer for <code>pkl-binary</code></a> encoding. */
public class VmPklBinaryEncoder extends AbstractRenderer {

  // this type explicitly works with MessageBufferPacker:
  // * assumes no I/O during packing (in-memory writes only)
  // * IOExceptions are caught and assumed unreachable

  private final MessageBufferPacker packer;

  public VmPklBinaryEncoder(MessageBufferPacker packer, PklConverter converter) {
    super("pkl-binary", converter, false, false);
    this.packer = packer;
  }

  public VmPklBinaryEncoder(MessageBufferPacker packer) {
    this(packer, PklConverter.NOOP);
  }

  private void packCode(PklBinaryCode code) throws IOException {
    packer.packByte(code.getCode());
  }

  @Override
  public void visitString(String value) {
    try {
      packer.packString(value);
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitBoolean(Boolean value) {
    try {
      packer.packBoolean(value);
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitInt(Long value) {
    try {
      packer.packLong(value);
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitFloat(Double value) {
    try {
      packer.packDouble(value);
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitDuration(VmDuration value) {
    try {
      packer.packArrayHeader(3);
      packCode(PklBinaryCode.DURATION);
      packer.packDouble(value.getValue());
      packer.packString(value.getUnit().toString());
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitDataSize(VmDataSize value) {
    try {
      packer.packArrayHeader(3);
      packCode(PklBinaryCode.DATASIZE);
      packer.packDouble(value.getValue());
      packer.packString(value.getUnit().toString());
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitBytes(VmBytes value) {
    try {
      packer.packArrayHeader(2);
      packCode(PklBinaryCode.BYTES);
      packer.packBinaryHeader(value.getBytes().length);
      packer.addPayload(value.getBytes());
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitIntSeq(VmIntSeq value) {
    try {
      packer.packArrayHeader(4);
      packCode(PklBinaryCode.INTSEQ);
      packer.packLong(value.start);
      packer.packLong(value.end);
      packer.packLong(value.step);
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  protected void visitDocument(Object value) {
    visit(value);
  }

  @Override
  protected void visitTopLevelValue(Object value) {
    visit(value);
  }

  @Override
  protected void visitRenderDirective(VmTyped value) {
    try {
      packer.writePayload(VmUtils.readBytesProperty(value).getBytes());
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  protected void startDynamic(VmDynamic value) {
    startObject(value, value.getRegularMemberCount());
  }

  @Override
  protected void startTyped(VmTyped value) {
    startObject(value, value.getVmClass().getAllRegularPropertyNames().size());
  }

  private void startObject(VmObjectLike value, int memberCount) {
    try {
      packer.packArrayHeader(4);
      packCode(PklBinaryCode.OBJECT);
      packer.packString(value.getVmClass().getDisplayName());
      packer.packString(
          value.getVmClass().getModule().getModuleInfo().getModuleKey().getUri().toString());
      packer.packArrayHeader(memberCount);
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  private void startList(PklBinaryCode code, int length) {
    try {
      packer.packArrayHeader(2);
      packCode(code);
      packer.packArrayHeader(length);
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  private void startMap(PklBinaryCode code, int length) {
    try {
      packer.packArrayHeader(2);
      packCode(code);
      packer.packMapHeader(length);
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  protected void startListing(VmListing value) {
    startList(PklBinaryCode.LISTING, value.getLength());
  }

  @Override
  protected void startMapping(VmMapping value) {
    startMap(PklBinaryCode.MAPPING, (int) value.getLength());
  }

  @Override
  protected void startList(VmList value) {
    startList(PklBinaryCode.LIST, value.getLength());
  }

  @Override
  protected void startSet(VmSet value) {
    startList(PklBinaryCode.SET, value.getLength());
  }

  @Override
  protected void startMap(VmMap value) {
    startMap(PklBinaryCode.MAP, value.getLength());
  }

  @Override
  protected void visitEntryKeyValue(
      Object key, boolean isFirst, Deque<Object> valuePath, Object value) {
    if (enclosingValue instanceof VmDynamic) {
      try {
        packer.packArrayHeader(3);
        packCode(PklBinaryCode.ENTRY);
      } catch (IOException e) {
        throw PklBugException.unreachableCode();
      }
    }

    super.visitEntryKeyValue(key, isFirst, valuePath, value);
  }

  @Override
  protected void visitElement(long index, Object value, boolean isFirst) {
    if (enclosingValue instanceof VmDynamic) {
      try {
        packer.packArrayHeader(3);
        packCode(PklBinaryCode.ELEMENT);
        packer.packLong(index);
      } catch (IOException e) {
        throw PklBugException.unreachableCode();
      }
    }
    visit(value);
  }

  @Override
  protected void visitProperty(Identifier name, Object value, boolean isFirst) {
    try {
      packer.packArrayHeader(3);
      packCode(PklBinaryCode.PROPERTY);
      packer.packString(name.toString());
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
    visit(value);
  }

  @Override
  public void visitClass(VmClass value) {
    try {
      packer.packArrayHeader(3);
      packCode(PklBinaryCode.CLASS);
      packer.packString(value.getDisplayName());
      packer.packString(value.getModule().getModuleInfo().getModuleKey().getUri().toString());
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitTypeAlias(VmTypeAlias value) {
    try {
      packer.packArrayHeader(3);
      packCode(PklBinaryCode.TYPEALIAS);
      packer.packString(value.getDisplayName());
      packer.packString(value.getModuleUri().toString());
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitPair(VmPair value) {
    try {
      packer.packArrayHeader(3);
      packCode(PklBinaryCode.PAIR);
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
    visit(value.getFirst());
    visit(value.getSecond());
  }

  @Override
  public void visitRegex(VmRegex value) {
    try {
      packer.packArrayHeader(2);
      packCode(PklBinaryCode.REGEX);
      packer.packString(value.getPattern().pattern());
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitNull(VmNull value) {
    try {
      packer.packNil();
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitFunction(VmFunction value) {
    try {
      packer.packArrayHeader(1);
      packCode(PklBinaryCode.FUNCTION);
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  @Override
  public void visitReference(VmReference value) {
    try {
      packer.packArrayHeader(5);
      packCode(PklBinaryCode.REFERENCE);
      visit(value.getDomain());
      visit(value.getData());
      packer.packArrayHeader(value.getPath().size());
      for (var access : value.getPath()) {
        visit(access);
      }
      packType(value.getReferentType());
    } catch (IOException e) {
      throw PklBugException.unreachableCode();
    }
  }

  private void packTypeHeader(PklBinaryCode type, int len) throws IOException {
    packer.packArrayHeader(len + 1);
    packer.packByte(type.getCode());
  }

  private void packType(VmType type) throws IOException {
    if (type == VmType.UnknownType.INSTANCE) {
      packTypeHeader(PklBinaryCode.TYPE_UNKNOWN, 0);
    } else if (type == VmType.NothingType.INSTANCE) {
      packTypeHeader(PklBinaryCode.TYPE_NOTHING, 0);
    } else if (type instanceof VmType.StringLiteralType stringLiteralType) {
      packTypeHeader(PklBinaryCode.TYPE_STRING_LITERAL, 1);
      packer.packString(stringLiteralType.getLiteral());
    } else if (type instanceof VmType.ClassType classType) {
      packTypeHeader(PklBinaryCode.TYPE_CLASS, 2);
      visit(classType.getVmClass());
      packTypes(classType.getTypeArguments());
    } else if (type instanceof VmType.FinalModuleType finalModuleType) {
      packTypeHeader(PklBinaryCode.TYPE_MODULE, 2);
      visit(finalModuleType.getVmClass());
      packer.packBoolean(true);
    } else if (type instanceof VmType.FinalThisType finalThisType) {
      packTypeHeader(PklBinaryCode.TYPE_THIS, 2);
      visit(finalThisType.getVmClass());
      packer.packBoolean(true);
    } else if (type instanceof VmType.NonFinalModuleType nonFinalModuleType) {
      packTypeHeader(PklBinaryCode.TYPE_MODULE, 2);
      visit(nonFinalModuleType.getVmClass());
      packer.packBoolean(false);
    } else if (type instanceof VmType.NonFinalThisType nonFinalThisType) {
      packTypeHeader(PklBinaryCode.TYPE_THIS, 2);
      visit(nonFinalThisType.getVmClass());
      packer.packBoolean(false);
    } else if (type instanceof VmType.NullableType nullableType) {
      packTypeHeader(PklBinaryCode.TYPE_NULLABLE, 1);
      packType(nullableType.getElementType());
    } else if (type instanceof VmType.ConstrainedType constrainedType) {
      packTypeHeader(PklBinaryCode.TYPE_CONSTRAINED, 2);
      packType(constrainedType.getBaseType());
      packer.packArrayHeader(constrainedType.getConstraints().length);
      for (var constraint : constrainedType.getConstraints()) {
        packer.packString(constraint);
      }
    } else if (type instanceof VmType.AliasType aliasType) {
      packTypeHeader(PklBinaryCode.TYPE_TYPEALIAS, 3);
      visit(aliasType.getVmTypeAlias());
      packTypes(aliasType.getTypeArguments());
      packType(aliasType.getAliasedType());
    } else if (type instanceof VmType.UnionType unionType) {
      packTypeHeader(PklBinaryCode.TYPE_UNION, 2);
      packTypes(unionType.getElementTypes());
      packer.packInt(unionType.getDefaultIndex());
    } else if (type instanceof VmType.TypeVariableType typeVariableType) {
      packTypeHeader(PklBinaryCode.TYPE_VARIABLE, 3);
      var typeParam = typeVariableType.getTypeParameter();
      packer.packInt(typeParam.getVariance().getCode());
      packer.packString(typeParam.getName());
      packer.packInt(typeParam.getIndex());
    } else {
      // must add above when adding new VmType subclasses
      throw PklBugException.unreachableCode();
    }
  }

  private void packTypes(VmType[] types) throws IOException {
    packer.packArrayHeader(types.length);
    for (var type : types) {
      packType(type);
    }
  }

  @Override
  protected void visitEntryKey(Object key, boolean isFirst) {
    visit(key);
  }

  @Override
  protected void visitEntryValue(Object value) {
    visit(value);
  }

  @Override
  protected void endDynamic(VmDynamic value, boolean isEmpty) {
    // noop
  }

  @Override
  protected void endTyped(VmTyped value, boolean isEmpty) {
    // noop
  }

  @Override
  protected void endListing(VmListing value, boolean isEmpty) {
    // noop
  }

  @Override
  protected void endMapping(VmMapping value, boolean isEmpty) {
    // noop
  }

  @Override
  protected void endList(VmList value) {
    // noop
  }

  @Override
  protected void endSet(VmSet value) {
    // noop
  }

  @Override
  protected void endMap(VmMap value) {
    // noop
  }

  @Override
  protected boolean canRenderPropertyOrEntryOf(VmDynamic object) {
    return true;
  }
}

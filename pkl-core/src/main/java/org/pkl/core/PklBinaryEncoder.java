/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core;

import java.io.IOException;
import java.util.Deque;
import org.msgpack.core.MessagePacker;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmBytes;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmIntSeq;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmListing;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmObjectLike;
import org.pkl.core.runtime.VmPair;
import org.pkl.core.runtime.VmRegex;
import org.pkl.core.runtime.VmSet;
import org.pkl.core.runtime.VmTypeAlias;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.AbstractRenderer;
import org.pkl.core.stdlib.PklConverter;

/**
 * An encoder/renderer for the <a
 * href="https://pkl-lang.org/main/current/bindings-specification/binary-encoding.html"><code>
 * pkl-binary</code></a> encoding.
 */
public class PklBinaryEncoder extends AbstractRenderer {

  protected static final byte CODE_OBJECT = 0x01;
  protected static final byte CODE_MAP = 0x02;
  protected static final byte CODE_MAPPING = 0x03;
  protected static final byte CODE_LIST = 0x04;
  protected static final byte CODE_LISTING = 0x05;
  protected static final byte CODE_SET = 0x06;
  protected static final byte CODE_DURATION = 0x07;
  protected static final byte CODE_DATASIZE = 0x08;
  protected static final byte CODE_PAIR = 0x09;
  protected static final byte CODE_INTSEQ = 0x0A;
  protected static final byte CODE_REGEX = 0x0B;
  protected static final byte CODE_CLASS = 0x0C;
  protected static final byte CODE_TYPEALIAS = 0x0D;
  protected static final byte CODE_FUNCTION = 0x0E;
  protected static final byte CODE_BYTES = 0x0F;

  protected static final byte CODE_PROPERTY = 0x10;
  protected static final byte CODE_ENTRY = 0x11;
  protected static final byte CODE_ELEMENT = 0x12;

  private final MessagePacker packer;

  public PklBinaryEncoder(MessagePacker packer, PklConverter converter) {
    super("pkl-binary", converter, BaseModule.getBytesRenderDirectiveClass(), false, false);
    this.packer = packer;
  }

  public PklBinaryEncoder(MessagePacker packer) {
    this(packer, new PklConverter(VmMapping.empty()));
  }

  private interface VisitMethod {
    void visit() throws IOException;
  }

  private static void doEncode(VisitMethod visitor) {
    try {
      visitor.visit();
    } catch (IOException e) {
      throw new VmExceptionBuilder().evalError("ioErrorEncodingToBinary").withCause(e).build();
    }
  }

  @Override
  public void visitString(String value) {
    doEncode(() -> packer.packString(value));
  }

  @Override
  public void visitBoolean(Boolean value) {
    doEncode(() -> packer.packBoolean(value));
  }

  @Override
  public void visitInt(Long value) {
    doEncode(() -> packer.packLong(value));
  }

  @Override
  public void visitFloat(Double value) {
    doEncode(() -> packer.packDouble(value));
  }

  @Override
  public void visitDuration(VmDuration value) {
    doEncode(
        () -> {
          packer.packArrayHeader(3);
          packer.packInt(CODE_DURATION);
          packer.packDouble(value.getValue());
          packer.packString(value.getUnit().toString());
        });
  }

  @Override
  public void visitDataSize(VmDataSize value) {
    doEncode(
        () -> {
          packer.packArrayHeader(3);
          packer.packInt(CODE_DATASIZE);
          packer.packDouble(value.getValue());
          packer.packString(value.getUnit().toString());
        });
  }

  @Override
  public void visitBytes(VmBytes value) {
    doEncode(
        () -> {
          packer.packArrayHeader(2);
          packer.packInt(CODE_BYTES);
          packer.packBinaryHeader(value.getBytes().length);
          packer.addPayload(value.getBytes());
        });
  }

  @Override
  public void visitIntSeq(VmIntSeq value) {
    doEncode(
        () -> {
          packer.packArrayHeader(4);
          packer.packInt(CODE_INTSEQ);
          packer.packLong(value.start);
          packer.packLong(value.end);
          packer.packLong(value.step);
        });
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
    doEncode(() -> packer.writePayload(VmUtils.readBytesProperty(value).getBytes()));
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
    doEncode(
        () -> {
          packer.packArrayHeader(4);
          packer.packInt(CODE_OBJECT);
          packer.packString(value.getVmClass().getDisplayName());
          packer.packString(
              value.getVmClass().getModule().getModuleInfo().getModuleKey().getUri().toString());
          packer.packArrayHeader(memberCount);
        });
  }

  private void startList(int code, int length) {
    doEncode(
        () -> {
          packer.packArrayHeader(2);
          packer.packInt(code);
          packer.packArrayHeader(length);
        });
  }

  private void startMap(int code, int length) {
    doEncode(
        () -> {
          packer.packArrayHeader(2);
          packer.packInt(code);
          packer.packMapHeader(length);
        });
  }

  @Override
  protected void startListing(VmListing value) {
    startList(CODE_LISTING, value.getLength());
  }

  @Override
  protected void startMapping(VmMapping value) {
    startMap(CODE_MAPPING, (int) value.getLength());
  }

  @Override
  protected void startList(VmList value) {
    startList(CODE_LIST, value.getLength());
  }

  @Override
  protected void startSet(VmSet value) {
    startList(CODE_SET, value.getLength());
  }

  @Override
  protected void startMap(VmMap value) {
    startMap(CODE_MAP, value.getLength());
  }

  @Override
  protected void visitEntryKeyValue(
      Object key, boolean isFirst, Deque<Object> valuePath, Object value) {
    if (enclosingValue instanceof VmDynamic) {
      doEncode(
          () -> {
            packer.packArrayHeader(3);
            packer.packInt(CODE_ENTRY);
          });
    }

    super.visitEntryKeyValue(key, isFirst, valuePath, value);
  }

  @Override
  protected void visitElement(long index, Object value, boolean isFirst) {
    if (enclosingValue instanceof VmDynamic) {
      doEncode(
          () -> {
            packer.packArrayHeader(3);
            packer.packInt(CODE_ELEMENT);
            packer.packLong(index);
          });
    }
    visit(value);
  }

  @Override
  protected void visitProperty(Identifier name, Object value, boolean isFirst) {
    doEncode(
        () -> {
          packer.packArrayHeader(3);
          packer.packInt(CODE_PROPERTY);
          packer.packString(name.toString());
        });
    visit(value);
  }

  @Override
  public void visitClass(VmClass value) {
    doEncode(
        () -> {
          packer.packArrayHeader(3);
          packer.packInt(CODE_CLASS);
          packer.packString(value.getModule().getModuleInfo().getModuleKey().getUri().toString());
          packer.packString(value.getDisplayName());
        });
  }

  @Override
  public void visitTypeAlias(VmTypeAlias value) {
    doEncode(
        () -> {
          packer.packArrayHeader(3);
          packer.packInt(CODE_TYPEALIAS);
          packer.packString(value.getModuleUri().toString());
          packer.packString(value.getQualifiedName());
        });
  }

  @Override
  public void visitPair(VmPair value) {
    doEncode(
        () -> {
          packer.packArrayHeader(3);
          packer.packInt(CODE_PAIR);
        });
    visit(value.getFirst());
    visit(value.getSecond());
  }

  @Override
  public void visitRegex(VmRegex value) {
    doEncode(
        () -> {
          packer.packArrayHeader(2);
          packer.packInt(CODE_REGEX);
          packer.packString(value.getPattern().pattern());
        });
  }

  @Override
  public void visitNull(VmNull value) {
    doEncode(packer::packNil);
  }

  @Override
  public void visitFunction(VmFunction value) {
    doEncode(
        () -> {
          packer.packArrayHeader(1);
          packer.packInt(CODE_FUNCTION);
        });
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

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

public class PklBinaryEncoding {

  public static final byte CODE_OBJECT = 0x01;
  public static final byte CODE_MAP = 0x02;
  public static final byte CODE_MAPPING = 0x03;
  public static final byte CODE_LIST = 0x04;
  public static final byte CODE_LISTING = 0x05;
  public static final byte CODE_SET = 0x06;
  public static final byte CODE_DURATION = 0x07;
  public static final byte CODE_DATASIZE = 0x08;
  public static final byte CODE_PAIR = 0x09;
  public static final byte CODE_INTSEQ = 0x0A;
  public static final byte CODE_REGEX = 0x0B;
  public static final byte CODE_CLASS = 0x0C;
  public static final byte CODE_TYPEALIAS = 0x0D;
  public static final byte CODE_FUNCTION = 0x0E;
  public static final byte CODE_BYTES = 0x0F;

  public static final byte CODE_PROPERTY = 0x10;
  public static final byte CODE_ENTRY = 0x11;
  public static final byte CODE_ELEMENT = 0x12;
}

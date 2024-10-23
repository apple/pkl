/*
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
package org.pkl.core.stdlib.registry;

import com.oracle.truffle.api.source.SourceSection;
import java.util.HashMap;
import java.util.Map;
import org.pkl.core.ast.ExpressionNode;
import org.pkl.core.ast.expression.primary.GetReceiverNode;
import org.pkl.core.ast.frame.ReadFrameSlotNodeGen;
import org.pkl.core.runtime.VmException;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.*;
import org.pkl.core.stdlib.ExternalPropertyNode.Factory;

public abstract class ExternalMemberRegistry {
  private final Map<String, Factory> propertyFactories = new HashMap<>();
  private final Map<String, ExternalMethod0Node.Factory> function0Factories = new HashMap<>();
  private final Map<String, ExternalMethod1Node.Factory> function1Factories = new HashMap<>();
  private final Map<String, ExternalMethod2Node.Factory> function2Factories = new HashMap<>();
  private final Map<String, ExternalMethod3Node.Factory> function3Factories = new HashMap<>();
  private final Map<String, ExternalMethod4Node.Factory> function4Factories = new HashMap<>();
  private final Map<String, ExternalMethod5Node.Factory> function5Factories = new HashMap<>();

  public final ExpressionNode getPropertyBody(String qualifiedName, SourceSection headerSection) {
    var factory = propertyFactories.get(qualifiedName);
    if (factory == null) throw cannotFindMemberImpl(qualifiedName, headerSection);

    return factory.create(new GetReceiverNode());
  }

  public final ExpressionNode getFunctionBody(
      String qualifiedName, SourceSection headerSection, int paramCount) {

    return switch (paramCount) {
      case 0 -> getFunction0Body(qualifiedName, headerSection);
      case 1 -> getFunction1Body(qualifiedName, headerSection);
      case 2 -> getFunction2Body(qualifiedName, headerSection);
      case 3 -> getFunction3Body(qualifiedName, headerSection);
      case 4 -> getFunction4Body(qualifiedName, headerSection);
      case 5 -> getFunction5Body(qualifiedName, headerSection);
      default ->
          throw new IllegalStateException(
              "External methods with more than 5 parameters are not currently supported.");
    };
  }

  protected void register(String memberName, ExternalPropertyNode.Factory factory) {
    if (propertyFactories.put(memberName, factory) != null) {
      throw duplicateRegistration(memberName);
    }
  }

  protected void register(String memberName, ExternalMethod0Node.Factory factory) {
    if (function0Factories.put(memberName, factory) != null) {
      throw duplicateRegistration(memberName);
    }
  }

  protected void register(String memberName, ExternalMethod1Node.Factory factory) {
    if (function1Factories.put(memberName, factory) != null) {
      throw duplicateRegistration(memberName);
    }
  }

  protected void register(String memberName, ExternalMethod2Node.Factory factory) {
    if (function2Factories.put(memberName, factory) != null) {
      throw duplicateRegistration(memberName);
    }
  }

  protected void register(String memberName, ExternalMethod3Node.Factory factory) {
    if (function3Factories.put(memberName, factory) != null) {
      throw duplicateRegistration(memberName);
    }
  }

  protected void register(String memberName, ExternalMethod4Node.Factory factory) {
    if (function4Factories.put(memberName, factory) != null) {
      throw duplicateRegistration(memberName);
    }
  }

  protected void register(String memberName, ExternalMethod5Node.Factory factory) {
    if (function5Factories.put(memberName, factory) != null) {
      throw duplicateRegistration(memberName);
    }
  }

  private ExpressionNode getFunction0Body(String qualifiedName, SourceSection headerSection) {

    var factory = function0Factories.get(qualifiedName);
    if (factory == null) throw cannotFindMemberImpl(qualifiedName, headerSection);

    return factory.create(new GetReceiverNode());
  }

  private ExpressionNode getFunction1Body(String qualifiedName, SourceSection headerSection) {

    var factory = function1Factories.get(qualifiedName);
    if (factory == null) throw cannotFindMemberImpl(qualifiedName, headerSection);

    var sourceSection = VmUtils.unavailableSourceSection();
    var param1Node = ReadFrameSlotNodeGen.create(sourceSection, 0);
    return factory.create(new GetReceiverNode(), param1Node);
  }

  private ExpressionNode getFunction2Body(String qualifiedName, SourceSection headerSection) {

    var factory = function2Factories.get(qualifiedName);
    if (factory == null) throw cannotFindMemberImpl(qualifiedName, headerSection);

    var sourceSection = VmUtils.unavailableSourceSection();
    var param1Node = ReadFrameSlotNodeGen.create(sourceSection, 0);
    var param2Node = ReadFrameSlotNodeGen.create(sourceSection, 1);
    return factory.create(new GetReceiverNode(), param1Node, param2Node);
  }

  private ExpressionNode getFunction3Body(String qualifiedName, SourceSection headerSection) {

    var factory = function3Factories.get(qualifiedName);
    if (factory == null) throw cannotFindMemberImpl(qualifiedName, headerSection);

    var sourceSection = VmUtils.unavailableSourceSection();
    var param1Node = ReadFrameSlotNodeGen.create(sourceSection, 0);
    var param2Node = ReadFrameSlotNodeGen.create(sourceSection, 1);
    var param3Node = ReadFrameSlotNodeGen.create(sourceSection, 2);
    return factory.create(new GetReceiverNode(), param1Node, param2Node, param3Node);
  }

  private ExpressionNode getFunction4Body(String qualifiedName, SourceSection headerSection) {

    var factory = function4Factories.get(qualifiedName);
    if (factory == null) throw cannotFindMemberImpl(qualifiedName, headerSection);

    var sourceSection = VmUtils.unavailableSourceSection();
    var param1Node = ReadFrameSlotNodeGen.create(sourceSection, 0);
    var param2Node = ReadFrameSlotNodeGen.create(sourceSection, 1);
    var param3Node = ReadFrameSlotNodeGen.create(sourceSection, 2);
    var param4Node = ReadFrameSlotNodeGen.create(sourceSection, 3);
    return factory.create(new GetReceiverNode(), param1Node, param2Node, param3Node, param4Node);
  }

  private ExpressionNode getFunction5Body(String qualifiedName, SourceSection headerSection) {

    var factory = function5Factories.get(qualifiedName);
    if (factory == null) throw cannotFindMemberImpl(qualifiedName, headerSection);

    var sourceSection = VmUtils.unavailableSourceSection();
    var param1Node = ReadFrameSlotNodeGen.create(sourceSection, 0);
    var param2Node = ReadFrameSlotNodeGen.create(sourceSection, 1);
    var param3Node = ReadFrameSlotNodeGen.create(sourceSection, 2);
    var param4Node = ReadFrameSlotNodeGen.create(sourceSection, 3);
    var param5Node = ReadFrameSlotNodeGen.create(sourceSection, 4);
    return factory.create(
        new GetReceiverNode(), param1Node, param2Node, param3Node, param4Node, param5Node);
  }

  private VmException duplicateRegistration(String memberName) {
    return new VmExceptionBuilder()
        .bug("Duplicate registration of external member `%s`.", memberName)
        .build();
  }

  private VmException cannotFindMemberImpl(String qualifiedName, SourceSection headerSection) {
    return new VmExceptionBuilder()
        .bug("Cannot find implementation of external member `%s`.", qualifiedName)
        .withSourceSection(headerSection)
        .withMemberName(qualifiedName)
        .build();
  }
}

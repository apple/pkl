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
package org.pkl.core.stdlib.platform;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.Platform;
import org.pkl.core.Platform.Language;
import org.pkl.core.Platform.OperatingSystem;
import org.pkl.core.Platform.Runtime;
import org.pkl.core.Platform.VirtualMachine;
import org.pkl.core.runtime.PlatformModule;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.stdlib.VmObjectFactory;

public final class PlatformNodes {
  private PlatformNodes() {}

  public abstract static class current extends ExternalPropertyNode {
    private static final VmObjectFactory<Language> languageFactory =
        new VmObjectFactory<Language>(PlatformModule::getLanguageClass)
            .addStringProperty("version", Language::version);

    private static final VmObjectFactory<Runtime> runtimeFactory =
        new VmObjectFactory<Runtime>(PlatformModule::getRuntimeClass)
            .addStringProperty("name", Runtime::name)
            .addStringProperty("version", Runtime::version);

    private static final VmObjectFactory<VirtualMachine> virtualMachineFactory =
        new VmObjectFactory<VirtualMachine>(PlatformModule::getVirtualMachineClass)
            .addStringProperty("name", VirtualMachine::name)
            .addStringProperty("version", VirtualMachine::version);

    private static final VmObjectFactory<OperatingSystem> operatingSystemFactory =
        new VmObjectFactory<OperatingSystem>(PlatformModule::getOperatingSystemClass)
            .addStringProperty("name", OperatingSystem::name)
            .addStringProperty("version", OperatingSystem::version);

    private static final VmObjectFactory<Platform.Processor> processorFactory =
        new VmObjectFactory<Platform.Processor>(PlatformModule::getProcessorClass)
            .addStringProperty("architecture", Platform.Processor::architecture);

    private static final VmObjectFactory<Platform> platformFactory =
        new VmObjectFactory<Platform>(PlatformModule::getPlatformClass)
            .addTypedProperty("language", platform -> languageFactory.create(platform.language()))
            .addTypedProperty("runtime", platform -> runtimeFactory.create(platform.runtime()))
            .addTypedProperty(
                "virtualMachine",
                platform -> virtualMachineFactory.create(platform.virtualMachine()))
            .addTypedProperty(
                "operatingSystem",
                platform -> operatingSystemFactory.create(platform.operatingSystem()))
            .addTypedProperty(
                "processor", platform -> processorFactory.create(platform.processor()));

    @Specialization
    @TruffleBoundary
    protected Object eval(@SuppressWarnings("unused") VmTyped self) {
      return platformFactory.create(Platform.current());
    }
  }
}

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
package org.pkl.core.stdlib.release;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import org.pkl.core.Release;
import org.pkl.core.Release.Documentation;
import org.pkl.core.Release.SourceCode;
import org.pkl.core.Release.StandardLibrary;
import org.pkl.core.runtime.ReleaseModule;
import org.pkl.core.runtime.VmSet;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.ExternalPropertyNode;
import org.pkl.core.stdlib.VmObjectFactories;
import org.pkl.core.stdlib.VmObjectFactory;

public final class ReleaseNodes {
  private ReleaseNodes() {}

  public abstract static class current extends ExternalPropertyNode {
    private static final VmObjectFactory<SourceCode> sourceCodeFactory =
        new VmObjectFactory<SourceCode>(ReleaseModule::getSourceCodeClass)
            .addStringProperty("homepage", SourceCode::homepage);

    private static final VmObjectFactory<Documentation> documentationFactory =
        new VmObjectFactory<Documentation>(ReleaseModule::getDocumentationClass)
            .addStringProperty("homepage", Documentation::homepage);

    private static final VmObjectFactory<StandardLibrary> standardLibraryFactory =
        new VmObjectFactory<StandardLibrary>(ReleaseModule::getStandardLibraryClass)
            .addSetProperty("modules", stdlib -> VmSet.create(stdlib.modules()));

    private static final VmObjectFactory<Release> releaseFactory =
        new VmObjectFactory<Release>(ReleaseModule::getReleaseClass)
            .addTypedProperty(
                "version", release -> VmObjectFactories.versionFactory.create(release.version()))
            .addStringProperty("versionInfo", Release::versionInfo)
            .addStringProperty("commitId", Release::commitId)
            .addTypedProperty(
                "sourceCode", release -> sourceCodeFactory.create(release.sourceCode()))
            .addTypedProperty(
                "documentation", release -> documentationFactory.create(release.documentation()))
            .addTypedProperty(
                "standardLibrary",
                release -> standardLibraryFactory.create(release.standardLibrary()));

    @Specialization
    @TruffleBoundary
    protected Object eval(@SuppressWarnings("unused") VmTyped self) {
      return releaseFactory.create(Release.current());
    }
  }
}

package org.pkl.core.plugins.impl;

import java.util.Optional;
import org.graalvm.polyglot.Context.Builder;
import org.pkl.core.plugin.ContextEventListener;
import org.pkl.core.plugin.PklPlugin;

/** Built-in plugin which tunes the VM execution context. */
@SuppressWarnings("unused") public class VmEnginePlugin implements PklPlugin {
  class VmContextListener implements ContextEventListener {
    @Override
    public void onCreateContext(Builder builder) {
      builder.allowNativeAccess(true);
      builder.allowExperimentalOptions(true);
      builder.allowValueSharing(true);
    }
  }

  // VM context listener.
  private final VmContextListener listener = new VmContextListener();

  @Override
  public String getName() {
    return "VM Engine (Base)";
  }

  @Override
  public Optional<ContextEventListener> contextEventListener() {
    return Optional.of(listener);
  }
}

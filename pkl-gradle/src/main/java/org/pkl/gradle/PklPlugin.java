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
package org.pkl.gradle;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.Convention;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.util.GradleVersion;
import org.pkl.cli.CliEvaluatorOptions;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.LateInit;
import org.pkl.gradle.spec.BasePklSpec;
import org.pkl.gradle.spec.CodeGenSpec;
import org.pkl.gradle.spec.EvalSpec;
import org.pkl.gradle.spec.JavaCodeGenSpec;
import org.pkl.gradle.spec.KotlinCodeGenSpec;
import org.pkl.gradle.spec.ModulesSpec;
import org.pkl.gradle.spec.PkldocSpec;
import org.pkl.gradle.spec.ProjectPackageSpec;
import org.pkl.gradle.spec.ProjectResolveSpec;
import org.pkl.gradle.spec.TestSpec;
import org.pkl.gradle.task.BasePklTask;
import org.pkl.gradle.task.CodeGenTask;
import org.pkl.gradle.task.EvalTask;
import org.pkl.gradle.task.JavaCodeGenTask;
import org.pkl.gradle.task.KotlinCodeGenTask;
import org.pkl.gradle.task.ModulesTask;
import org.pkl.gradle.task.PkldocTask;
import org.pkl.gradle.task.ProjectPackageTask;
import org.pkl.gradle.task.ProjectResolveTask;
import org.pkl.gradle.task.TestTask;

@SuppressWarnings("unused")
public class PklPlugin implements Plugin<Project> {

  private static final String MIN_GRADLE_VERSION = "8.1";

  @LateInit private Project project;

  @Override
  public void apply(Project project) {
    this.project = project;

    if (GradleVersion.current().compareTo(GradleVersion.version(MIN_GRADLE_VERSION)) < 0) {
      throw new GradleException(
          String.format("Plugin `org.pkl` requires Gradle %s or higher.", MIN_GRADLE_VERSION));
    }

    var extension = project.getExtensions().create("pkl", PklExtension.class);
    configureExtension(extension);
  }

  private void configureExtension(PklExtension extension) {
    configureEvalTasks(extension.getEvaluators());
    configureJavaCodeGenTasks(extension.getJavaCodeGenerators());
    configureKotlinCodeGenTasks(extension.getKotlinCodeGenerators());
    configurePkldocTasks(extension.getPkldocGenerators());
    configureTestTasks(extension.getTests());
    configureProjectPackageTasks(extension.getProject().getPackagers());
    configureProjectResolveTasks(extension.getProject().getResolvers());
  }

  private void configureProjectPackageTasks(NamedDomainObjectContainer<ProjectPackageSpec> specs) {
    specs.all(
        spec -> {
          configureBaseSpec(spec);
          spec.getOutputPath()
              .convention(project.getLayout().getBuildDirectory().dir("generated/pkl/packages"));
          spec.getOverwrite().convention(false);
          var packageTask = createTask(ProjectPackageTask.class, spec);
          packageTask.configure(
              task -> {
                task.getProjectDirectories().from(spec.getProjectDirectories());
                task.getOutputPath().set(spec.getOutputPath());
                task.getSkipPublishCheck().set(spec.getSkipPublishCheck());
                task.getJunitReportsDir().set(spec.getJunitReportsDir());
                task.getOverwrite().set(spec.getOverwrite());
              });
          project
              .getPluginManager()
              .withPlugin(
                  "base",
                  appliedPlugin ->
                      project
                          .getTasks()
                          .named(
                              LifecycleBasePlugin.BUILD_TASK_NAME,
                              it -> it.dependsOn(packageTask)));
        });
  }

  private void configureProjectResolveTasks(NamedDomainObjectContainer<ProjectResolveSpec> specs) {
    specs.all(
        spec -> {
          configureBaseSpec(spec);
          var resolveTask = createTask(ProjectResolveTask.class, spec);
          resolveTask.configure(
              task -> task.getProjectDirectories().from(spec.getProjectDirectories()));
        });
  }

  private void configureEvalTasks(NamedDomainObjectContainer<EvalSpec> specs) {
    specs.all(
        spec -> {
          configureBaseSpec(spec);
          spec.getOutputFile()
              .convention(
                  project
                      .getLayout()
                      .getProjectDirectory()
                      // %{moduleDir} is resolved relatively to the working directory,
                      // and the working directory is set to the project directory,
                      // so this path works correctly.
                      .file("%{moduleDir}/%{moduleName}.%{outputFormat}"));
          spec.getOutputFormat().convention("pcf");
          spec.getModuleOutputSeparator()
              .convention(CliEvaluatorOptions.Companion.getDefaults().getModuleOutputSeparator());
          spec.getExpression()
              .convention(CliEvaluatorOptions.Companion.getDefaults().getExpression());

          createModulesTask(EvalTask.class, spec)
              .configure(
                  task -> {
                    task.getOutputFile().set(spec.getOutputFile());
                    task.getOutputFormat().set(spec.getOutputFormat());
                    task.getModuleOutputSeparator().set(spec.getModuleOutputSeparator());
                    task.getMultipleFileOutputDir().set(spec.getMultipleFileOutputDir());
                    task.getExpression().set(spec.getExpression());
                  });
        });
  }

  private void configureJavaCodeGenTasks(NamedDomainObjectContainer<JavaCodeGenSpec> specs) {
    specs.all(
        spec -> {
          configureBaseSpec(spec);
          configureCodeGenSpec(spec);

          spec.getGenerateGetters().convention(false);
          spec.getGenerateJavadoc().convention(false);

          createModulesTask(JavaCodeGenTask.class, spec)
              .configure(
                  task -> {
                    task.setDescription(TaskConstants.GENERATE_JAVA_DESCRIPTION);
                    task.setGroup(TaskConstants.TASK_GROUP_CODEGEN);
                    configureCodeGenTask(task, spec);
                    task.getGenerateGetters().set(spec.getGenerateGetters());
                    task.getGenerateJavadoc().set(spec.getGenerateJavadoc());
                    task.getParamsAnnotation().set(spec.getParamsAnnotation());
                    task.getNonNullAnnotation().set(spec.getNonNullAnnotation());
                  });
        });

    project.afterEvaluate(
        prj ->
            specs.all(
                spec -> {
                  configureIdeaModule(spec);
                  configureCodeGenSpecSourceDirectories(
                      spec, "java", s -> Optional.of(s.getJava()));
                }));
  }

  private void configureKotlinCodeGenTasks(NamedDomainObjectContainer<KotlinCodeGenSpec> specs) {
    specs.all(
        spec -> {
          configureBaseSpec(spec);
          configureCodeGenSpec(spec);

          spec.getGenerateKdoc().convention(false);

          createModulesTask(KotlinCodeGenTask.class, spec)
              .configure(
                  task -> {
                    task.setDescription(TaskConstants.GENERATE_KOTLIN_DESCRIPTION);
                    task.setGroup(TaskConstants.TASK_GROUP_CODEGEN);
                    configureCodeGenTask(task, spec);
                    task.getGenerateKdoc().set(spec.getGenerateKdoc());
                  });
        });

    project.afterEvaluate(
        prj ->
            specs.all(
                spec -> {
                  configureIdeaModule(spec);
                  configureCodeGenSpecSourceDirectories(
                      spec, "kotlin", this::getKotlinSourceDirectorySet);
                }));
  }

  private void configurePkldocTasks(NamedDomainObjectContainer<PkldocSpec> specs) {
    specs.all(
        spec -> {
          configureBaseSpec(spec);

          spec.getOutputDir()
              .convention(
                  project
                      .getLayout()
                      .getBuildDirectory()
                      .map(it -> it.dir("pkldoc").dir(spec.getName())));

          createModulesTask(PkldocTask.class, spec)
              .configure(
                  task -> {
                    task.setDescription(TaskConstants.GENERATE_PKLDOC_DESCRIPTION);
                    task.setGroup(TaskConstants.TASK_GROUP_DOCS);
                    task.getOutputDir().set(spec.getOutputDir());
                  });
        });
  }

  private void configureTestTasks(NamedDomainObjectContainer<TestSpec> specs) {
    specs.all(
        spec -> {
          configureBaseSpec(spec);

          spec.getOverwrite().convention(false);

          var testTask = createModulesTask(TestTask.class, spec);
          testTask.configure(
              task -> {
                task.setDescription(TaskConstants.GENERATE_PKLDOC_DESCRIPTION);
                task.setGroup(TaskConstants.TASK_GROUP_DOCS);
                task.getJunitReportsDir().set(spec.getJunitReportsDir());
                task.getOverwrite().set(spec.getOverwrite());
              });

          project
              .getPluginManager()
              .withPlugin(
                  "base",
                  appliedPlugin ->
                      project
                          .getTasks()
                          .named(
                              LifecycleBasePlugin.CHECK_TASK_NAME,
                              checkTask -> checkTask.dependsOn(testTask)));
        });
  }

  private void configureBaseSpec(BasePklSpec spec) {
    spec.getAllowedModules()
        .convention(
            List.of(
                "repl:", "file:", "modulepath:", "https:", "pkl:", "package:", "projectpackage:"));

    spec.getAllowedResources()
        .convention(List.of("env:", "prop:", "file:", "modulepath:", "https:", "package:"));

    spec.getEvalRootDir().convention(project.getRootProject().getLayout().getProjectDirectory());

    // Defaulting to OS env vars is bad for reproducibility and cachability.
    // Hence, this spec defaults to empty env vars, which is consistent with other Gradle tasks
    // (e.g., Test) but inconsistent with the Pkl CLI.
    // Therefore, we don't set any initial value for the environmentVariables property.

    // Not using `convention()` to allow the user to unset this property, disabling the cache.
    spec.getModuleCacheDir().set(IoUtils.getDefaultModuleCacheDir().toFile());

    spec.getNoCache().convention(false);

    spec.getTestPort().convention(-1);

    spec.getHttpNoProxy().convention(List.of());
  }

  private void configureCodeGenSpec(CodeGenSpec spec) {
    spec.getOutputDir()
        .convention(
            project
                .getLayout()
                .getBuildDirectory()
                .map(it -> it.dir("generated").dir("pkl").dir(spec.getName())));

    spec.getSourceSet()
        .convention(
            project
                .getProviders()
                .provider(
                    () -> {
                      var sourceSets = project.getExtensions().findByType(SourceSetContainer.class);
                      if (sourceSets == null) {
                        return null;
                      }
                      return sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
                    }));

    spec.getIndent().convention("  ");

    spec.getGenerateSpringBootConfig().convention(false);

    spec.getImplementSerializable().convention(false);

    configureCodeGenSpecModulePath(spec);
  }

  private void configureCodeGenSpecModulePath(CodeGenSpec spec) {
    // Set module path to all of the configured resources source directories and the compile
    // classpath to find Pkl modules that are classpath resources.
    // Compilation classpath should be correct (vs. runtime classpath) if the library author
    // properly declares upstream libraries as `api` dependencies.
    // We must not use the processResources task as an input here, because it would introduce
    // a circular dependency (because codegen also generates a resources directory).
    //
    // Also note that in this case, we are NOT setting a dependency from the
    // spec.getModulePath() file collection to the sourceSet.getResources().getSourceDirectories()
    // file collection. Doing that would make spec.getModulePath() propagate a dependency
    // on the tasks which contribute to sourceSet.getResources().getSourceDirectories(),
    // and one of them is our own codegen task, which would result in a circular dependency.
    // Refer to configureCodeGenSpecSourceDirectories for logic which links the codegen task
    // to sourceSet.getResources().getSourceDirectories().

    var modulePath = project.files();
    modulePath
        .from(getResourceSourceDirectoriesExceptSpecOutput(spec))
        // This technically breaks the dependency on compile classpath builder tasks,
        // however, compile classpath on a source set is always a plain configuration which
        // has no builder tasks, so this is not an issue.
        .from(spec.getSourceSet().map(SourceSet::getCompileClasspath));
    spec.getModulePath().from(modulePath);
  }

  private Provider<Set<File>> getResourceSourceDirectoriesExceptSpecOutput(CodeGenSpec spec) {
    // Intentionally break the dependency on source set's resources source directory set
    // builder tasks by using `getFiles()` instead of `FileCollection`
    // returned by `getSourceDirectories()`.
    // Additionally, we must exclude our own output, to avoid creating circular dependencies
    // at runtime which invalidate task execution cache.
    return spec.getSourceSet()
        .flatMap(
            sourceSet ->
                spec.getOutputDir()
                    .map(
                        specOutputDir ->
                            sourceSet
                                .getResources()
                                .getSourceDirectories()
                                .filter(
                                    f ->
                                        !f.getAbsolutePath()
                                            .startsWith(
                                                specOutputDir.getAsFile().getAbsolutePath()))
                                .getFiles()));
  }

  // Must be called from Project.afterEvaluate() only, because this method depends
  // on user-provided configuration not accessible with lazy configuration.
  private void configureCodeGenSpecSourceDirectories(
      CodeGenSpec spec,
      String languageName,
      Function<? super SourceSet, ? extends Optional<SourceDirectorySet>>
          extractSourceDirectorySet) {
    var task = project.getTasks().named(spec.getName(), CodeGenTask.class);
    var sourceSet = spec.getSourceSet().get();
    extractSourceDirectorySet
        .apply(sourceSet)
        .ifPresentOrElse(
            dirSet -> dirSet.srcDir(task.flatMap(t -> t.getOutputDir().dir(languageName))),
            () ->
                project
                    .getLogger()
                    .debug(
                        "Source directory set for language {} is not available, "
                            + "will not add task {} as its dependency",
                        languageName,
                        task.getName()));
    sourceSet.getResources().srcDir(task.flatMap(t -> t.getOutputDir().dir("resources")));
  }

  // Must be called from Project.afterEvaluate() only, because this method depends
  // on user-provided configuration not accessible with lazy configuration.
  private void configureIdeaModule(CodeGenSpec spec) {
    project
        .getPluginManager()
        .withPlugin(
            "idea",
            plugin -> {
              var module = project.getExtensions().getByType(IdeaModel.class).getModule();
              var outputDir = spec.getOutputDir().get().getAsFile();
              module.getGeneratedSourceDirs().add(outputDir);
              if (spec.getSourceSet().get().getName().toLowerCase().contains("test")) {
                module.getTestSources().from(append(module.getTestSources().getFiles(), outputDir));
              } else {
                module.setSourceDirs(append(module.getSourceDirs(), outputDir));
              }
            });
  }

  private void configureCodeGenTask(CodeGenTask task, CodeGenSpec spec) {
    task.getIndent().set(spec.getIndent());
    task.getOutputDir().set(spec.getOutputDir());
    task.getGenerateSpringBootConfig().set(spec.getGenerateSpringBootConfig());
    task.getImplementSerializable().set(spec.getImplementSerializable());
    task.getRenames().set(spec.getRenames());
  }

  private <T extends BasePklTask, S extends BasePklSpec> void configureBaseTask(T task, S spec) {
    task.getAllowedModules().set(spec.getAllowedModules());
    task.getAllowedResources().set(spec.getAllowedResources());
    task.getEnvironmentVariables().set(spec.getEnvironmentVariables());
    task.getExternalProperties().set(spec.getExternalProperties());
    task.getModulePath().from(spec.getModulePath());
    task.getSettingsModule().set(spec.getSettingsModule());
    task.getEvalRootDir().set(spec.getEvalRootDir());
    task.getNoCache().set(spec.getNoCache());
    task.getModuleCacheDir().set(spec.getModuleCacheDir());
    task.getEvalTimeout().set(spec.getEvalTimeout());
    task.getTestPort().set(spec.getTestPort());
    task.getHttpProxy().set(spec.getHttpProxy());
    task.getHttpNoProxy().set(spec.getHttpNoProxy());
  }

  private <T extends ModulesTask, S extends ModulesSpec> void configureModulesTask(T task, S spec) {
    configureBaseTask(task, spec);
    task.getSourceModules().set(spec.getSourceModules());
    task.getTransitiveModules().from(spec.getTransitiveModules());
    task.getNoProject().set(spec.getNoProject());
    task.getProjectDir().set(spec.getProjectDir());
    task.getOmitProjectSettings().set(spec.getOmitProjectSettings());
  }

  private <T extends ModulesTask> TaskProvider<T> createModulesTask(
      Class<T> taskClass, ModulesSpec spec) {
    return project
        .getTasks()
        .register(spec.getName(), taskClass, task -> configureModulesTask(task, spec));
  }

  private <T extends BasePklTask> TaskProvider<T> createTask(Class<T> taskClass, BasePklSpec spec) {
    return project
        .getTasks()
        .register(spec.getName(), taskClass, task -> configureBaseTask(task, spec));
  }

  private <T> Set<T> append(Set<? extends T> set1, T element) {
    Set<T> result = new LinkedHashSet<>(set1.size() + 1);
    result.addAll(set1);
    result.add(element);
    return result;
  }

  private Optional<SourceDirectorySet> getKotlinSourceDirectorySet(SourceSet sourceSet) {
    // First, try loading it as an extension - 1.8+ version of Kotlin plugin does this.
    var kotlinExtension = sourceSet.getExtensions().findByName("kotlin");
    if (kotlinExtension instanceof SourceDirectorySet sourceDirSet) {
      return Optional.of(sourceDirSet);
    }

    // Otherwise, try to load it as a convention. First, we attempt to get the convention
    // object of the source set via the HasConvention.getConvention() method.
    // We don't use the HasConvention interface directly as it is deprecated.
    // Then, we extract the `kotlin` plugin from the convention, which "provides"
    // the additional properties for the source set. This plugin has a method named
    // `getKotlin` whose return type is a source directory set, so we use reflection
    // to call it too.
    // Basically, this is equivalent to calling `sourceSet.kotlin`, where `kotlin` is a property
    // contributed by a plugin also named `kotlin`.
    // This part of logic can be removed once we stop supporting Kotlin plugin with version
    // less than 1.8.x.
    try {
      var getConventionMethod = sourceSet.getClass().getMethod("getConvention");
      var convention = getConventionMethod.invoke(sourceSet);
      //noinspection deprecation
      if (convention instanceof Convention c) {
        //noinspection deprecation
        var kotlinSourceSet = c.getPlugins().get("kotlin");
        if (kotlinSourceSet == null) {
          project
              .getLogger()
              .debug(
                  "Cannot obtain Kotlin source directory set of source set [{}], "
                      + "it does not have the `kotlin` convention plugin",
                  sourceSet.getName());
          return Optional.empty();
        }

        var getKotlinMethod = kotlinSourceSet.getClass().getMethod("getKotlin");
        var kotlinSourceDirectorySet = getKotlinMethod.invoke(kotlinSourceSet);
        if (kotlinSourceDirectorySet instanceof SourceDirectorySet sourceDirSet) {
          return Optional.of(sourceDirSet);
        }

        project
            .getLogger()
            .debug(
                "Cannot obtain Kotlin source directory set, sourceSets.{}.kotlin is of wrong type",
                sourceSet.getName());
      } else {
        project
            .getLogger()
            .debug(
                "Cannot obtain Kotlin source directory set, sourceSets.{}.convention "
                    + "returned unexpected type",
                sourceSet.getName());
      }

    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      project
          .getLogger()
          .debug(
              "Cannot obtain Kotlin source directory set of source set [{}] via a convention",
              sourceSet.getName(),
              e);
    }

    return Optional.empty();
  }
}

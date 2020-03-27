/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugin.devel.internal.precompiled;

import groovy.lang.Script;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.ScriptRunner;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.internal.CompiledScript;
import org.gradle.groovy.scripts.internal.ScriptRunnerFactory;
import org.gradle.internal.resource.StringTextResource;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.plugin.use.internal.PluginsAwareScript;

public class PreCompiledScriptRunner {

    private final Object target;
    private final ServiceRegistry serviceRegistry;

    private final ScriptRunnerFactory scriptRunnerFactory;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final PluginRequestApplicator pluginRequestApplicator;

    private final ClassLoaderScope classLoaderScope;
    private final PluginManagerInternal pluginManager;

    @SuppressWarnings("unused")
    public PreCompiledScriptRunner(Project project) {
        this(project, ((ProjectInternal) project).getServices(), ((ProjectInternal) project).getClassLoaderScope(), ((ProjectInternal) project).getPluginManager());
    }

    @SuppressWarnings("unused")
    public PreCompiledScriptRunner(Settings settings) {
        this(settings, ((SettingsInternal) settings).getGradle().getServices(), ((SettingsInternal) settings).getClassLoaderScope(), null);
    }

    @SuppressWarnings("unused")
    public PreCompiledScriptRunner(Gradle gradle) {
        this(gradle, ((GradleInternal) gradle).getServices(), ((GradleInternal) gradle).getClassLoaderScope().createChild("init-plugin"), null);
    }

    private PreCompiledScriptRunner(Object target, ServiceRegistry serviceRegistry, ClassLoaderScope classLoaderScope, PluginManagerInternal pluginManager) {
        this.target = target;
        this.serviceRegistry = serviceRegistry;

        this.scriptRunnerFactory = serviceRegistry.get(ScriptRunnerFactory.class);
        this.scriptHandlerFactory = serviceRegistry.get(ScriptHandlerFactory.class);
        this.pluginRequestApplicator = serviceRegistry.get(PluginRequestApplicator.class);

        this.classLoaderScope = classLoaderScope;
        this.pluginManager = pluginManager;
    }

    public void run(Class<?> pluginsBlockClass, Class<?> precompiledScriptClass) {
        classLoaderScope.lock();

        if (pluginsBlockClass != null) {
            applyPlugins(pluginsBlockClass);
        }

        if (precompiledScriptClass != null) {
            executeScript(precompiledScriptClass);
        }
    }

    private void applyPlugins(Class<?> pluginsBlockClass) {
        ScriptSource scriptSource = new PrecompiledScriptSource(pluginsBlockClass);
        CompiledScript<PluginsAwareScript, ?> compiledPlugins = new CompiledGroovyPlugin<>(pluginsBlockClass, PluginsAwareScript.class);
        ScriptRunner<PluginsAwareScript, ?> runner = scriptRunnerFactory.create(compiledPlugins, scriptSource, classLoaderScope.getExportClassLoader());
        runner.run(target, serviceRegistry);

        ScriptHandlerInternal scriptHandler = scriptHandlerFactory.create(scriptSource, classLoaderScope);
        pluginRequestApplicator.applyPlugins(runner.getScript().getPluginRequests(), scriptHandler, pluginManager, classLoaderScope);
    }

    private void executeScript(Class<?> precompiledScriptClass) {
        ScriptSource scriptSource = new PrecompiledScriptSource(precompiledScriptClass);
        CompiledScript<BasicScript, ?> compiledScript = new CompiledGroovyPlugin<>(precompiledScriptClass, BasicScript.class);
        ScriptRunner<? extends BasicScript, ?> runner = scriptRunnerFactory.create(compiledScript, scriptSource, classLoaderScope.getExportClassLoader());
        runner.run(target, serviceRegistry);
    }

    private class CompiledGroovyPlugin<T extends Script> implements CompiledScript<T, Object> {

        private final Class<? extends T> compiledClass;

        private CompiledGroovyPlugin(Class<?> scriptClass, Class<T> scriptBaseClass) {
            this.compiledClass = scriptClass.asSubclass(scriptBaseClass);
        }

        @Override
        public boolean getRunDoesSomething() {
            return true;
        }

        @Override
        public boolean getHasMethods() {
            return false;
        }

        @Override
        public Class<? extends T> loadClass() {
            return compiledClass;
        }

        @Override
        public Object getData() {
            return null;
        }

        @Override
        public void onReuse() {
            classLoaderScope.onReuse();
        }
    }

    private static class PrecompiledScriptSource implements ScriptSource {

        private final Class<?> scriptClass;

        private PrecompiledScriptSource(Class<?> scriptClass) {
            this.scriptClass = scriptClass;
        }

        @Override
        public String getClassName() {
            return scriptClass.getSimpleName();
        }

        @Override
        public TextResource getResource() {
            return new StringTextResource(scriptClass.getSimpleName(), "");
        }

        @Override
        public String getFileName() {
            return scriptClass.getSimpleName() + ".class";
        }

        @Override
        public String getDisplayName() {
            return scriptClass.getSimpleName();
        }
    }
}

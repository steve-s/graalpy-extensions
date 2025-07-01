/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.python.tasks;

import org.graalvm.python.GradleLogger;
import org.graalvm.python.dsl.GraalPyExtension;
import org.graalvm.python.embedding.tools.vfs.VFSUtils;
import org.graalvm.python.embedding.tools.vfs.VFSUtils.Launcher;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.graalvm.python.embedding.tools.vfs.VFSUtils.LAUNCHER_NAME;

/**
 * This task is responsible installing the dependencies which were requested by the user.
 * This is either done in generated resources folder or in external directory provided by the user
 * in {@link GraalPyExtension#getExternalDirectory()}.
 *
 * <p/>
 * In scope of this task:
 * <ol>
 *     <li>The GraalPy launcher is set up.</li>
 *     <li>A python venv is created.</li>
 *     <li>Python packages are installed into the venv.</li>
 * </ol>
 *
 */
public abstract class AbstractPackagesTask extends DefaultTask {

    @Input
    public abstract ListProperty<String> getPackages();

    @Internal
    public abstract DirectoryProperty getLauncherDirectory();

    @Classpath
    public abstract ConfigurableFileCollection getLauncherClasspath();

    /**
     * The directory where the virtual filesystem should be generated.
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getGraalPyLockFile();

    @Internal
    public abstract RegularFileProperty getVenvDirectory();

    /**
     * Desired polyglot runtime and GraalPy version.
     */
    @Input
    public abstract Property<String> getPolyglotVersion();

    protected Set<String> calculateLauncherClasspath() {
        return getLauncherClasspath().getFiles().stream().map(File::getAbsolutePath).collect(Collectors.toUnmodifiableSet());
    }

    protected Launcher createLauncher() {
        return new Launcher( getLauncherPath()) {
            public Set<String> computeClassPath() {
                return calculateLauncherClasspath();
            }
        };
    }

    @Internal
    protected GradleLogger getLog() {
        return GradleLogger.of(getLogger());
    }

    private Path getLauncherPath() {
        return computeLauncherDirectory().resolve(LAUNCHER_NAME);
    }

    @NotNull
    protected Path computeLauncherDirectory() {
        return getLauncherDirectory().get().getAsFile().toPath();
    }

    @Internal
    protected Path getLockFilePath() {
        return getGraalPyLockFile().get().getAsFile().toPath();
    }

}

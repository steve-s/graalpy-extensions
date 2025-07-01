/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.python.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.graalvm.python.embedding.tools.vfs.VFSUtils;
import org.graalvm.python.embedding.tools.vfs.VFSUtils.PackagesChangedException;

import java.io.IOException;
import java.nio.file.Path;

@Mojo(name = "process-graalpy-resources", defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
                requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME,
                requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class InstallPackagesMojo extends AbstractGraalPyMojo {

    private static final String PACKAGES_CHANGED_ERROR = """
        
        Install of python packages is based on lock file %s,
        but packages and their version constraints in graalpy-maven-plugin configuration are different then previously used to generate the lock file.
        
        Packages currently declared in graalpy-maven-plugin configuration: %s
        Packages which were used to generate the lock file: %s
         
        The lock file has to be refreshed by running the maven goal 'org.graalvm.python:graalpy-maven-plugin:lock-packages'.
        
        For more information, please refer to https://www.graalvm.org/latest/reference-manual/python/Embedding-Build-Tools#Python-Dependency-Management                
        """;

    protected static final String MISSING_LOCK_FILE_WARNING = """
        
        The list of installed Python packages does not match the packages specified in the graalpy-maven-plugin configuration.
        This could indicate that either extra dependencies were installed or some packages were installed with a more specific versions than declared.  
        
        In such cases, it is strongly recommended to lock the Python dependencies by executing the Maven goal 'org.graalvm.python:graalpy-maven-plugin:lock-packages'.
        
        For more details on managing Python dependencies, please refer to https://www.graalvm.org/latest/reference-manual/python/Embedding-Build-Tools#Python-Dependency-Management
        
        """;

    public void execute() throws MojoExecutionException {
        preExec(true);

        manageVenv();
        listGraalPyResources();
        manageNativeImageConfig();

        postExec();
    }

    private void manageVenv() throws MojoExecutionException {
        Path venvDirectory = getVenvDirectory();
        MavenDelegateLog log = new MavenDelegateLog(getLog());
        Path lockFile = getLockFile();
        try {
            VFSUtils.createVenv(venvDirectory, packages, lockFile, MISSING_LOCK_FILE_WARNING, createLauncher(), getGraalPyVersion(project), log);
        } catch(PackagesChangedException pce) {
            String pluginPkgsString = pce.getPluginPackages().isEmpty() ? "None" : String.join(", ", pce.getPluginPackages());
            String lockFilePkgsString = pce.getLockFilePackages().isEmpty() ? "None" : String.join(", ", pce.getLockFilePackages());
            throw new MojoExecutionException(String.format(PACKAGES_CHANGED_ERROR, lockFile, pluginPkgsString, lockFilePkgsString));
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("failed to create venv %s", venvDirectory), e);
        }
    }

}

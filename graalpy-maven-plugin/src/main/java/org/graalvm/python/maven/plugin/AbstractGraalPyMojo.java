/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.*;
import org.eclipse.aether.graph.Dependency;
import org.graalvm.python.embedding.tools.vfs.VFSUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.graalvm.python.embedding.tools.vfs.VFSUtils.*;

public abstract class AbstractGraalPyMojo extends AbstractMojo {

    private static final String PYTHON_LAUNCHER_ARTIFACT_ID = "python-launcher";

    private static final String POLYGLOT_GROUP_ID = "org.graalvm.polyglot";
    private static final String PYTHON_COMMUNITY_ARTIFACT_ID = "python-community";
    private static final String PYTHON_ARTIFACT_ID = "python";
    private static final String GRAALPY_MAVEN_PLUGIN_ARTIFACT_ID = "graalpy-maven-plugin";

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Parameter
    String pythonResourcesDirectory;

    @Parameter(property = "externalDirectory")
    String externalDirectory;

    @Parameter(property = "resourceDirectory")
    String resourceDirectory;
    
    @Parameter(property = "graalPyLockFile", defaultValue = "graalpy.lock")
    String graalPyLockFile;

    @Parameter
    List<String> packages;

    public static class PythonHome {
        private List<String> includes;
        private List<String> excludes;
    }

    @Parameter
    PythonHome pythonHome;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component
    private ProjectBuilder projectBuilder;

    private Set<String> launcherClassPath;

    protected void manageNativeImageConfig() throws MojoExecutionException {
        try {
            VFSUtils.writeNativeImageConfig(Path.of(project.getBuild().getOutputDirectory(), "META-INF"), GRAALPY_MAVEN_PLUGIN_ARTIFACT_ID, resourceDirectory);
        } catch (IOException e) {
            throw new MojoExecutionException("failed to create native image configuration files", e);
        }
    }

    protected void listGraalPyResources() throws MojoExecutionException {
        Path vfs = Path.of(project.getBuild().getOutputDirectory(), resourceDirectory);
        if (Files.exists(vfs)) {
            try {
                VFSUtils.generateVFSFilesList(Path.of(project.getBuild().getOutputDirectory()), vfs);
            } catch (IOException e) {
                throw new MojoExecutionException(String.format("Failed to generate files list in '%s'", vfs), e);
            }
        }
    }

    protected void preExec(boolean enableWarnings) throws MojoExecutionException {
        pythonResourcesDirectory = normalizeEmpty(pythonResourcesDirectory);
        externalDirectory = normalizeEmpty(externalDirectory);
        resourceDirectory = normalizeEmpty(resourceDirectory);
        graalPyLockFile = normalizeEmpty(graalPyLockFile);
        packages = packages != null ? packages.stream().filter(p -> p != null && !p.trim().isEmpty()).toList() : Collections.EMPTY_LIST;

        if(pythonResourcesDirectory != null) {
            if (externalDirectory != null) {
                throw new MojoExecutionException(
                        "Cannot use <externalDirectory> and <resourceDirectory> at the same time. " +
                                "New option <externalDirectory> is a replacement for deprecated <pythonResourcesDirectory>. " +
                                "If you want to deploy the virtual environment into physical filesystem, use <externalDirectory>. " +
                                "The deployment of the external directory alongside the application is not handled by the GraalPy Maven plugin in such case." +
                                "If you want to bundle the virtual filesystem in Java resources, use <resourceDirectory>. " +
                                "For more details, please refer to https://www.graalvm.org/latest/reference-manual/python/Embedding-Build-Tools. ");
            }
            getLog().warn("Option <pythonResourcesDirectory> is deprecated and will be removed. Use <externalDirectory> instead.");
            externalDirectory = pythonResourcesDirectory;
        }

        if (resourceDirectory != null) {
            if (resourceDirectory.startsWith("/") || resourceDirectory.endsWith("/")) {
                throw new MojoExecutionException(
                        "Value of <resourceDirectory> should be relative resources path, i.e., without the leading '/', and it also must not end with trailing '/'");
            }
        }

        if (resourceDirectory == null) {
            if (enableWarnings && externalDirectory == null) {
                getLog().info(String.format("Virtual filesystem is deployed to default resources directory '%s'. " +
                        "This can cause conflicts if used with other Java libraries that also deploy GraalPy virtual filesystem. " +
                        "Consider adding <resourceDirectory>GRAALPY-VFS/${project.groupId}/${project.artifactId}</resourceDirectory> to your pom.xml, " +
                        "moving any existing sources from '%s' to '%s', and using VirtualFileSystem$Builder#resourceDirectory." +
                        "For more details, please refer to https://www.graalvm.org/latest/reference-manual/python/Embedding-Build-Tools. ",
                        VFS_ROOT,
                        Path.of(VFS_ROOT, "src"),
                        Path.of("GRAALPY-VFS", project.getGroupId(), project.getArtifactId())));
            }
            resourceDirectory = VFS_ROOT;
        }

        if(enableWarnings && pythonHome != null) {
            getLog().warn("The GraalPy plugin <pythonHome> configuration setting was deprecated and has no effect anymore.\n" +
                "For execution in jvm mode, the python language home is always available.\n" +
                "When building a native executable using GraalVM Native Image, then the full python language home is by default embedded into the native executable.\n" +
                    "For more details, please refer to the documentation of GraalVM Native Image options IncludeLanguageResources and CopyLanguageResources.");
        }
    }
    
    protected void postExec() throws MojoExecutionException {
        for(Resource r : project.getBuild().getResources()) {
            if (Files.exists(Path.of(r.getDirectory(), resourceDirectory, "proj"))) {
                getLog().warn(String.format("usage of %s is deprecated, use %s instead", Path.of(resourceDirectory, "proj"), Path.of(resourceDirectory, "src")));
            }
            if (!Files.exists(Path.of(r.getDirectory(), resourceDirectory)) && Files.exists(Path.of(r.getDirectory(), "vfs", "proj"))) {
                // there isn't the actual vfs resource root "org.graalvm.python.vfs" (VFS_ROOT), and there is only the outdated "vfs/proj"
                // => looks like a project created < 24.1.0
                throw new MojoExecutionException(String.format(
                        "Wrong virtual filesystem root!\n" +
                        "Since 24.1.0 the virtual filesystem root has to be '%s'.\n" +                       
                        "Please rename the resource directory '%s' to '%s'", resourceDirectory, Path.of(r.getDirectory(), "vfs"), Path.of(r.getDirectory(), resourceDirectory)));
            }
            Path srcPath = Path.of(r.getDirectory(), resourceDirectory, "src");
            if (externalDirectory != null && Files.exists(srcPath)) {
                getLog().warn(String.format(
                        "Found Java resources directory %s, however, the GraalPy Maven plugin is configured to use <externalDirectory> instead of Java resources. " +
                                "The files from %s will not be available in Contexts created using GraalPyResources#contextBuilder(Path). Move them to '%s' if " +
                                "you want to make them available when using external directory, or use Java resources by removing <externalDirectory> option.",
                        srcPath,
                        srcPath,
                        Path.of(externalDirectory, "src")
                ));
            }
        }
    }

    protected Path getVenvDirectory() {
        Path venvDirectory;
        if(externalDirectory == null) {
            venvDirectory = Path.of(project.getBuild().getOutputDirectory(), resourceDirectory, VFS_VENV);
        } else {
            venvDirectory = Path.of(externalDirectory, VFS_VENV);
        }
        return venvDirectory;
    }

    private static String normalizeEmpty(String s) {
        if (s == null) {
            return s;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
        
    protected Launcher createLauncher() {
        Launcher launcherArg = new Launcher(getLauncherPath()) {
            public Set<String> computeClassPath() throws IOException {
                return calculateLauncherClasspath(project);
            }
        };
        return launcherArg;
    }

    protected Path getLockFile() {
        Path rfp = Path.of(graalPyLockFile);
        if(rfp.isAbsolute()) {
            return rfp;
        } else {
            return project.getBasedir().toPath().resolve(graalPyLockFile);
        }
    }

    private void delete(Path dir) throws MojoExecutionException {
        try {
            try (var s = Files.walk(dir)) {
                s.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            new MojoExecutionException(String.format("failed to delete %s", dir),  e);
        }
    }

    private Path getLauncherPath() {
        return Paths.get(project.getBuild().getDirectory(), LAUNCHER_NAME);
    }

    protected static String getGraalPyVersion(MavenProject project) throws IOException {
        DefaultArtifact a = (DefaultArtifact) getGraalPyArtifact(project);
        String version = a.getVersion();
        if(a.isSnapshot()) {
            // getVersion for a snapshot artefact returns base version + timestamp - e.g. 24.2.0-20240808.200816-1
            // and there might be snapshot artefacts with different timestamps in the repository.
            // We should use $baseVersion + "-SNAPSHOT" as maven is in such case
            // able to properly resolve all project artefacts.
            version = a.getBaseVersion();
            if(!version.endsWith("-SNAPSHOT")) {
                // getBaseVersion is expected to return a version without any additional metadata, e.g. 24.2.0-20240808.200816-1 -> 24.2.0,
                // but also saw getBaseVersion() already returning version with -SNAPSHOT suffix
                version = version + "-SNAPSHOT";
            }
        }
        return version;
    }

    private static Artifact getGraalPyArtifact(MavenProject project) throws IOException {
        var projectArtifacts = resolveProjectDependencies(project);
        Artifact graalPyArtifact = projectArtifacts.stream().
                filter(a -> isPythonArtifact(a))
                .findFirst()
                .orElse(null);
        return Optional.ofNullable(graalPyArtifact).orElseThrow(() -> new IOException("Missing GraalPy dependency. Please add to your pom either %s:%s or %s:%s".formatted(POLYGLOT_GROUP_ID, PYTHON_COMMUNITY_ARTIFACT_ID, POLYGLOT_GROUP_ID, PYTHON_ARTIFACT_ID)));
    }

    private static boolean isPythonArtifact(Artifact a) {
        return (POLYGLOT_GROUP_ID.equals(a.getGroupId()) || GRAALPY_GROUP_ID.equals(a.getGroupId())) &&
               (PYTHON_COMMUNITY_ARTIFACT_ID.equals(a.getArtifactId()) || PYTHON_ARTIFACT_ID.equals(a.getArtifactId()));
    }

    private static Collection<Artifact> resolveProjectDependencies(MavenProject project) {
        return project.getArtifacts()
                .stream()
                .filter(a -> !"test".equals(a.getScope()))
                .collect(Collectors.toList());
    }

    private Set<String> calculateLauncherClasspath(MavenProject project) throws IOException {
        if(launcherClassPath == null) {
            String version = getGraalPyVersion(project);
            launcherClassPath = new HashSet<String>();

            // 1.) python-launcher and transitive dependencies
            // get the artifact from its direct dependency in graalpy-maven-plugin
            getLog().debug("calculateLauncherClasspath based on " + GRAALPY_GROUP_ID + ":" + GRAALPY_MAVEN_PLUGIN_ARTIFACT_ID + ":" + version);
            DefaultArtifact mvnPlugin = new DefaultArtifact(GRAALPY_GROUP_ID, GRAALPY_MAVEN_PLUGIN_ARTIFACT_ID, version, "compile", "jar", null, new DefaultArtifactHandler("pom"));
            ProjectBuildingResult result = buildProjectFromArtifact(mvnPlugin);
            Artifact graalPyLauncherArtifact = result.getProject().getArtifacts().stream().filter(a -> GRAALPY_GROUP_ID.equals(a.getGroupId()) && PYTHON_LAUNCHER_ARTIFACT_ID.equals(a.getArtifactId()))
                    .findFirst()
                    .orElse(null);
            // python-launcher artifact
            launcherClassPath.add(graalPyLauncherArtifact.getFile().getAbsolutePath());
            // and transitively all its dependencies
            launcherClassPath.addAll(resolveDependencies(graalPyLauncherArtifact));

            // 2.) graalpy dependencies
            Artifact graalPyArtifact = getGraalPyArtifact(project);
            assert graalPyArtifact != null;
            launcherClassPath.addAll(resolveDependencies(graalPyArtifact));
        }
        return launcherClassPath;
    }

    private Set<String> resolveDependencies(Artifact artifact) throws IOException {
        Set<String> dependencies = new HashSet<>();
        ProjectBuildingResult result = buildProjectFromArtifact(artifact);
        for(Dependency d : result.getDependencyResolutionResult().getResolvedDependencies()) {
            addDependency(d, dependencies);
        }
        return dependencies;
    }

    private ProjectBuildingResult buildProjectFromArtifact(Artifact artifact) throws IOException{
        try{
            ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setProject(null);
            buildingRequest.setResolveDependencies(true);
            buildingRequest.setPluginArtifactRepositories(project.getPluginArtifactRepositories());
            buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());

            return projectBuilder.build(artifact, buildingRequest);
        } catch (ProjectBuildingException e) {
            throw new IOException("Error while building project", e);
        }
    }

    private void addDependency(Dependency d, Set<String> dependencies) {
        File f = d.getArtifact().getFile();
        if(f != null) {
            dependencies.add(f.getAbsolutePath());
        } else {
            getLog().warn("could not retrieve local file for artifact " + d.getArtifact());
        }
    }
}



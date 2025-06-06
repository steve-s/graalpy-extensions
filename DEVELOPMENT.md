## How to develop against the latest GraalPy, GraalVM SDK, and Truffle:

Checkout out and build the [GraalPy](https://github.com/oracle/graalpython)
following instructions in its documentation. We need to build all the necessary
dependencies:

```
mx --dy /tools,/sdk,/truffle build
```


Run the `mx maven-deploy` (full command below) from the `graal/vm` directory.
Make sure to replace the version with what is the current development version
and keep the `-SNAPSHOT` suffix for better development experience.

Note: `-SNAPSHOT` versions are always re-checked and the newest snapshot is
always pulled from the repository by Maven unlike regular version, which are
assumed to be "immutable" and can be cached with no way to invalidate the cache
other than manually removing the artifacts from `~/.m2/repository/...`.

```
mx --dy /tools,/truffle,/graalpython maven-deploy --all-suites \
            --version-string 25.0.0-SNAPSHOT \
            --validate none \
            --supress-javadoc # faster, but you won't see JavaDoc of GraalVM SDK methods
```

Since we did not specify repository, it will install the Maven artifacts into
your local repository (e.g., `~/.m2/repository`) and Maven projects can just
use the version `25.0.0-SNAPSHOT` without any extra configuration. Installation
to the local repository also omits the license checks.

## Maven workflow

**Changing version**:

For Maven projects:

```
mvn versions:set -DnewVersion=26.0.0-SNAPSHOT -DgenerateBackupPoms=false
```

For Gradle projects: change the version manually in `build.gradle`. There is
only one Gradle project in the repo at the time of the writing.

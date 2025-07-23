#!/bin/bash
if [[ $# -eq 0 || "$1" == "--help" || "$1" == "help" ]]; then
  echo "Usage: $0 <directory-where-to-download-the-bundle>"
  echo "Downloads and unpacks the latest Maven bundle according to the revision in top level pom.xml"
  echo "Generates settings.xml in the project root for the downloaded bundle (overriding any existing settings.xml)"
  echo "After that Maven can be run with the settings.xml using: mvn -s settings.xml ..."
  exit 0
fi

source="${BASH_SOURCE[0]}"
while [ -h "$source" ] ; do
    prev_source="$source"
    source="$(readlink "$source")";
    if [[ "$source" != /* ]]; then
        # if the link was relative, it was relative to where it came from
        dir="$( cd -P "$( dirname "$prev_source" )" && pwd )"
        source="$dir/$source"
    fi
done
project_root="$( cd -P "$( dirname "$source" )/.." && pwd )"
bundle_dest="$(realpath "$1")"

revision="$(mvn -f "$project_root/pom.xml" help:evaluate -Dexpression=revision -q -DforceStdout)"
revision="${revision%-SNAPSHOT}" # remove -SNAPSHOT
revision_quoted_for_jq="${revision//./\\\\.}"

echo "Trying to find the release for revision: ${revision}"
asset_url=$(curl -sSL "https://api.github.com/repos/graalvm/oracle-graalvm-ea-builds/releases" \
    | jq -r 'map(select(.tag_name | test("'${revision_quoted_for_jq}'"))) | max_by(.published_at) | .assets[] | select(.name | test("^maven-resource-.*\\.zip$")) | .browser_download_url')
if [[ -z "$asset_url" ]]; then
  echo "Failed to find a maven-resource-bundle zip" >&2
  exit 1
fi
echo "Downloading: $asset_url"
curl -L -o maven-resource-bundle.zip "$asset_url"
unzip -q -o maven-resource-bundle.zip -d "$bundle_dest"
rm maven-resource-bundle.zip
cat > "${project_root}/settings.xml" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <profiles>
    <profile>
      <id>local-repo-profile</id>
      <pluginRepositories>
        <pluginRepository>
          <id>local-repo</id>
          <name>local-repo</name>
          <url>file://$bundle_dest</url>
        </pluginRepository>
      </pluginRepositories>
      <repositories>
        <repository>
          <id>local-repo</id>
          <name>local-repo</name>
          <url>file://$bundle_dest</url>
          <releases>
            <enabled>true</enabled>
          </releases>
          <snapshots>
            <enabled>true</enabled>
          </snapshots>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>local-repo-profile</activeProfile>
  </activeProfiles>
</settings>
EOF
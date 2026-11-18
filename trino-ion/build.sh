#!/usr/bin/env bash
# Builds 484/trino-hive-484.jar and 484/io.trino_trino-hive-formats-484.jar: Trino 484's hive
# plugin jars with patches/ion-port-484.patch compiled in. See README.md.
#
# usage: ./build.sh [WORK_DIR]
#
# WORK_DIR (default: ./build, ignored by git) holds the downloads, about 1 GB, which later runs
# reuse. Needs bash, curl, tar, git and python3; runs on Linux and macOS, x86_64 and arm64.
# Every input is pinned, so a rebuild reproduces the committed jars: `git status` stays clean.
set -euo pipefail
export LC_ALL=C

TRINO_VERSION=484
TRINO_COMMIT=e2d50bb733e193607c44be84ad80ce2430ba1d01 # tag 484
TRINO_TARBALL_URL=https://github.com/trinodb/trino/releases/download/$TRINO_VERSION/trino-server-$TRINO_VERSION.tar.gz
TRINO_TARBALL_SHA256=4b2d05f2e52fcc156b2f137cabc886353e5dde782d12bf71e290ffc13463b81a
# the Trino sources the patch edits, with their sha256 at TRINO_COMMIT
TRINO_SOURCES="
lib/trino-hive-formats/src/main/java/io/trino/hive/formats/HiveClassNames.java a3d31aaae0f4104437a1b8e47e75b00b4721c373cd370aa3bd242c487eda4163
plugin/trino-hive/src/main/java/io/trino/plugin/hive/HiveFormatsModule.java 63bf4abc39a1e794e96ed11d371dcf3c89231e1a21fedb9a9f65c0fc75e2396a
plugin/trino-hive/src/main/java/io/trino/plugin/hive/HiveStorageFormat.java 7c72a849c80656e9c2411fc88701d8a0f2485e55de63910a2d8653374cf96ff1
plugin/trino-hive/src/main/java/io/trino/plugin/hive/HiveWriterFactory.java 60a5fbbbccf78cad737fa8bd6cd8fb21f6934ad66e588b012d812760d6482332
"

MAVEN=https://repo1.maven.org/maven2
ION_JAVA_URL=$MAVEN/com/amazon/ion/ion-java/1.11.9/ion-java-1.11.9.jar
ION_JAVA_SHA256=7f7e4914ac79d578969d75c040e3b3adce215b32281bc65d9d6aca411553b5dd
ION_PATH_URL=$MAVEN/com/amazon/ion/ion-java-path-extraction/1.5.0/ion-java-path-extraction-1.5.0.jar
ION_PATH_SHA256=eb1fe904b34c666eac657f142278ca2824fc8e6b4711e5dda1f3f8545bec593d

# Trino 484 runs on Java 25; javac comes from a pinned Temurin build
JDK_RELEASE=25
JDK_VERSION=25.0.5+8
case "$(uname -s)-$(uname -m)" in
  Linux-x86_64) JDK_PLATFORM=x64_linux JDK_SHA256=68982529869257d0ee6b824ec5aa270ff8b118bb438670da0b97ebdab178d7fb ;;
  Linux-aarch64 | Linux-arm64) JDK_PLATFORM=aarch64_linux JDK_SHA256=bb5b2ded252f21c6090cf780b0a408ad0d83bbb557e702955643557f13231880 ;;
  Darwin-x86_64) JDK_PLATFORM=x64_mac JDK_SHA256=32cd4d2151c91aa00d0fc911edfd05245ed40fa6b1aa894d5f35c928cd67a59d ;;
  Darwin-arm64) JDK_PLATFORM=aarch64_mac JDK_SHA256=36dc14d797aaff6d89aab302cd053fdba6207dfdcf772052fa7b023a94b0e857 ;;
  *) echo "unsupported platform: $(uname -s)-$(uname -m)" >&2 && exit 1 ;;
esac
JDK_URL=https://github.com/adoptium/temurin$JDK_RELEASE-binaries/releases/download/jdk-${JDK_VERSION/+/%2B}/OpenJDK${JDK_RELEASE}U-jdk_${JDK_PLATFORM}_hotspot_${JDK_VERSION/+/_}.tar.gz

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mkdir -p "${1:-$HERE/build}" && cd "${1:-$HERE/build}" && pwd)"
DOWNLOADS="$WORK/downloads"
OUT="$HERE/$TRINO_VERSION"

sha256() {
  if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1
}

download() { # URL SHA256 FILE: skipped when FILE already has SHA256
  if [ -f "$3" ] && [ "$(sha256 "$3")" = "$2" ]; then return; fi
  echo "   $1"
  mkdir -p "$(dirname "$3")"
  curl -fsSL --retry 3 -o "$3.part" "$1"
  local actual
  actual="$(sha256 "$3.part")"
  if [ "$actual" != "$2" ]; then
    echo "sha256 mismatch for $1: expected $2, got $actual" >&2 && exit 1
  fi
  mv "$3.part" "$3"
}

echo "== downloading"
download "$TRINO_TARBALL_URL" "$TRINO_TARBALL_SHA256" "$DOWNLOADS/trino-server-$TRINO_VERSION.tar.gz"
download "$ION_JAVA_URL" "$ION_JAVA_SHA256" "$DOWNLOADS/$(basename "$ION_JAVA_URL")"
download "$ION_PATH_URL" "$ION_PATH_SHA256" "$DOWNLOADS/$(basename "$ION_PATH_URL")"
download "$JDK_URL" "$JDK_SHA256" "$DOWNLOADS/$(basename "$JDK_URL")"
while read -r path sum; do
  [ -z "$path" ] || download "https://raw.githubusercontent.com/trinodb/trino/$TRINO_COMMIT/$path" "$sum" "$DOWNLOADS/src/$path"
done <<<"$TRINO_SOURCES"

echo "== extracting Trino $TRINO_VERSION and JDK $JDK_VERSION"
# the whole server, because most jars in plugin/hive are hard links to files in other directories
TRINO_HOME="$WORK/trino-server-$TRINO_VERSION"
JAVA_HOME="$WORK/jdk-$JDK_VERSION"
rm -rf "$TRINO_HOME" "$JAVA_HOME"
tar -xzf "$DOWNLOADS/trino-server-$TRINO_VERSION.tar.gz" -C "$WORK"
tar -xzf "$DOWNLOADS/$(basename "$JDK_URL")" -C "$WORK"
if [ -d "$JAVA_HOME/Contents/Home" ]; then JAVA_HOME="$JAVA_HOME/Contents/Home"; fi # macOS
PLUGIN="$TRINO_HOME/plugin/hive"
FORMATS_JAR="$PLUGIN/io.trino_trino-hive-formats-$TRINO_VERSION.jar"
HIVE_JAR="$PLUGIN/trino-hive-$TRINO_VERSION.jar"

echo "== applying patches/ion-port-$TRINO_VERSION.patch"
rm -rf "$WORK/src"
mkdir -p "$WORK/src"
cp -R "$DOWNLOADS/src/." "$WORK/src/"
git init -q "$WORK/src" # its own repository, so that `git apply` never resolves paths against an enclosing one
git -C "$WORK/src" apply "$HERE/patches/ion-port-$TRINO_VERSION.patch"

echo "== compiling"
# the plugin's jars before the server's, as the plugin class loader sees them; in a fixed order
CP="$DOWNLOADS/$(basename "$ION_JAVA_URL"):$DOWNLOADS/$(basename "$ION_PATH_URL")"
for jar in "$PLUGIN"/*.jar "$TRINO_HOME"/lib/*.jar; do CP="$CP:$jar"; done
JAVAC=("$JAVA_HOME/bin/javac" --release "$JDK_RELEASE" -g -parameters -proc:none -implicit:none -encoding UTF-8 -sourcepath "")
rm -rf "$WORK/classes"
mkdir -p "$WORK/classes/formats" "$WORK/classes/hive"
FORMATS_SRC="$WORK/src/lib/trino-hive-formats/src/main/java/io/trino/hive/formats"
HIVE_SRC="$WORK/src/plugin/trino-hive/src/main/java/io/trino/plugin/hive"
"${JAVAC[@]}" -cp "$CP" -d "$WORK/classes/formats" \
  "$FORMATS_SRC/HiveClassNames.java" "$FORMATS_SRC"/ion/*.java
"${JAVAC[@]}" -cp "$WORK/classes/formats:$CP" -d "$WORK/classes/hive" \
  "$HIVE_SRC/HiveFormatsModule.java" "$HIVE_SRC/HiveStorageFormat.java" "$HIVE_SRC/HiveWriterFactory.java" "$HIVE_SRC"/ion/*.java

echo "== repacking"
mkdir -p "$OUT"
python3 -I "$HERE/repack.py" "$FORMATS_JAR" "$WORK/classes/formats" "$OUT/$(basename "$FORMATS_JAR")"
python3 -I "$HERE/repack.py" "$HIVE_JAR" "$WORK/classes/hive" "$OUT/$(basename "$HIVE_JAR")"
for jar in "$OUT"/*.jar; do echo "$(sha256 "$jar")  ${jar#"$HERE"/}"; done

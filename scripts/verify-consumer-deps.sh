#!/usr/bin/env bash
# Confirm Maven consumer scopes and a runtime free of build-only dependencies.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d -t hercules-consumer-XXXXXXXX)"
trap 'rm -rf "$TMP"' EXIT

VERSION="$(python3 - "$ROOT/pom.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
ns = "{http://maven.apache.org/POM/4.0.0}"
print(root.findtext(ns + "version"))
PY
)"

# Deliberately install only into the developer/CI local Maven repository.
"$ROOT/mvnw" -B -ntp -q -f "$ROOT/pom.xml" -Dmaven.test.skip=true install

mkdir -p "$TMP/src/main/java/consumer"
cat > "$TMP/pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>consumer</groupId>
  <artifactId>hercules-consumer-smoke</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
  <dependencies>
    <dependency>
      <groupId>io.github.blackbeltbadger</groupId>
      <artifactId>hercules</artifactId>
      <version>$VERSION</version>
    </dependency>
  </dependencies>
</project>
EOF

cat > "$TMP/src/main/java/consumer/Smoke.java" <<'EOF'
package consumer;

import bbb.hercules.Either;

public class Smoke {
    public static void main(String[] args) {
        Either<String, Integer> value = Either.right(2);
        int result = value.map(x -> x + 1).fold(error -> -1, x -> x);
        if (result != 3) {
            throw new AssertionError("Unexpected public API behavior: " + result);
        }
    }
}
EOF

"$ROOT/mvnw" -B -ntp -q -f "$TMP/pom.xml" compile
"$ROOT/mvnw" -B -ntp -q -f "$TMP/pom.xml" dependency:tree \
    -DoutputFile="$TMP/dependency-tree.txt"

grep -Eq 'com\.google\.guava:guava:jar:[^:]+:compile' "$TMP/dependency-tree.txt" || {
    echo "Missing intended transitive Guava compile dependency" >&2; exit 1;
}
grep -Eq 'org\.jspecify:jspecify:jar:[^:]+:compile' "$TMP/dependency-tree.txt" || {
    echo "Missing JSpecify contract dependency" >&2; exit 1;
}
if grep -Eq '(org\.projectlombok:lombok|org\.junit|org\.instancio):' "$TMP/dependency-tree.txt"; then
    echo "Build-only/test dependency leaked to consumers:" >&2
    cat "$TMP/dependency-tree.txt" >&2
    exit 1
fi
echo "PASS: consumer transitive scopes (Guava/JSpecify, no Lombok/JUnit/Instancio)"

LIBRARY="$ROOT/target/hercules-$VERSION.jar"
if jar tf "$LIBRARY" | grep -Eq '^(com/google/|lombok/|org/jspecify/)'; then
    echo "Third-party classes have been bundled into the Hercules JAR" >&2
    exit 1
fi
echo "PASS: Hercules JAR does not embed third-party classes"

# This example does not require Guava/JSpecify/Lombok at runtime. Those
# dependencies remain available to Maven consumers where needed.
java -cp "$TMP/target/classes:$LIBRARY" consumer.Smoke
echo "PASS: separate Maven consumer compiles and executes without Lombok"

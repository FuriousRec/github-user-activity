#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
build_dir=$(mktemp -d)
trap 'rm -rf "$build_dir"' EXIT HUP INT TERM
javac -d "$build_dir" src/main/java/GithubRequests.java src/test/java/GithubRequestsChecks.java
java -cp "$build_dir" GithubRequestsChecks

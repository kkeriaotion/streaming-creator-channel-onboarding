#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/creator-onboarding-java-build"
mkdir -p "$BUILD_DIR"
find src/main/java src/test/java -name '*.java' -print | sort > "$BUILD_DIR/sources.txt"
javac -d "$BUILD_DIR" @"$BUILD_DIR/sources.txt"
java -cp "$BUILD_DIR" learning.streaming.service.CreatorWelcomeServiceTest

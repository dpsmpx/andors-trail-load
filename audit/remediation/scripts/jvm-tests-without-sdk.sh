#!/usr/bin/env bash
# Compiles the app sources that do not depend on AndroidX and runs the JVM unit tests,
# without the Android SDK or the Android Gradle Plugin.
#
# This is a fallback for environments that cannot reach Google's Maven repository or
# dl.google.com. The authoritative build is `./gradlew testDebugUnitTest` in CI.
#
# Differences from the Gradle unit test run:
# - The Android framework comes from Robolectric's android-all jar (Maven Central) instead
#   of the SDK's android.jar stubs. Tests must not call Android framework methods either way.
# - R and BuildConfig are generated stubs whose values are not the real resource ids.
# - UI sources (activity/, view/, Dialogs) and sources using AndroidX fragments or activities are
#   only read for their signatures and never compiled, so tests cannot load those classes. The
#   other AndroidX classes are stubs that do nothing.
# - Java 21 is required: the app calls Math.clamp, which Android provides from API 35 on.
#
# Usage: audit/remediation/scripts/jvm-tests-without-sdk.sh [work-dir]
set -euo pipefail

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
APP="$REPO/AndorsTrail/app"
WORK="${1:-${TMPDIR:-/tmp}/andors-trail-jvm-tests}"
MAVEN="https://repo1.maven.org/maven2"
ANDROID_ALL="org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar"
JUNIT="junit/junit/4.13.2/junit-4.13.2.jar"
HAMCREST="org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"

mkdir -p "$WORK/lib" "$WORK/stubs" "$WORK/classes" "$WORK/test-classes"
for jar in "$ANDROID_ALL" "$JUNIT" "$HAMCREST"; do
	target="$WORK/lib/$(basename "$jar")"
	[ -s "$target" ] || curl -sSfL -o "$target" "$MAVEN/$jar"
done
CP="$WORK/lib/$(basename "$ANDROID_ALL"):$WORK/lib/$(basename "$JUNIT"):$WORK/lib/$(basename "$HAMCREST")"

# R and BuildConfig stubs, generated from the references in the sources.
PKG_DIR="$WORK/stubs/com/gpl/rpg/AndorsTrail"
mkdir -p "$PKG_DIR"
python3 - "$APP/src/main/java" "$PKG_DIR" "$APP/build.gradle" "$APP/src/main/AndroidManifest.xml" <<'EOF'
import collections, os, re, sys
src, out, gradle, manifest = sys.argv[1:5]
refs = collections.defaultdict(set)
for root, _, files in os.walk(src):
    for f in files:
        if f.endswith('.java'):
            text = open(os.path.join(root, f), encoding='utf-8').read()
            for kind, name in re.findall(r'(?<![\w.])R\.(\w+)\.(\w+)', text):
                refs[kind].add(name)
with open(os.path.join(out, 'R.java'), 'w') as fh:
    fh.write('package com.gpl.rpg.AndorsTrail;\npublic final class R {\n')
    n = 0x7f000000
    for kind in sorted(refs):
        fh.write(f'  public static final class {kind} {{\n')
        for name in sorted(refs[kind]):
            n += 1
            fh.write(f'    public static final int {name} = {n};\n')
        fh.write('  }\n')
    fh.write('}\n')
text = open(gradle, encoding='utf-8').read() + open(manifest, encoding='utf-8').read()
code = re.search(r'versionCode\W+(\d+)', text).group(1)
name = re.search(r'versionName\W+"?([\w.]+)', text).group(1)
with open(os.path.join(out, 'BuildConfig.java'), 'w') as fh:
    fh.write('package com.gpl.rpg.AndorsTrail;\npublic final class BuildConfig {\n'
             '  public static final boolean DEBUG = true;\n'
             '  public static final String APPLICATION_ID = "com.gpl.rpg.AndorsTrail.dev";\n'
             '  public static final String BUILD_TYPE = "debug";\n'
             f'  public static final int VERSION_CODE = {code};\n'
             f'  public static final String VERSION_NAME = "{name}dev";\n}}\n')
EOF

# Minimal AndroidX stubs: only the types that appear in signatures of classes the tests load.
mkdir -p "$WORK/stubs/androidx/annotation" "$WORK/stubs/androidx/documentfile/provider" "$WORK/stubs/androidx/core/content" "$WORK/stubs/androidx/fragment/app" "$WORK/stubs/androidx/activity"
for a in NonNull Nullable RequiresApi IdRes LayoutRes StringRes DrawableRes ColorInt; do
	echo "package androidx.annotation; public @interface $a { int value() default 0; int api() default 0; }" > "$WORK/stubs/androidx/annotation/$a.java"
done
echo "package androidx.documentfile.provider; public abstract class DocumentFile { public static DocumentFile fromFile(java.io.File f) { return null; } public abstract DocumentFile createFile(String mimeType, String name); public abstract DocumentFile findFile(String name); public abstract DocumentFile[] listFiles(); public abstract android.net.Uri getUri(); public abstract String getName(); public abstract boolean isDirectory(); public abstract boolean isFile(); public abstract boolean exists(); public abstract boolean delete(); }" > "$WORK/stubs/androidx/documentfile/provider/DocumentFile.java"
echo "package androidx.core.content; public class FileProvider { public static android.net.Uri getUriForFile(android.content.Context c, String a, java.io.File f) { return null; } }" > "$WORK/stubs/androidx/core/content/FileProvider.java"
echo "package androidx.fragment.app; public class Fragment {}" > "$WORK/stubs/androidx/fragment/app/Fragment.java"
echo "package androidx.fragment.app; public class FragmentActivity extends android.app.Activity { public androidx.activity.OnBackPressedDispatcher getOnBackPressedDispatcher() { return null; } public FragmentManager getSupportFragmentManager() { return null; } }" > "$WORK/stubs/androidx/fragment/app/FragmentActivity.java"
echo "package androidx.fragment.app; public class FragmentManager { public interface OnBackStackChangedListener { void onBackStackChanged(); } }" > "$WORK/stubs/androidx/fragment/app/FragmentManager.java"
echo "package androidx.fragment.app; public class FragmentTabHost extends android.widget.TabHost { public FragmentTabHost(android.content.Context c) { super(c); } }" > "$WORK/stubs/androidx/fragment/app/FragmentTabHost.java"
echo "package androidx.activity; public abstract class OnBackPressedCallback { public OnBackPressedCallback(boolean b) {} public abstract void handleOnBackPressed(); }" > "$WORK/stubs/androidx/activity/OnBackPressedCallback.java"
echo "package androidx.activity; public class OnBackPressedDispatcher { public void onBackPressed() {} public void addCallback(OnBackPressedCallback c) {} public void addCallback(Object o, OnBackPressedCallback c) {} }" > "$WORK/stubs/androidx/activity/OnBackPressedDispatcher.java"

# android.jar stubs throw RuntimeException("Stub!"), while android-all's Log is native. Mimic the stub,
# because the code relies on catching that RuntimeException (util/L.java).
mkdir -p "$WORK/stubs/android/util"
echo 'package android.util; public final class Log { static int stub() { throw new RuntimeException("Stub!"); } public static int d(String t, String m) { return stub(); } public static int i(String t, String m) { return stub(); } public static int w(String t, String m) { return stub(); } public static int w(String t, String m, Throwable e) { return stub(); } public static int e(String t, String m) { return stub(); } public static int e(String t, String m, Throwable e) { return stub(); } public static int println(int p, String t, String m) { return stub(); } public static String getStackTraceString(Throwable e) { stub(); return null; } public static final int DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6; }' > "$WORK/stubs/android/util/Log.java"
mkdir -p "$WORK/framework-overrides"
javac --release 21 -nowarn -d "$WORK/framework-overrides" "$WORK/stubs/android/util/Log.java"
CP="$WORK/framework-overrides:$CP"

# Compile every main source that does not import AndroidX; the rest is only used for signatures.
MAIN_SOURCES="$WORK/main-sources.txt"
(cd "$APP/src/main/java" && grep -rLE "import androidx\.(fragment|activity)" --include=*.java . | grep -v -e "/activity/" -e "/view/" -e "/Dialogs.java" | sed "s|^\.|$APP/src/main/java|") > "$MAIN_SOURCES"
rm -rf "$WORK/classes" "$WORK/test-classes" && mkdir -p "$WORK/classes" "$WORK/test-classes"
javac --release 21 -proc:none -implicit:none -nowarn -encoding UTF-8 -d "$WORK/classes" -cp "$CP" \
	-sourcepath "$APP/src/main/java:$WORK/stubs" "@$MAIN_SOURCES" "$PKG_DIR/R.java" "$PKG_DIR/BuildConfig.java" 2>&1 | grep -v "^Note:" || true
[ -f "$WORK/classes/com/gpl/rpg/AndorsTrail/controller/Constants.class" ] || { echo "main compilation failed" >&2; exit 1; }

find "$APP/src/test/java" -name "*.java" > "$WORK/test-sources.txt"
javac --release 21 -proc:none -nowarn -encoding UTF-8 -d "$WORK/test-classes" -cp "$WORK/classes:$CP" "@$WORK/test-sources.txt"

# At run time only: android-all's Build reads system properties natively. In android.jar
# Build.VERSION.SDK_INT is 0, which is what the unit tests see in Gradle.
mkdir -p "$WORK/stubs-runtime/android/os" "$WORK/runtime-overrides"
echo 'package android.os; public class Build { public static class VERSION { public static final int SDK_INT = Integer.parseInt("0"); } }' > "$WORK/stubs-runtime/android/os/Build.java"
javac --release 21 -nowarn -d "$WORK/runtime-overrides" "$WORK/stubs-runtime/android/os/Build.java"

TESTS=$(cd "$APP/src/test/java" && find . -name "*Test.java" | sed 's|^\./||; s|\.java$||; s|/|.|g' | sort)
cd "$APP"   # Gradle runs unit tests in the module directory.
java -cp "$WORK/runtime-overrides:$WORK/test-classes:$WORK/classes:$CP" org.junit.runner.JUnitCore $TESTS

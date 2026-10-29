const assert = require("node:assert/strict");
const {readFileSync, mkdtempSync, mkdirSync, writeFileSync, rmSync} = require("node:fs");
const {join, dirname} = require("node:path");
const {tmpdir} = require("node:os");
const {execFileSync} = require("node:child_process");
const {test} = require("node:test");

const javaRoot = join(__dirname, "../app/src/main/java/org/b3log/siyuan");
// These doubles exercise our listeners and state transitions on a JVM, without claiming
// to emulate Android rendering. Gradle compilation checks the actual platform API surface.
const doubles = {
    "android/R.java": `package android; public class R { public static class id { public static final int content=1; } }`,
    "android/os/Build.java": `package android.os; public class Build {
        public static class VERSION { public static int SDK_INT=26; }
        public static class VERSION_CODES { public static final int P=28, Q=29; }
    }`,
    "android/graphics/Color.java": `package android.graphics; public class Color { public static final int TRANSPARENT=0; }`,
    "android/view/View.java": `package android.view;
        import androidx.core.view.*;
        public class View {
            public OnApplyWindowInsetsListener listener;
            public WindowInsetsCompat insets;
            private int l,t,r,b;
            public void setPadding(int l,int t,int r,int b) { this.l=l; this.t=t; this.r=r; this.b=b; }
            public int getPaddingLeft(){return l;} public int getPaddingTop(){return t;}
            public int getPaddingRight(){return r;} public int getPaddingBottom(){return b;}
        }`,
    "android/view/ViewGroup.java": `package android.view; public class ViewGroup extends View {
        public View child=new View(); public View getChildAt(int index){return child;}
    }`,
    "android/view/WindowManager.java": `package android.view; public class WindowManager {
        public static class LayoutParams {
            public static final int FLAG_TRANSLUCENT_STATUS=1, FLAG_TRANSLUCENT_NAVIGATION=2,
                FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS=4, LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES=1;
            public int layoutInDisplayCutoutMode;
        }
    }`,
    "android/view/Window.java": `package android.view;
        import androidx.core.view.WindowInsetsControllerCompat;
        public class Window {
            public final View decor=new View();
            public final WindowInsetsControllerCompat controller=new WindowInsetsControllerCompat();
            public boolean fitsSystemWindows=true, navigationContrast=true, statusContrast=true;
            public int navigationColor=-1, statusColor=-1;
            private WindowManager.LayoutParams attributes=new WindowManager.LayoutParams();
            public View getDecorView(){return decor;}
            public void clearFlags(int flags){} public void addFlags(int flags){}
            public void setStatusBarColor(int color){statusColor=color;}
            public void setNavigationBarColor(int color){navigationColor=color;}
            public void setNavigationBarDividerColor(int color){}
            public WindowManager.LayoutParams getAttributes(){return attributes;}
            public void setAttributes(WindowManager.LayoutParams attributes){this.attributes=attributes;}
            public void setStatusBarContrastEnforced(boolean value){statusContrast=value;}
            public void setNavigationBarContrastEnforced(boolean value){navigationContrast=value;}
        }`,
    "android/app/Activity.java": `package android.app; import android.view.*;
        public class Activity {
            public final Window window=new Window(); public final ViewGroup content=new ViewGroup();
            public Window getWindow(){return window;}
            public <T extends View> T findViewById(int id){return (T)content;}
        }`,
    "androidx/core/graphics/Insets.java": `package androidx.core.graphics;
        public class Insets {
            public final int left,top,right,bottom;
            public static final Insets NONE=of(0,0,0,0);
            private Insets(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
            public static Insets of(int l,int t,int r,int b){return new Insets(l,t,r,b);}
        }`,
    "androidx/core/view/OnApplyWindowInsetsListener.java": `package androidx.core.view;
        public interface OnApplyWindowInsetsListener {
            WindowInsetsCompat onApplyWindowInsets(android.view.View view, WindowInsetsCompat insets);
        }`,
    "androidx/core/view/ViewCompat.java": `package androidx.core.view; import android.view.View;
        public class ViewCompat {
            public static int requests;
            public static void setOnApplyWindowInsetsListener(View view, OnApplyWindowInsetsListener listener){view.listener=listener;}
            public static void requestApplyInsets(View view){requests++;}
            public static WindowInsetsCompat getRootWindowInsets(View view){return view.insets;}
        }`,
    "androidx/core/view/WindowCompat.java": `package androidx.core.view; import android.view.*;
        public class WindowCompat {
            public static void setDecorFitsSystemWindows(Window window, boolean fits){window.fitsSystemWindows=fits;}
            public static WindowInsetsControllerCompat getInsetsController(Window window, View view){return window.controller;}
        }`,
    "androidx/core/view/WindowInsetsControllerCompat.java": `package androidx.core.view;
        public class WindowInsetsControllerCompat {
            public static final int BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE=2;
            public boolean lightStatus,lightNavigation; public int visible=3,behavior;
            public void show(int types){visible|=types;} public void hide(int types){visible&=~types;}
            public void setAppearanceLightStatusBars(boolean light){lightStatus=light;}
            public void setAppearanceLightNavigationBars(boolean light){lightNavigation=light;}
            public int getSystemBarsBehavior(){return behavior;}
            public void setSystemBarsBehavior(int behavior){this.behavior=behavior;}
        }`,
    "androidx/core/view/WindowInsetsCompat.java": `package androidx.core.view;
        import androidx.core.graphics.Insets; import java.util.*;
        public class WindowInsetsCompat {
            public static final WindowInsetsCompat CONSUMED=new WindowInsetsCompat();
            public int visible=3;
            private final Map<Integer,Insets> values=new HashMap<>();
            public WindowInsetsCompat with(int type,Insets insets){values.put(type,insets);return this;}
            public Insets getInsets(int types){
                int l=0,t=0,r=0,b=0;
                for(var item:values.entrySet()) if((item.getKey()&types)!=0){
                    Insets i=item.getValue(); l=Math.max(l,i.left);t=Math.max(t,i.top);r=Math.max(r,i.right);b=Math.max(b,i.bottom);
                }
                return Insets.of(l,t,r,b);
            }
            public boolean isVisible(int types){return (visible&types)==types;}
            public static class Type {
                public static int statusBars(){return 1;} public static int navigationBars(){return 2;}
                public static int systemBars(){return 19;} public static int displayCutout(){return 4;}
                public static int captionBar(){return 16;}
                public static int ime(){return 8;}
            }
        }`,
};

test("system bars preserve safe content, theme contrast and fullscreen restoration", () => {
    const temporary = mkdtempSync(join(tmpdir(), "siyuan-system-bars-"));
    try {
        const files = Object.entries(doubles).map(([name, source]) => {
            const file = join(temporary, name);
            mkdirSync(dirname(file), {recursive: true});
            writeFileSync(file, source, "utf8");
            return file;
        });
        files.push(join(javaRoot, "SystemBars.java"), join(__dirname, "fixtures/SystemBarsTest.java"));
        execFileSync("javac", ["-encoding", "UTF-8", "-d", temporary, ...files], {windowsHide: true});
        execFileSync("java", ["-cp", temporary, "org.b3log.siyuan.SystemBarsTest"], {windowsHide: true});
    } finally {
        assert.equal(dirname(temporary), tmpdir());
        rmSync(temporary, {recursive: true, force: true});
    }
});

test("all Android entry points use native safe areas without hiding navigation", () => {
    for (const name of ["MainActivity", "BootActivity", "ShortcutActivity"]) {
        const source = readFileSync(join(javaRoot, `${name}.java`), "utf8");
        assert.match(source, /SystemBars\.configure\(this,/);
        assert.doesNotMatch(source, /setNavBarVisibility|getStatusBarHeight|UltimateBarX/);
        if (name !== "MainActivity") assert.match(source, /SystemBars\.fitContent\(this\)/);
    }
    const bridge = readFileSync(join(javaRoot, "JSAndroid.java"), "utf8");
    const themeChange = bridge.slice(bridge.indexOf("public void changeStatusBarColor"), bridge.indexOf("private int parseColor"));
    assert.match(themeChange, /SystemBars\.setAppearance/);
    assert.doesNotMatch(themeChange, /isTablet|setNavBarVisibility/);
    const workaround = readFileSync(join(javaRoot, "AndroidBug5497Workaround.java"), "utf8");
    assert.match(workaround, /SystemBars\.fitContent\(activity\)/);
    assert.match(workaround, /SystemBars\.applyInsets\(container,.*Insets.NONE, false\)/);
    assert.match(workaround, /return WindowInsets.CONSUMED/);
});

test("keyboard content height, hide animations and focus restoration stay compatible", () => {
    const temporary = mkdtempSync(join(tmpdir(), "siyuan-keyboard-insets-"));
    const names = ["KeyboardContentHeight", "KeyboardHideState", "KeyboardFocusState"];
    try {
        const files = names.flatMap(name => [join(javaRoot, `${name}.java`),
            join(__dirname, `../app/src/test/java/org/b3log/siyuan/${name}Test.java`)]);
        execFileSync("javac", ["-encoding", "UTF-8", "-d", temporary, ...files], {windowsHide: true});
        for (const name of names) {
            execFileSync("java", ["-cp", temporary, `org.b3log.siyuan.${name}Test`], {windowsHide: true});
        }
    } finally {
        assert.equal(dirname(temporary), tmpdir());
        rmSync(temporary, {recursive: true, force: true});
    }
});

package io.github.hexstr.UnityFPSUnlocker;

import android.os.Process;
import java.lang.reflect.Method;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MyModule implements IXposedHookLoadPackage {

    private int delay = 15;
    private int fps = 120;
    private boolean mod_opcode = true;
    private float scale = -1.0f;

    private static XSharedPreferences getPref(String path) {
        XSharedPreferences pref = new XSharedPreferences(BuildConfig.APPLICATION_ID, path);
        return pref.getFile().canRead() ? pref : null;
    }

    private static int getIntPref(XSharedPreferences settings, String key, int fallback) {
        try { return Integer.parseInt(settings.getString(key, String.valueOf(fallback))); } 
        catch (Exception e) { return fallback; }
    }

    public static native void HelloWorld(int delay, int fps, boolean mod_opcode, float scale);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String pkg = lpparam.packageName;
        if (!"com.nhnpa.cps.huawei".equals(pkg)) return;

        XposedBridge.log("UnityFPSUnlocker: #コンパス 超強化アンチブロック");

        // 1. DetectionPopup 全方位ブロック
        try {
            Class<?> detClass = XposedHelpers.findClass("com.siem.ms7.DetectionPopup", lpparam.classLoader);
            for (Method m : detClass.getDeclaredMethods()) {
                String name = m.getName();
                XposedHelpers.findAndHookMethod(detClass, name, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("UnityFPSUnlocker: ★ BLOCKED DetectionPopup." + name + " ★");
                        param.setResult(null);
                    }
                });
            }
            // コンストラクタもブロック
            XposedHelpers.findAndHookConstructor(detClass, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("UnityFPSUnlocker: BLOCKED DetectionPopup constructor");
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("DetectionPopup hook: " + t.getMessage());
        }

        // 2. killProcess / exit 強化
        hookKillProcess();
        hookSystemExit();

        // 3. root検知コマンドブロック
        hookRootChecks();

        // 4. Xposed痕跡隠蔽（簡易）
        hideXposedTraces(lpparam);

        loadPrefsAndNative();
    }

    private void hookKillProcess() {
        try {
            XposedHelpers.findAndHookMethod(Process.class, "killProcess", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if ((int) param.args[0] == Process.myPid()) {
                        XposedBridge.log("UnityFPSUnlocker: ★ SELF KILL BLOCKED ★");
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookSystemExit() {
        try {
            XposedHelpers.findAndHookMethod(System.class, "exit", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("UnityFPSUnlocker: System.exit BLOCKED");
                    param.setResult(null);
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookRootChecks() {
        try {
            XposedHelpers.findAndHookMethod(Runtime.class, "exec", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    String cmd = (String) param.args[0];
                    if (cmd != null && (cmd.contains("su") || cmd.contains("magisk") || cmd.contains("root"))) {
                        XposedBridge.log("UnityFPSUnlocker: Blocked root cmd: " + cmd);
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hideXposedTraces(XC_LoadPackage.LoadPackageParam lpparam) {
        // 簡易的なXposed痕跡隠蔽
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    String className = (String) param.args[0];
                    if (className != null && (className.contains("xposed") || className.contains("lsposed"))) {
                        XposedBridge.log("UnityFPSUnlocker: Blocked Xposed class load: " + className);
                        param.setThrowable(new ClassNotFoundException("blocked"));
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void loadPrefsAndNative() {
        XSharedPreferences settings = getPref("fps_prefs");
        if (settings != null) {
            delay = getIntPref(settings, "delay", 15);
            fps = getIntPref(settings, "fps", 120);
            mod_opcode = settings.getBoolean("mod_opcode", true);
        }
        XposedBridge.log("UnityFPSUnlocker: fps=" + fps + " mod_opcode=" + mod_opcode);

        try {
            System.loadLibrary("UnityFPSUnlocker");
            HelloWorld(delay, fps, mod_opcode, scale);
            XposedBridge.log("Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log("Native load skipped (検知ブロック優先)");
        }
    }
}

package io.github.hexstr.UnityFPSUnlocker;

import android.os.Process;
import java.lang.reflect.Method;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MyModule implements IXposedHookLoadPackage {

    private int delay = 5;
    private int fps = 30;           // 30fpsテスト
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
        if (!"com.nhnpa.cps.huawei".equals(lpparam.packageName)) return;

        XposedBridge.log("UnityFPSUnlocker: #コンパス v7 - 30fps + 完全制圧");

        hookDetectionPopupFull(lpparam);
        hookKillProcess();
        hookSystemExit();
        hookActivityFinish(lpparam);   // ← 修正済み
        hookRootChecks();
        hideXposedTraces(lpparam);

        loadPrefsAndNative();
    }

    private void hookDetectionPopupFull(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> detClass = XposedHelpers.findClass("com.siem.ms7.DetectionPopup", lpparam.classLoader);
            XposedBridge.log("★ DetectionPopup class FOUND - FULL BLOCK v7 ★");

            for (Method m : detClass.getDeclaredMethods()) {
                final String name = m.getName();
                XposedHelpers.findAndHookMethod(detClass, name, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("UnityFPSUnlocker: ★ BLOCKED DetectionPopup." + name + " ★");
                        param.setResult(null);
                    }
                });
            }

            String[] critical = {"Ij11111IlIijjjlil1jliI", "finishApp", "killProcess", "exitApp", "finish", "shutdown"};
            for (String name : critical) {
                try {
                    XposedHelpers.findAndHookMethod(detClass, name, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log("UnityFPSUnlocker: ★ CRITICAL BLOCK " + name + " ★");
                            param.setResult(null);
                        }
                    });
                } catch (Throwable ignored) {}
            }

            XposedHelpers.findAndHookConstructor(detClass, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("UnityFPSUnlocker: ★ BLOCKED DetectionPopup Constructor ★");
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("DetectionPopup hook error: " + t.getMessage());
        }
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
                    XposedBridge.log("UnityFPSUnlocker: ★ System.exit BLOCKED ★");
                    param.setResult(null);
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookActivityFinish(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "finish", 
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("UnityFPSUnlocker: ★ Activity.finish BLOCKED ★");
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
                    if (cmd != null && (cmd.contains("su") || cmd.contains("magisk"))) {
                        XposedBridge.log("UnityFPSUnlocker: Blocked root: " + cmd);
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hideXposedTraces(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    String name = (String) param.args[0];
                    if (name != null && (name.contains("xposed") || name.contains("lsposed") || name.contains("siem.ms7"))) {
                        XposedBridge.log("UnityFPSUnlocker: Blocked trace: " + name);
                        param.setThrowable(new ClassNotFoundException("blocked"));
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void loadPrefsAndNative() {
        XSharedPreferences settings = getPref("fps_prefs");
        if (settings != null) {
            fps = getIntPref(settings, "fps", 30);
            mod_opcode = settings.getBoolean("mod_opcode", true);
        }
        XposedBridge.log("UnityFPSUnlocker: fps=" + fps + " mod_opcode=" + mod_opcode);

        try {
            System.loadLibrary("UnityFPSUnlocker");
            HelloWorld(delay, fps, mod_opcode, scale);
            XposedBridge.log("Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log("Native load failed: " + e.getMessage());
        }
    }
}

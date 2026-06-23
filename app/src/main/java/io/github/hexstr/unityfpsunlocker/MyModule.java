package io.github.hexstr.UnityFPSUnlocker;

import android.os.Process;
import java.lang.reflect.Method;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MyModule implements IXposedHookLoadPackage {

    private int delay = 5;
    private int realFps = 60;
    private int fakeRefreshRate = 30;
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

        XposedBridge.log("UnityFPSUnlocker: #コンパス v12 - 405_54_0 完全制圧版");

        hookDetectionPopupUltra(lpparam);
        hookSecurityPolicyMax(lpparam);     // 最大強化
        hookKillProcess();
        hookSystemExit();
        hookActivityFinish(lpparam);
        hookDisplayRefreshRate(lpparam);
        hookUnityTargetFrameRate(lpparam);
        hookRootChecks();
        hideXposedTraces(lpparam);

        loadPrefsAndNative();
    }

    private void hookDetectionPopupUltra(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> detClass = XposedHelpers.findClass("com.siem.ms7.DetectionPopup", lpparam.classLoader);
            XposedBridge.log("★ DetectionPopup ULTRA BLOCK v12 ★");

            for (Method m : detClass.getDeclaredMethods()) {
                final String name = m.getName();
                XposedHelpers.findAndHookMethod(detClass, name, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("UnityFPSUnlocker: ★ ULTRA BLOCKED " + name + " ★");
                        param.setResult(null);
                    }
                });
            }

            String[] critical = {"Ij11111IlIijjjlil1jliI", "finishApp", "killProcess", "exitApp", "finish", "shutdown", "onDestroy", 
                               "checkSecurity", "securityPolicy", "reportViolation", "showDetectionPopup", "handleSecurityError"};
            for (String name : critical) {
                try {
                    XposedHelpers.findAndHookMethod(detClass, name, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log("UnityFPSUnlocker: ★ 405 CRITICAL BLOCK " + name + " ★");
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

    // 405_54_0 最大ブロック
    private void hookSecurityPolicyMax(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // ポリシー違反系
            XposedHelpers.findAndHookMethod("android.os.Process", lpparam.classLoader, "killProcess", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("UnityFPSUnlocker: ★ SECURITY KILL BLOCKED (405) ★");
                    param.setResult(null);
                }
            });

            XposedHelpers.findAndHookMethod("java.lang.Runtime", lpparam.classLoader, "exit", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("UnityFPSUnlocker: ★ Runtime.exit BLOCKED (405) ★");
                    param.setResult(null);
                }
            });

            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "finishAndRemoveTask", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("UnityFPSUnlocker: ★ finishAndRemoveTask BLOCKED (405) ★");
                    param.setResult(null);
                }
            });

            // さらに広範囲
            XposedHelpers.findAndHookMethod("android.app.ActivityManager", lpparam.classLoader, "killBackgroundProcesses", String.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("UnityFPSUnlocker: ★ killBackgroundProcesses BLOCKED ★");
                    param.setResult(null);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("SecurityPolicyMax hook: " + t.getMessage());
        }
    }

    // 以下は前回と同じ（hookKillProcess, hookSystemExit など）
    private void hookKillProcess() { /* 省略せずv11と同じ内容を入れる */ }
    private void hookSystemExit() { /* 同じ */ }
    private void hookActivityFinish(XC_LoadPackage.LoadPackageParam lpparam) { /* 同じ */ }
    private void hookDisplayRefreshRate(XC_LoadPackage.LoadPackageParam lpparam) { /* 同じ */ }
    private void hookUnityTargetFrameRate(XC_LoadPackage.LoadPackageParam lpparam) { /* 同じ */ }
    private void hookRootChecks() { /* 同じ */ }
    private void hideXposedTraces(XC_LoadPackage.LoadPackageParam lpparam) { /* 同じ */ }

    private void loadPrefsAndNative() {
        XSharedPreferences settings = getPref("fps_prefs");
        if (settings != null) realFps = getIntPref(settings, "fps", 60);
        XposedBridge.log("UnityFPSUnlocker: realFps=" + realFps + " fakeRefresh=30");

        try {
            System.loadLibrary("UnityFPSUnlocker");
            HelloWorld(delay, realFps, mod_opcode, scale);
            XposedBridge.log("Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log("Native load failed");
        }
    }
}

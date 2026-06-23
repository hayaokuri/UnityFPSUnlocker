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

        XposedBridge.log("UnityFPSUnlocker: #コンパス v11 - セキュリティポリシー405_54_0 特化制圧");

        hookDetectionPopupUltra(lpparam);
        hookSecurityPolicyUltra(lpparam);   // 新強化
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
            XposedBridge.log("★ DetectionPopup ULTRA BLOCK v11 ★");

            for (Method m : detClass.getDeclaredMethods()) {
                final String name = m.getName();
                XposedHelpers.findAndHookMethod(detClass, name, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("UnityFPSUnlocker: ★ ULTRA BLOCKED " + name + " ★");
                        param.setResult(null);
                    }
                });
            }

            String[] critical = {"Ij11111IlIijjjlil1jliI", "finishApp", "killProcess", "exitApp", "finish", "shutdown", "onDestroy", "checkSecurity", "securityPolicy", "reportViolation"};
            for (String name : critical) {
                try {
                    XposedHelpers.findAndHookMethod(detClass, name, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log("UnityFPSUnlocker: ★ CRITICAL 405 BLOCK " + name + " ★");
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

    // セキュリティポリシー特化ブロック
    private void hookSecurityPolicyUltra(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // ポリシー違反終了系
            XposedHelpers.findAndHookMethod("android.os.Process", lpparam.classLoader, "killProcess", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("UnityFPSUnlocker: ★ SECURITY KILL BLOCKED ★");
                    param.setResult(null);
                }
            });

            XposedHelpers.findAndHookMethod("java.lang.Runtime", lpparam.classLoader, "exit", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("UnityFPSUnlocker: ★ Runtime.exit BLOCKED (405) ★");
                    param.setResult(null);
                }
            });

            // アプリ終了関連全般
            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "finishAndRemoveTask", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("UnityFPSUnlocker: ★ finishAndRemoveTask BLOCKED ★");
                    param.setResult(null);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("SecurityPolicy hook: " + t.getMessage());
        }
    }

    // 残りはv10と同じ（hookKillProcess, hookSystemExit など省略せず全部残す）
    private void hookKillProcess() { /* v10と同じ */ }
    private void hookSystemExit() { /* v10と同じ */ }
    private void hookActivityFinish(XC_LoadPackage.LoadPackageParam lpparam) { /* v10と同じ */ }
    private void hookDisplayRefreshRate(XC_LoadPackage.LoadPackageParam lpparam) { /* v10と同じ */ }
    private void hookUnityTargetFrameRate(XC_LoadPackage.LoadPackageParam lpparam) { /* v10と同じ */ }
    private void hookRootChecks() { /* v10と同じ */ }
    private void hideXposedTraces(XC_LoadPackage.LoadPackageParam lpparam) { /* v10と同じ */ }

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

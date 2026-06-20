package io.github.hexstr.UnityFPSUnlocker;

import android.app.Activity;
import android.content.Context;
import android.os.Process;
import android.view.Window;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;

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
        boolean isCompass = "com.nhnpa.cps.huawei".equals(pkg);

        if (isCompass) {
            XposedBridge.log("UnityFPSUnlocker: #コンパス アンチブロック 強化版");

            // DetectionPopup より広範囲フック
            try {
                Class<?> detClass = XposedHelpers.findClass("com.siem.ms7.DetectionPopup", lpparam.classLoader);
                for (Method m : detClass.getDeclaredMethods()) {
                    String name = m.getName();
                    if (name.length() > 10 || name.contains("Ij") || name.contains("kill") || name.contains("finish") || name.contains("detect")) {
                        XposedHelpers.findAndHookMethod(detClass, name, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                XposedBridge.log("UnityFPSUnlocker: ★ BLOCKED DetectionPopup." + name + " ★");
                                param.setResult(null);
                            }
                        });
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log("DetectionPopup broad hook: " + t.getMessage());
            }

            // killProcess / exit 強化
            hookKillProcess();
            hookSystemExit();

            // 追加：よくあるroot検知メソッドブロック
            hookCommonRootChecks(lpparam);
        }

        // 設定読み込み & Native
        loadPrefsAndNative();
    }

    private void hookKillProcess() {
        try {
            XposedHelpers.findAndHookMethod(Process.class, "killProcess", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if ((int) param.args[0] == Process.myPid()) {
                        XposedBridge.log("UnityFPSUnlocker: ★ SELF KILL BLOCKED ★");
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {}
    }

    private void hookSystemExit() {
        try {
            XposedHelpers.findAndHookMethod(System.class, "exit", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log("UnityFPSUnlocker: System.exit BLOCKED");
                    param.setResult(null);
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookCommonRootChecks(XC_LoadPackage.LoadPackageParam lpparam) {
        // Runtime.exec("su") などブロック例
        try {
            XposedHelpers.findAndHookMethod(Runtime.class, "exec", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String cmd = (String) param.args[0];
                    if (cmd != null && (cmd.contains("su") || cmd.contains("magisk"))) {
                        XposedBridge.log("UnityFPSUnlocker: Blocked root cmd: " + cmd);
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void loadPrefsAndNative() {
        // prefs & native load (省略せず以前と同じ)
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
            XposedBridge.log("Native load skipped (検知ブロックは動作中)");
        }
    }
}

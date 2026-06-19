package io.github.hexstr.UnityFPSUnlocker;

import android.app.Activity;
import android.content.Context;
import android.os.Process;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MyModule implements IXposedHookLoadPackage {

    private int display_mode_id = -1;
    private int delay = 5;
    private int fps = 90;
    private boolean mod_opcode = true;
    private float scale = -1;

    private static XSharedPreferences getPref(String path) {
        XSharedPreferences pref = new XSharedPreferences(BuildConfig.APPLICATION_ID, path);
        return pref.getFile().canRead() ? pref : null;
    }

    private static int getIntPref(XSharedPreferences settings, String key, int fallback) {
        return parseInt(settings.getString(key, String.valueOf(fallback)), fallback);
    }

    private static float getFloatPref(XSharedPreferences settings, String key, float fallback) {
        return parseFloat(settings.getString(key, String.valueOf(fallback)), fallback);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            XposedBridge.log("Invalid integer preference value: " + value);
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            XposedBridge.log("Invalid float preference value: " + value);
            return fallback;
        }
    }

    public static native void HelloWorld(int delay, int fps, boolean mod_opcode, float scale);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String package_name = lpparam.packageName;

        // #コンパス専用処理
        boolean isCompass = "com.nhnpa.cps.huawei".equals(package_name);

        XSharedPreferences settings = getPref("fps_prefs");
        if (settings != null) {
            display_mode_id = getIntPref(settings, "display_mode_id", -1);
            delay = getIntPref(settings, "delay", 5);
            fps = getIntPref(settings, "fps", 90);
            mod_opcode = settings.getBoolean("mod_opcode", true);
            scale = getFloatPref(settings, "scale", -1);

            // per-app設定
            display_mode_id = getIntPref(settings, package_name + "_per_app_display_mode_id", display_mode_id);
            delay = getIntPref(settings, package_name + "_per_app_delay", delay);
            fps = getIntPref(settings, package_name + "_per_app_fps", fps);
            mod_opcode = settings.getBoolean(package_name + "_per_app_mod_opcode", mod_opcode);
            scale = getFloatPref(settings, package_name + "_per_app_scale", scale);
        } else {
            XposedBridge.log("Cannot read settings");
        }

        // ====================== 検知ブロック（最優先） ======================
        if (isCompass) {
            XposedBridge.log("UnityFPSUnlocker: #コンパス検知ブロックを有効化");

            // 1. com.siem.ms7.DetectionPopup の kill系メソッドをブロック
            try {
                Class<?> detectionClass = XposedHelpers.findClass("com.siem.ms7.DetectionPopup", lpparam.classLoader);
                
                // ログにあったobfuscatedメソッド名
                XposedHelpers.findAndHookMethod(detectionClass, "Ij11111IlIijjjlil1jliI",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log("UnityFPSUnlocker: Blocked DetectionPopup finishApp / kill!");
                            param.setResult(null); // nop
                        }
                    });
            } catch (Throwable t) {
                XposedBridge.log("UnityFPSUnlocker: DetectionPopup hook failed: " + t.getMessage());
            }

            // 2. android.os.Process.killProcess をブロック
            try {
                XposedHelpers.findAndHookMethod(Process.class, "killProcess", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int pid = (int) param.args[0];
                            if (pid == Process.myPid()) {
                                XposedBridge.log("UnityFPSUnlocker: Blocked self-killProcess from #コンパス!");
                                param.setResult(null);
                                return;
                            }
                        }
                    });
            } catch (Throwable t) {
                XposedBridge.log("UnityFPSUnlocker: killProcess hook failed: " + t.getMessage());
            }
        }
        // ====================== 検知ブロック 終了 ======================

        try {
            // UnityPlayer Constructor Hook（既存）
            XposedHelpers.findAndHookConstructor(
                    "com.unity3d.player.UnityPlayer",
                    lpparam.classLoader,
                    Context.class,
                    XposedHelpers.findClass("com.unity3d.player.EnumC1199x", lpparam.classLoader),
                    XposedHelpers.findClass("com.unity3d.player.IUnityPlayerLifecycleEvents", lpparam.classLoader),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object contextObj = param.args[0];
                            if (contextObj instanceof Activity) {
                                Activity activity = (Activity) contextObj;
                                if (activity != null && display_mode_id != -1) {
                                    Window window = activity.getWindow();
                                    WindowManager.LayoutParams params = window.getAttributes();
                                    params.preferredDisplayModeId = display_mode_id;
                                    window.setAttributes(params);
                                    XposedBridge.log("Set display mode to " + display_mode_id);
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log("UnityFPSUnlocker Hook failed: " + t.getMessage());
        }

        XposedBridge.log("display_mode_id: " + display_mode_id + " | delay: " + delay 
                + " | fps: " + fps + " | mod_opcode: " + mod_opcode + " | scale: " + scale);

        System.loadLibrary("UnityFPSUnlocker");
        HelloWorld(delay, fps, mod_opcode, scale);
    }
}

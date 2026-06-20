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

    // ★修正済：引数を int mod_opcode に変更
    public static native void HelloWorld(int delay, int fps, int mod_opcode, float scale);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String package_name = lpparam.packageName;
        boolean isCompass = "com.nhnpa.cps.huawei".equals(package_name);

        XSharedPreferences settings = getPref("fps_prefs");
        if (settings != null) {
            display_mode_id = getIntPref(settings, "display_mode_id", -1);
            delay = getIntPref(settings, "delay", 5);
            fps = getIntPref(settings, "fps", 90);
            mod_opcode = settings.getBoolean("mod_opcode", true);
            scale = getFloatPref(settings, "scale", -1);
        }

        // ====================== #コンパス強制終了ブロック ======================
        if (isCompass) {
            XposedBridge.log("UnityFPSUnlocker: #コンパス 検知ブロック開始");

            // 1. DetectionPopup.finishApp を直接潰す
            try {
                XposedHelpers.findAndHookMethod("com.siem.ms7.DetectionPopup", lpparam.classLoader, "finishApp",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log("UnityFPSUnlocker: Blocked finishApp!");
                            param.setResult(null); // 強制終了をキャンセル
                        }
                    });
            } catch (Throwable t) {
                XposedBridge.log("UnityFPSUnlocker: finishApp hook failed: " + t.getMessage());
            }

            // 2. System.exit(0) を念のため潰す
            try {
                XposedHelpers.findAndHookMethod(System.class, "exit", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log("UnityFPSUnlocker: Blocked System.exit!");
                            param.setResult(null);
                        }
                    });
            } catch (Throwable t) {
                XposedBridge.log("UnityFPSUnlocker: System.exit hook failed.");
            }
        }
        // ====================== ブロック終了 ======================

        // UnityPlayer Hook（★修正済：$ を . に直した完全版）
        try {
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
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("UnityFPSUnlocker Hook failed: " + t.getMessage());
        }

        // ネイティブライブラリ読み込み
        try {
            System.loadLibrary("UnityFPSUnlocker");
            // ★修正済：mod_opcode を int に変換
            HelloWorld(delay, fps, mod_opcode ? 1 : 0, scale);
            XposedBridge.log("UnityFPSUnlocker: Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log("UnityFPSUnlocker: Native library load failed: " + e.getMessage());
        }
    }
}

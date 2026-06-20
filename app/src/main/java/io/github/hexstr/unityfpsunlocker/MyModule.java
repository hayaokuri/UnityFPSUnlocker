package io.github.hexstr.UnityFPSUnlocker;

import android.app.Activity;
import android.content.Context;
import android.os.Process;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Method; // ★リフレクション用に追加

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

    // ネイティブ関数（C++側と型を合わせたint）
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

        // ====================== #コンパス 激強ブロック開始 (Grok+Gemini) ======================
        if (isCompass) {
            XposedBridge.log("UnityFPSUnlocker: #コンパス 激強検知ブロック開始");

            // 1. Process.killProcess を潰す
            try {
                XposedHelpers.findAndHookMethod(Process.class, "killProcess", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if ((int)param.args[0] == Process.myPid()) {
                            XposedBridge.log("UnityFPSUnlocker: ★ BLOCKED Process.killProcess ★");
                            param.setResult(null);
                        }
                    }
                });
            } catch (Throwable t) {}

            // 2. System.exit を潰す
            try {
                XposedHelpers.findAndHookMethod(System.class, "exit", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("UnityFPSUnlocker: ★ BLOCKED System.exit ★");
                        param.setResult(null);
                    }
                });
            } catch (Throwable t) {}

            // 3. Activity.finish を潰す（Grokの提案：ゲームが正常に閉じられなくなる可能性があるが、強制終了の阻止には最強）
            try {
                XposedHelpers.findAndHookMethod(Activity.class, "finish", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("UnityFPSUnlocker: ★ BLOCKED Activity.finish ★");
                        param.setResult(null);
                    }
                });
            } catch (Throwable t) {}

            // 4. DetectionPopup の全キルスイッチを引数無視で完全に潰す（Geminiの真骨頂）
            try {
                Class<?> detClass = XposedHelpers.findClass("com.siem.ms7.DetectionPopup", lpparam.classLoader);
                String[] criticalMethods = {"finishApp", "Ij11111IlIijjjlil1jliI", "killProcess", "exitApp", "finish", "onDestroy", "shutdown"};
                
                // クラス内の全メソッドを舐め回して、名前が一致したら引数に関係なく全部フックする
                for (Method m : detClass.getDeclaredMethods()) {
                    for (String target : criticalMethods) {
                        if (m.getName().equals(target)) {
                            XposedBridge.hookMethod(m, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) {
                                    XposedBridge.log("UnityFPSUnlocker: ★ CRITICAL BLOCK " + m.getName() + " ★");
                                    param.setResult(null);
                                }
                            });
                        }
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log("UnityFPSUnlocker: DetectionPopup broad hook failed: " + t.getMessage());
            }
        }
        // ====================== ブロック終了 ======================

        // UnityPlayer Hook
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
            HelloWorld(delay, fps, mod_opcode ? 1 : 0, scale);
            XposedBridge.log("UnityFPSUnlocker: Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            XposedBridge.log("UnityFPSUnlocker: Native library load failed: " + e.getMessage());
        }
    }
}

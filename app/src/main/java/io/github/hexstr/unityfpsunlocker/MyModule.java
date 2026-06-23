package io.github.hexstr.UnityFPSUnlocker;

import android.app.Activity;
import android.content.Context;
import android.os.Process;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MyModule implements IXposedHookLoadPackage {

    private static final String TAG = "UnityFPSUnlocker";
    private int targetRealFps = 60;      // 実際に動かしたいFPS
    private float fakeRefreshRate = 30.0f; // ゲームに偽装するFPS

    private static XSharedPreferences getPref() {
        XSharedPreferences pref = new XSharedPreferences(BuildConfig.APPLICATION_ID, "prefs");
        return pref.getFile().canRead() ? pref : null;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.nhnpa.cps.huawei")) return;

        XposedBridge.log(TAG + " Loaded for #コンパス");

        // 1. Refresh Rate 完全偽装（これが一番重要）
        hookRefreshRate(lpparam);

        // 2. アンチチート DetectionPopup 完全ブロック
        hookDetectionPopup(lpparam);

        // 3. killProcess / finishApp ブロック
        hookKillProcess(lpparam);

        // 4. Preferred Display Mode（高リフレッシュレート強制）
        hookPreferredDisplayMode(lpparam);

        // 5. Unity TargetFrameRate 制御
        hookUnityFrameRate(lpparam);
    }

    private void hookRefreshRate(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.view.Display",
                lpparam.classLoader,
                "getRefreshRate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(fakeRefreshRate);
                        XposedBridge.log(TAG + " getRefreshRate faked to " + fakeRefreshRate);
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + " RefreshRate hook failed: " + t);
        }
    }

    private void hookDetectionPopup(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> detectionClass = XposedHelpers.findClass("com.siem.ms7.DetectionPopup", lpparam.classLoader);

            // 複数の可能性をまとめてブロック
            XposedHelpers.findAndHookMethod(detectionClass, "finishApp", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log(TAG + " Blocked DetectionPopup.finishApp");
                    param.setResult(null);
                }
            });

            // 難読化メソッドも保険でブロック
            XposedHelpers.findAndHookMethod(detectionClass, "Ij11111IlIijjjlil1jliI", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log(TAG + " Blocked DetectionPopup obfuscated method");
                    param.setResult(null);
                }
            });

        } catch (Throwable t) {
            XposedBridge.log(TAG + " DetectionPopup hook failed (maybe class changed): " + t.getMessage());
        }
    }

    private void hookKillProcess(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                Process.class,
                "killProcess",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + " Blocked killProcess! SELF KILL PREVENTED");
                        param.setResult(null);
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + " killProcess hook failed: " + t);
        }
    }

    private void hookPreferredDisplayMode(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onCreate",
                android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        if (activity != null) {
                            Window window = activity.getWindow();
                            WindowManager.LayoutParams params = window.getAttributes();
                            // 高リフレッシュレートを優先
                            params.preferredDisplayModeId = 1; // デバイスによって調整
                            window.setAttributes(params);
                            XposedBridge.log(TAG + " Set preferredDisplayModeId");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + " PreferredDisplayMode hook failed: " + t);
        }
    }

    private void hookUnityFrameRate(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.unity3d.player.UnityPlayer",
                lpparam.classLoader,
                "setFrameRate",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.args[0] = targetRealFps;
                        XposedBridge.log(TAG + " Forced Unity FrameRate to " + targetRealFps);
                    }
                }
            );
        } catch (Throwable ignored) {}
    }
}

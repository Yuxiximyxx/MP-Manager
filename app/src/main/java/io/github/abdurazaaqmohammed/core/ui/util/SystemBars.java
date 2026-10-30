package io.github.abdurazaaqmohammed.core.ui.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import io.github.abdurazaaqmohammed.core.ui.theme.BuiltInThemes;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;

/**
 * Global immersive system-bar treatment, applied to every activity from
 * {@code App}'s activity lifecycle callbacks and to dialogs at their own
 * show/onStart entry points.
 *
 * <p>The navigation bar is hidden in sticky-immersive mode: content owns the
 * whole bottom edge, and a swipe from the bottom edge reveals the transient
 * bar again. That is the only treatment that keeps working on Android 15+,
 * where {@code Window#setNavigationBarColor} is ignored once the platform
 * enforces edge-to-edge — a merely transparent bar then falls back to the
 * window background and still reads as a black strip.
 *
 * <p>While the transient bar is visible its colour is still transparent with
 * no divider and no contrast scrim, and the icon brightness follows the active
 * MP Manager theme rather than the system dark mode (the app can run Light on
 * a dark system, and Black on a light one).
 */
public final class SystemBars {

    private SystemBars() {
    }

    /**
     * Immersive bars for one activity window. Safe to call repeatedly
     * (onCreate/onResume/window-focus), and deliberately cheap: it only writes
     * window flags and insets-controller state, never layout.
     */
    public static void applyImmersiveNavigationBar(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Window window = activity.getWindow();
        if (window == null) return;
        apply(window, isLightNavigationBar(activity));
    }

    /**
     * Immersive bars for one dialog window: AlertDialogs, DialogFragments,
     * BottomSheetDialogs and custom dialogs all own a window whose system bars
     * are painted from that window's attributes, so the activity-level
     * treatment never reaches them on its own.
     */
    public static void applyImmersiveNavigationBar(Dialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        apply(window, isLightNavigationBar(dialog.getContext()));
    }

    /**
     * Whether the current theme wants dark icons on the navigation bar.
     */
    public static boolean isLightNavigationBar(Context context) {
        if (context == null) return false;
        try {
            String id = ThemeRegistry.getCurrentId(context);
            if (BuiltInThemes.LIGHT_ID.equals(id)) return true;
            if (BuiltInThemes.DARK_ID.equals(id) || BuiltInThemes.BLACK_ID.equals(id)) return false;
            return (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO;
        } catch (Exception ignored) {
            return (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO;
        }
    }

    private static void apply(Window window, boolean lightBars) {
        if (window == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // The divider is drawn as a hairline on top of the bar; drop it so
                // the transient bar does not turn into a visible line.
                window.setNavigationBarDividerColor(Color.TRANSPARENT);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ dims the bar to keep contrast against app content.
                // That scrim is exactly what makes a transparent bar read as a
                // solid strip, so it must be off.
                window.setNavigationBarContrastEnforced(false);
            }
        }

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller == null) return;

        // Sticky immersive: bars stay hidden, a bottom-edge swipe shows them
        // transiently and they hide themselves again.
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.navigationBars());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            controller.setAppearanceLightNavigationBars(lightBars);
        }
    }
}

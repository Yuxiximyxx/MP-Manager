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
import androidx.core.view.WindowInsetsControllerCompat;

import io.github.abdurazaaqmohammed.core.ui.theme.BuiltInThemes;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;

/**
 * Global system-bar treatment, applied to every activity from
 * {@code App}'s activity lifecycle callbacks and to dialogs at their own
 * show/onStart entry points, so screens that do not extend BaseActivity and
 * the windows floating above them get the same look.
 *
 * <p>The navigation bar becomes transparent and loses the system contrast
 * scrim, so the window background (for a dialog: the activity underneath it)
 * shows through continuously instead of a separately tinted strip. Icon
 * appearance follows the active MP Manager theme rather than the system dark
 * mode, because the app can run Light on a dark system (and Black on a light
 * one).
 *
 * <p>Below API 26 the navigation bar keeps its opaque theme colour: light
 * navigation icons only exist from O onwards, so a transparent bar would leave
 * them invisible on a light background. Dialog windows get the same treatment
 * from {@code values-v26/styles.xml}. Nothing changes there compared to the
 * previous behaviour.
 */
public final class SystemBars {

    private SystemBars() {
    }

    /**
     * Immersive navigation bar for one activity window. Safe to call repeatedly
     * (onCreate/onResume/window-focus), and deliberately cheap: it only writes
     * window flags and colours, never layout.
     */
    public static void applyImmersiveNavigationBar(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Window window = activity.getWindow();
        if (window == null) return;
        apply(window, isLightNavigationBar(activity));
    }

    /**
     * Immersive navigation bar for one dialog window: AlertDialogs,
     * DialogFragments, BottomSheetDialogs and custom dialogs all own a window
     * whose navigation bar is painted from that window's attributes, so the
     * activity-level treatment never reaches them on its own.
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // API 23-25 only render white navigation icons, so a transparent bar
            // would be invisible over light content. Keep the theme colour.
            return;
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // The divider is drawn as a hairline on top of the bar; drop it so the
            // transparent bar does not turn into a visible line.
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ dims the bar to keep contrast against app content. That
            // scrim is exactly what makes an otherwise transparent bar look like a
            // solid strip, so it must be off for the immersive look.
            window.setNavigationBarContrastEnforced(false);
        }

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightNavigationBars(lightBars);
        }
    }
}

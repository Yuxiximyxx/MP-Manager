package io.github.abdurazaaqmohammed.core.ui.util;

import android.app.Activity;
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
 * {@code App}'s activity lifecycle callbacks so screens that do not extend
 * BaseActivity get the same look.
 *
 * <p>The navigation bar becomes transparent and loses the system contrast
 * scrim, so the window background shows through continuously instead of a
 * separately tinted strip. Icon appearance follows the active theme rather
 * than the system dark mode, because MP Manager can run Light on a dark system
 * (and Black on a light one).
 *
 * <p>Below API 26 the navigation bar keeps its opaque theme colour: light
 * navigation icons only exist from O onwards, so a transparent bar would leave
 * them invisible on a light background. Nothing changes there compared to the
 * previous behaviour.
 */
public final class SystemBars {

    private SystemBars() {
    }

    public static void applyImmersiveNavigationBar(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

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
            controller.setAppearanceLightNavigationBars(isLightTheme(activity));
        }
    }

    private static boolean isLightTheme(Activity activity) {
        String id = ThemeRegistry.getCurrentId(activity);
        if (BuiltInThemes.LIGHT_ID.equals(id)) return true;
        if (BuiltInThemes.DARK_ID.equals(id) || BuiltInThemes.BLACK_ID.equals(id)) return false;
        return (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO;
    }
}

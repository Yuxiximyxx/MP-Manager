package io.github.abdurazaaqmohammed.core.ui.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Map;
import java.util.WeakHashMap;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.theme.BuiltInThemes;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;

/**
 * Global immersive system-bar treatment, applied to every activity from
 * {@code App}'s activity lifecycle callbacks and to dialogs at their own
 * show/onStart entry points.
 *
 * <p>Layout is edge-to-edge: the window draws behind the system bars and its
 * content is only inset at the top, so the app's own background continues
 * behind the transparent navigation bar instead of a separately tinted strip.
 * A view with id {@code bottomBar} keeps its bottom padding in step with the
 * navigation-bar inset, so a bottom toolbar's background extends under the bar
 * while its buttons stay above it.
 *
 * <p>Android 15+ already enforces edge-to-edge for applications targeting 35
 * and later and ignores {@code Window#setNavigationBarColor}; the colour writes
 * below therefore only matter on older releases, and the contrast scrim is
 * switched off wherever the platform still honours it.
 */
public final class SystemBars {

    /** Original bottom padding of a padded bottom bar, keyed weakly. */
    private static final Map<View, Integer> bottomBarBasePadding = new WeakHashMap<>();

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
        // The app paints to the screen edges; content insets are applied by
        // {@link #applyContentInsets(Activity)}.
        WindowCompat.setDecorFitsSystemWindows(window, false);
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
     * Applies the window insets that the edge-to-edge layout needs: status bar
     * and cutout on the top and sides, nothing at the bottom, so lists and
     * backgrounds scroll behind the transparent navigation bar.
     *
     * <p>{@code R.id.bottomBar}, when the screen has one, is the exception: its
     * background extends under the bar and its own padding grows by the
     * navigation-bar inset, which keeps the buttons above the system bar.
     */
    public static void applyContentInsets(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Window window = activity.getWindow();
        if (window == null) return;
        View content = window.getDecorView().findViewById(android.R.id.content);
        if (content == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            View bottomBar = v.findViewById(R.id.bottomBar);
            if (bottomBar != null) {
                Integer base = bottomBarBasePadding.get(bottomBar);
                if (base == null) {
                    base = bottomBar.getPaddingBottom();
                    bottomBarBasePadding.put(bottomBar, base);
                }
                bottomBar.setPadding(
                        bottomBar.getPaddingLeft(),
                        bottomBar.getPaddingTop(),
                        bottomBar.getPaddingRight(),
                        base + bars.bottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
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
                window.setNavigationBarDividerColor(Color.TRANSPARENT);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }
        }

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            controller.setAppearanceLightNavigationBars(lightBars);
        }
    }
}

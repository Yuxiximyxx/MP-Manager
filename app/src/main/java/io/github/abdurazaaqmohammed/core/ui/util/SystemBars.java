package io.github.abdurazaaqmohammed.core.ui.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.widget.Toolbar;
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
 * <p>Both bars are transparent and the window draws behind them. Content is
 * inset on the sides only; the status-bar inset is applied to the screen's own
 * header (a {@link Toolbar} or one of the known header ids) so the header keeps
 * its background under the status bar, and the navigation-bar inset is applied
 * to a {@code bottomBar} so a bottom toolbar's background extends under the bar
 * while its buttons stay above it. Screens without a detectable header fall
 * back to padding the content container, which keeps every screen usable.
 *
 * <p>Android 15+ already enforces edge-to-edge for applications targeting 35
 * and later and ignores the bar colour setters; those calls therefore only
 * matter on older releases, and the contrast scrims are switched off wherever
 * the platform still honours them.
 */
public final class SystemBars {

    /** Header ids used by MP Manager screens, checked after a Toolbar scan. */
    private static final int[] HEADER_IDS = {
            R.id.toolbar, R.id.topAppBar, R.id.settingsToolbar, R.id.tools_toolbar,
            R.id.topBar, R.id._toolbar, R.id.drawer_toolbar, R.id.sharedEntryToolbar,
            R.id.hexToolbar, R.id.compare_text_toolbar, R.id.compare_dex_toolbar,
            R.id.linear_header, R.id.hexHeader
    };

    /** Original padding of a view we extend for the bars, keyed weakly. */
    private static final Map<View, int[]> basePadding = new WeakHashMap<>();

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
        boolean lightIcons = isLightBars(activity);
        applyBars(window, lightIcons, lightIcons, true);
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
        applyBars(window, false, isLightBars(dialog.getContext()), false);
    }

    /**
     * Applies the window insets that the edge-to-edge layout needs.
     *
     * <p>Side insets always go on the content container. The top inset goes on
     * the screen header (Toolbar or a known header id) so its background runs
     * under the status bar; if no header is found the content container keeps
     * the top padding so nothing slides under the clock. The bottom inset goes
     * on {@code R.id.bottomBar} only, so lists and backgrounds scroll behind
     * the transparent navigation bar.
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
            v.setPadding(bars.left, 0, bars.right, 0);
            View header = findHeader(v);
            if (header != null) {
                padTop(header, bars.top);
            } else {
                v.setPadding(bars.left, bars.top, bars.right, 0);
            }
            View bottomBar = v.findViewById(R.id.bottomBar);
            if (bottomBar != null) {
                padBottom(bottomBar, bars.bottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }

    /**
     * Whether the current theme wants dark icons on the system bars.
     */
    public static boolean isLightNavigationBar(Context context) {
        return isLightBars(context);
    }

    private static boolean isLightBars(Context context) {
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

    /** First Toolbar in the tree, or a known header id. */
    private static View findHeader(View content) {
        View toolbar = firstToolbar(content);
        if (toolbar != null) return toolbar;
        for (int id : HEADER_IDS) {
            View candidate = content.findViewById(id);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static View firstToolbar(View view) {
        if (view instanceof Toolbar) return view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = firstToolbar(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void padTop(View view, int inset) {
        int[] base = basePadding.get(view);
        if (base == null) {
            base = new int[]{view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), view.getPaddingBottom()};
            basePadding.put(view, base);
        }
        view.setPadding(base[0], base[1] + inset, base[2], base[3]);
    }

    private static void padBottom(View view, int inset) {
        int[] base = basePadding.get(view);
        if (base == null) {
            base = new int[]{view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), view.getPaddingBottom()};
            basePadding.put(view, base);
        }
        view.setPadding(base[0], base[1], base[2], base[3] + inset);
    }

    private static void applyBars(Window window, boolean lightStatusIcons,
                                  boolean lightNavigationIcons, boolean statusBar) {
        if (window == null) return;

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (statusBar) {
            window.setStatusBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setStatusBarContrastEnforced(false);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        if (controller != null) {
            if (statusBar) {
                controller.setAppearanceLightStatusBars(lightStatusIcons);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                controller.setAppearanceLightNavigationBars(lightNavigationIcons);
            }
        }
    }
}

/*
 *   Copyright (C) 2022 Ratul Hasan
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.ratul.topactivity;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.abdurazaaqmohammed.plugins.packs.PackManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.core.app.NotificationManagerCompat;
import androidx.multidex.MultiDexApplication;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.app.ServiceLocator;
import io.github.abdurazaaqmohammed.core.ui.util.SystemBars;
import io.github.abdurazaaqmohammed.utils.AppLogs;
import io.github.ratul.topactivity.manager.NotificationUiManager;
import io.github.ratul.topactivity.ui.ClipboardActivity;

public class App extends MultiDexApplication {

    private static App instance;

    public static final String REPO = "codehasan/Current-Activity";
    public static final String REPO_URL = "https://github.com/" + REPO;

    private ClipboardManager clipboardManager;

    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }

    public static App getInstance() {
        return instance;
    }

    /**
     * Decor views already watched for window-focus changes. Weak keys so a
     * destroyed window cannot pin its activity.
     */
    private static final Map<View, Boolean> navBarWatched =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Applies the immersive navigation bar and keeps it applied: the deferred
     * re-apply covers bars recoloured later in onCreate/onResume, and the focus
     * listener covers every window regaining focus (a dismissed dialog,
     * transient system bars, returning to the task) without touching layout.
     */
    private static void applyNavigationBar(Activity activity) {
        if (activity == null) return;
        SystemBars.applyImmersiveNavigationBar(activity);
        SystemBars.applyContentInsets(activity);
        Window window = activity.getWindow();
        if (window == null) return;
        final View decor = window.getDecorView();
        if (decor == null) return;
        decor.post(() -> SystemBars.applyImmersiveNavigationBar(activity));
        synchronized (navBarWatched) {
            if (navBarWatched.containsKey(decor)) return;
            navBarWatched.put(decor, Boolean.TRUE);
        }
        decor.getViewTreeObserver().addOnWindowFocusChangeListener(focused -> {
            if (focused) SystemBars.applyImmersiveNavigationBar(activity);
        });
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        instance = this;
        AppLogs.install(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Global navigation-bar immersion: applies to every activity, including the
        // ones that do not extend BaseActivity, so the look cannot drift per screen.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                applyNavigationBar(activity);
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                // A theme switch recreates activities, and screens may recolour the
                // bars from their own onCreate/onResume; reapply after that code ran
                // so the icon colour follows the new theme even when the window was
                // reused.
                applyNavigationBar(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
        ServiceLocator.init(this);
        // Cold-start optimization: each pack costs an asset read, a signature
        // check (PackageManager IPC), a resources.arsc parse and a
        // DexClassLoader dexopt. None of it is needed before first paint;
        // ToolsHub rebuilds its list in onResume, so late arrival is picked up.
        new Thread(() -> {
            try {
                // Tools (except a few built-ins like Storage Manager) ship as
                // downloadable packs now; load whatever the user installed.
                PackManager.loadInstalledPacks(this);
            } catch (Exception ignored) {
            }
        }, "packs-load").start();
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NotificationUiManager.CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_description));
            NotificationManagerCompat.from(this).createNotificationChannel(channel);
        }
    }

    public static void copyString(Context context, String str, String msg) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            instance.clipboardManager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), str));
        } else {
            context.startActivity(new Intent(context, ClipboardActivity.class)
                    .putExtra(Intent.EXTRA_TEXT, str)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        showToast(context, msg);
    }

    public static void showToast(Context context, String message) {
        try {
            Extensions.showMessage((Activity) context, message);
        } catch (Exception ignored) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
}

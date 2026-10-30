package io.github.abdurazaaqmohammed.core.ui.base;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.core.ui.util.AppFont;
import io.github.abdurazaaqmohammed.utils.ProgressManager;

/**
 * Single place for activity-wide UI behaviour.
 * All feature activities should extend this instead of AppCompatActivity
 * so theme switching, dynamic colors and edge-to-edge change in one file.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private int appliedFontEpoch = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Theme must be set before super.onCreate so inflation uses it.
        ThemeRegistry.applySaved(this);
        // Font factory must also wrap AppCompat's, so install before super.onCreate.
        AppFont.installFactory(this);
        appliedFontEpoch = AppFont.currentEpoch();
        super.onCreate(savedInstanceState);
        // Content insets (status bar/cutout padding, no bottom padding so the UI
        // runs behind the transparent navigation bar) are applied globally from
        // App's lifecycle callbacks; see SystemBars.applyContentInsets.
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getIntent() != null && getIntent().getBooleanExtra("restore_progress", false)) {
            getIntent().removeExtra("restore_progress");
            ProgressManager.restoreHidden();
        }
        // Pick up a font changed in Settings while this activity was in background.
        if (appliedFontEpoch != AppFont.currentEpoch()) recreate();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("restore_progress", false)) {
            ProgressManager.restoreHidden();
        }
    }

    /**
     * Named dpPx (not dp) to avoid clashing with the legacy private dp()
     * helpers still present in migrated activities.
     */
    protected int dpPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}

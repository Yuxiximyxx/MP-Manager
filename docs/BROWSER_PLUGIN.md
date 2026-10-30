# Built-in browser plugin

An installable Android plugin that gives MP Manager a real in-app browsing
surface. It is deliberately **not** part of the host APK: a browser needs its
own `Activity`, WebView lifecycle, file chooser and download handling, so it
ships as a separate application and integrates through the host's existing
external-plugin contract.

## Why a plugin and not a pack

MP Manager has two extension mechanisms:

| mechanism | shape | can declare Android `Activity`s? | fits a browser? |
| --- | --- | --- | --- |
| first-party pack (`packs/*`, in-process) | `ToolPack`/`ToolPlugin` returning embedded `View`s | no | no — WebView file uploads, downloads and new windows need an Activity lifecycle and `onActivityResult` |
| external plugin app (`plugins/*`, separate APK/UID) | activity per contract action, verified by certificate pinning | yes | yes |

The browser therefore follows the second row: a separate APK, discovered and
launched by the host like any other external plugin.

## How the host integrates it

1. **Discovery.** `plugins.ipc.PluginHost.query(context, ACTION_SIDEBAR_OPEN)`
   enumerates installed activities through `PackageManager.queryIntentActivities`
   with `GET_META_DATA`. The browser's manifest declares:

   ```xml
   <intent-filter>
     <action android:name="io.github.abdurazaaqmohammed.MPManager.action.SIDEBAR_OPEN" />
     <category android:name="android.intent.category.DEFAULT" />
   </intent-filter>
   <meta-data android:name="...PLUGIN_ID" android:value="browser.main" />
   <meta-data android:name="...TITLE"    android:value="@string/plugin_label" />
   ```

2. **Trust.** The first launch goes through `PluginHost`/`PluginTrust`: the host
   shows the plugin label, package and signing-certificate fingerprint, pins it
   on allow, re-prompts if the certificate changes and evicts trust on uninstall.
   The host starts it with an explicit `setClassName` intent only.

3. **Entry points.**
   - Sidebar entry — the external sidebar section in the built-in sidebar.
   - Tools page — the external plugin section (`PluginHost.ExternalPlugin` list).
   - Standalone launcher icon (also useful for adb/debugging).

4. **Build and release.** `settings.gradle` includes `:plugins:plugin-browser`.
   `android.yml` builds `:plugins:plugin-browser:assembleDebug` next to the app
   and uploads both APKs. `release.yml` builds the plugin release APK, signs it
   with the same release keystore and publishes it as `MP-Browser-1.0.apk`
   alongside the app APK.

## What the browser supports ("submit" operations)

- JavaScript, DOM storage, cookies and first-party cookie sync, so signed-in
  sites and JS-driven forms behave like on a normal browser.
- File inputs: `<input type="file">` is backed by the platform file chooser
  (`WebChromeClient.onShowFileChooser` → `ActivityResult`), which is what upload
  forms (issue attachments, release assets, plugin submissions) need.
- Form POST/redirect flows stay inside the browser; `target=_blank` is folded
  into the same window (`setSupportMultipleWindows(false)`).
- Downloads go through the system `DownloadManager` (with the WebView cookies
  attached), so an authenticated download keeps working.
- Address bar accepts URLs, bare domains, search words, `/sdcard/...` paths and
  `content://` URIs.
- HTTP(S) stays internal; `mailto:`, `tel:` and app-store links are handed to
  the system without changing the current page.
- WebView remote debugging is enabled, so an adb-attached desktop agent can
  drive *this* in-app browser (`chrome://inspect`) instead of controlling a
  desktop browser.

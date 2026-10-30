# Releasing MP Manager

Releases are cut by pushing a tag. The `Release` workflow
(`.github/workflows/release.yml`) checks the tag against `versionName`, builds
`assembleRelease`, zipaligns and signs the APK with the release keystore, then
publishes a GitHub release with auto-generated notes.

## One-time setup: signing secrets

Add these repository secrets under **Settings → Secrets and variables →
Actions**. They are only read by the `Release` workflow.

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | The `.jks`/`.p12` keystore, base64-encoded (single line) |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias inside the keystore |
| `RELEASE_KEY_PASSWORD` | Password of that key |

Create and encode a keystore:

```bash
keytool -genkeypair -v \
  -keystore release.jks -alias mpmanager \
  -keyalg RSA -keysize 4096 -validity 10000

base64 < release.jks | tr -d '\n' > release.jks.b64
# paste the contents of release.jks.b64 into RELEASE_KEYSTORE_BASE64
```

Keep the keystore and its passwords out of the repository — `*.jks` is already
ignored. Losing this key means future builds cannot update an installed app.

## Cutting a release

1. Bump `versionCode` and `versionName` in `app/build.gradle` (`versionCode`
   must strictly increase).
2. Commit, push to `main`, and let `Android CI` go green.
3. Tag the commit with the same version, optionally prefixed with `v`, and push
   the tag:

   ```bash
   git tag 1.2.0
   git push origin 1.2.0
   ```

4. Watch the `Release` run. The workflow fails fast with a `::error::` message
   when the tag and `versionName` disagree or a signing secret is missing.

The published asset is named `MP-Manager-<tag without v>.apk`.

## Verifying an APK locally

```bash
apksigner verify --print-certs MP-Manager-1.2.0.apk
```

## Notes

- Without the four signing secrets the workflow does not fail: it builds the
  debug variant, publishes `MP-Manager-<version>-debug.apk`, and states in the
  release notes that the APK is debug-signed. Configure the secrets to switch
  the same tag to a release-signed APK.
- The `Lint (advisory)` job does not block the pipeline; download its report
  from the run artifacts. Once the remaining warnings are triaged, the job can
  be made blocking by removing `continue-on-error`.
- `Android CI` runs on every push to `main` and on every pull request: debug
  build, JVM unit tests, and the advisory lint report.

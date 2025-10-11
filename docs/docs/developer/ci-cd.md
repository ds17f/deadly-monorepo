# CI/CD System

This document describes the CI/CD infrastructure for building and releasing the Deadly monorepo applications.

## Table of Contents

- [Overview](#overview)
- [Required GitHub Secrets](#required-github-secrets)
- [Automated Secret Upload](#automated-secret-upload)
- [Fastlane for Android](#fastlane-for-android)
- [Fastlane for iOS](#fastlane-for-ios)
- [GitHub Actions Workflows](#github-actions-workflows)
- [Local Signed Builds](#local-signed-builds)
- [Complete Release Process](#complete-release-process)
- [Troubleshooting](#troubleshooting)

---

## Overview

The CI/CD pipeline is structured in three tiers:

1. **Local Build Commands** - Makefile targets for day-to-day development
2. **Fastlane Automation** - Cross-platform build automation tool
3. **GitHub Actions** - Cloud-based CI/CD for automated testing and releases

This layered approach allows for:

- Quick local iteration with signed builds
- Consistent build processes via fastlane
- Automated CI/CD without manual intervention

---

## Required GitHub Secrets

The monorepo requires **11 GitHub secrets** for automated builds:

### Android Secrets (5)

| Secret Name | Type | Description | How to Generate |
|-------------|------|-------------|-----------------|
| `ANDROID_KEYSTORE_BASE64` | File (Base64) | Base64-encoded Android keystore (.jks) | `base64 -i .secrets/my-release-key.jks \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | String | Keystore password | From `.secrets/keystore.properties` (storePassword line) |
| `ANDROID_KEY_ALIAS` | String | Key alias name | From `.secrets/keystore.properties` (keyAlias line) |
| `ANDROID_KEY_PASSWORD` | String | Key password | From `.secrets/keystore.properties` (keyPassword line) |
| `PLAY_STORE_JSON_BASE64` | File (Base64) | Google Play service account JSON | `base64 -i .secrets/thedeadly-app-f48493c2a133.json \| pbcopy` |

### iOS Secrets (6)

| Secret Name | Type | Description | How to Generate |
|-------------|------|-------------|-----------------|
| `IOS_CERTIFICATE_BASE64` | File (Base64) | P12 code signing certificate | `base64 -i .secrets/DeadlyApp_AppStore2.p12 \| pbcopy` |
| `IOS_CERTIFICATE_PASSWORD` | String | P12 certificate password | From `.secrets/cert_password.txt` |
| `IOS_PROVISIONING_PROFILE_BASE64` | File (Base64) | iOS provisioning profile | `base64 -i .secrets/DeadlyApp_AppStore2.mobileprovision \| pbcopy` |
| `APP_STORE_CONNECT_KEY_ID` | String | App Store Connect API Key ID | Fixed value: `V862XWV7WB` |
| `APP_STORE_CONNECT_ISSUER_ID` | String | App Store Connect Issuer ID | Fixed value: `9501cc7b-1a6c-4e4d-8c37-c04149a31886` |
| `APP_STORE_CONNECT_KEY_BASE64` | File (Base64) | App Store Connect API key (.p8) | `base64 -i .secrets/AuthKey_V862XWV7WB.p8 \| pbcopy` |

---

## Automated Secret Upload

The easiest way to upload all secrets is using the automated script:

```bash
# From repository root
make setup-github-secrets

# Or directly:
./scripts/setup-github-secrets.sh
```

### Prerequisites

1. **Install gh CLI**: `brew install gh`
2. **Authenticate**: `gh auth login`
3. **Verify secrets exist**: Ensure all files in `.secrets/` directory

The script will:
- Check that gh CLI is installed and authenticated
- Verify all required files exist
- Upload all 11 secrets to GitHub
- Show progress for each secret

### Manual Upload

If you prefer to upload secrets manually:

```bash
# Android secrets
gh secret set ANDROID_KEYSTORE_BASE64 < <(base64 -i .secrets/my-release-key.jks)
gh secret set ANDROID_KEYSTORE_PASSWORD --body "YOUR_PASSWORD"
# ... etc

# iOS secrets
gh secret set IOS_CERTIFICATE_BASE64 < <(base64 -i .secrets/DeadlyApp_AppStore2.p12)
# ... etc
```

### Verify Secrets

```bash
gh secret list
```

---

## Fastlane for Android

### Location

`androidApp/fastlane/`

### Monorepo Adaptations

The Android fastlane setup has been adapted for the monorepo structure:

**Module Name**: `:app:` (not `:composeApp:`)
```ruby
task: ":app:assembleDebug"  # Our structure
# NOT: ":composeApp:assembleDebug"  # KMM structure
```

**Path Management**: Two separate roots
```ruby
repo_root = File.expand_path("../..", __dir__)      # Monorepo root (for .secrets, version.properties)
project_root = File.expand_path("..", __dir__)      # androidApp/ (for gradlew)

gradle(project_dir: project_root, task: ":app:assembleDebug")
```

**Secrets Path**:
```ruby
"#{repo_root}/.secrets/keystore.properties"  # Two levels up to monorepo root
```

### Available Lanes

#### Build Lanes

**build_debug** - Build debug APK
```bash
cd androidApp
fastlane build_debug

# Or from root:
cd androidApp && fastlane build_debug
```

**build_release** - Build signed release APK
```bash
cd androidApp
fastlane build_release

# Or use Makefile:
make android-build-release
```

**build_bundle** - Build signed App Bundle (AAB)
```bash
cd androidApp
fastlane build_bundle

# Or use Makefile:
make android-build-bundle
```

#### Device Deployment

**deploy_device** - Install debug APK to connected device
```bash
cd androidApp
fastlane deploy_device
```

**deploy_device_release** - Install signed release APK to connected device
```bash
cd androidApp
fastlane deploy_device_release
```

#### Play Store Deployment

**deploy_testing** - Deploy to Play Store Internal Testing
```bash
cd androidApp
fastlane deploy_testing

# Or use Makefile:
make android-deploy-testing
```

**deploy_alpha** - Deploy to Play Store Alpha Testing
```bash
cd androidApp
fastlane deploy_alpha
```

**deploy_beta** - Deploy to Play Store Beta Testing
```bash
cd androidApp
fastlane deploy_beta
```

#### Play Store Promotions

**promote_internal_to_alpha** - Promote latest from Internal to Alpha
```bash
cd androidApp
fastlane promote_internal_to_alpha
```

**promote_alpha_to_beta** - Promote latest from Alpha to Beta
```bash
cd androidApp
fastlane promote_alpha_to_beta
```

**promote_beta_to_production** - Promote latest from Beta to Production
```bash
cd androidApp
fastlane promote_beta_to_production
```

#### Testing

**test** - Run unit tests
```bash
cd androidApp
fastlane test
```

---

## Fastlane for iOS

### Location

`iosApp/fastlane/`

### Monorepo Adaptations

The iOS fastlane setup has been adapted from the Deadly KMM project:

**No KMM Framework Building**: Removed all KMM/Kotlin framework build steps
```ruby
# REMOVED from lanes:
# build_kmm_framework(configuration: "Release", target: "IosArm64")
```

**Version Reading**: Reads from monorepo root
```ruby
version_props = File.read("../../version.properties")
```

**Xcode Project Path**: Adjusted for monorepo
```ruby
xcodeproj_path = File.expand_path("../deadly.xcodeproj", __dir__)
```

### Available Lanes

#### Private Lanes

**update_version_from_properties** - Updates Xcode project version from `version.properties`
```ruby
# Called automatically by build_release and deploy_testflight
# Updates MARKETING_VERSION and CURRENT_PROJECT_VERSION in Xcode project
```

#### Build Lanes

**build_debug** - Build debug app for simulator
```bash
cd iosApp
fastlane build_debug
```

**build_release** - Build signed release IPA
```bash
cd iosApp
fastlane build_release

# Or use Makefile:
make ios-build-release
```

#### Device Deployment

**deploy_device** - Build and install debug app to connected device
```bash
cd iosApp
fastlane deploy_device
```

#### Testing

**test** - Run unit tests
```bash
cd iosApp
fastlane test
```

#### TestFlight Deployment

**deploy_testflight** - Deploy to TestFlight
```bash
cd iosApp
fastlane deploy_testflight

# Or use Makefile:
make ios-deploy-testflight
```

#### Certificate Management

**sync_certs** - Placeholder for certificate syncing
```bash
cd iosApp
fastlane sync_certs
```

This lane is a placeholder. Configure it for your certificate management strategy (manual or match).

---

## GitHub Actions Workflows

### Android Release Workflow

**File**: `.github/workflows/android-release.yml`

**Triggers**:
- Git tags matching `v*` (e.g., `v1.0.0`, `v1.2.3`)

**Steps**:
1. Checkout code
2. Set up JDK 17
3. Set up Ruby and install fastlane
4. Decode keystore from `ANDROID_KEYSTORE_BASE64` secret
5. Create `keystore.properties` from secrets
6. Build signed APK using `fastlane build_release`
7. Build signed AAB using `fastlane build_bundle`
8. Decode Play Store service account JSON
9. **Deploy to Play Store Internal Testing** using `fastlane deploy_testing`
10. Upload APK artifact as backup (30-day retention)
11. Upload AAB artifact as backup (30-day retention)
12. Clean up secret files

**Result**:
- **Automatically available in Play Store Internal Testing track** for your testers
- Backup artifacts available in GitHub Actions

**Artifacts** (backup only):
- `release-apk` - Signed APK file
- `release-aab` - Signed AAB file

### iOS Release Workflow

**File**: `.github/workflows/ios-release.yml`

**Triggers**:
- Git tags matching `v*` (e.g., `v1.0.0`, `v1.2.3`)

**Steps**:
1. Checkout code
2. Set up Ruby and install fastlane + xcodeproj gem
3. Decode P12 certificate from `IOS_CERTIFICATE_BASE64` secret
4. Decode provisioning profile from `IOS_PROVISIONING_PROFILE_BASE64` secret
5. Install provisioning profile to Xcode
6. Create temporary keychain and import certificate
7. Decode App Store Connect API key
8. **Build and deploy to TestFlight** using `fastlane deploy_testflight`
9. Upload IPA artifact as backup (30-day retention)
10. Clean up certificate, profile, API key, and keychain

**Result**:
- **Automatically available in TestFlight** for your testers
- Backup artifact available in GitHub Actions

**Artifacts** (backup only):
- `release-ipa` - Signed IPA file

### Accessing Releases

#### Testing Tracks (Automatic)

After pushing a tag, releases are automatically deployed:

**Android**:
- Go to [Google Play Console](https://play.google.com/console)
- Navigate to **Testing → Internal testing**
- Your release will be available to internal testers within minutes

**iOS**:
- Go to [App Store Connect](https://appstoreconnect.apple.com)
- Navigate to **TestFlight**
- Your build will process and become available to testers (processing takes 5-15 minutes)

#### Downloading Backup Artifacts

If you need the raw build files:

1. Go to **Actions** tab in GitHub
2. Click on the workflow run (triggered by your tag)
3. Scroll to **Artifacts** section
4. Download `release-apk`, `release-aab`, or `release-ipa`

---

## Local Signed Builds

### Android Prerequisites

1. **Keystore exists**: `.secrets/my-release-key.jks`
2. **Properties configured**: `.secrets/keystore.properties` with:
   ```properties
   storeFile=.secrets/my-release-key.jks
   storePassword=YOUR_PASSWORD
   keyAlias=YOUR_ALIAS
   keyPassword=YOUR_PASSWORD
   ```

### Android Build Commands

```bash
# Build signed APK
make android-build-release

# Build signed AAB
make android-build-bundle

# Build and install on connected device
cd androidApp && fastlane deploy_device_release
```

**Output**:
- APK: `androidApp/app/build/outputs/apk/release/app-release.apk`
- AAB: `androidApp/app/build/outputs/bundle/release/app-release.aab`

### iOS Prerequisites

1. **Certificate imported**: P12 certificate in Keychain
   ```bash
   security import .secrets/DeadlyApp_AppStore2.p12 -k ~/Library/Keychains/login.keychain-db
   ```

2. **Provisioning profile installed**:
   ```bash
   cp .secrets/DeadlyApp_AppStore2.mobileprovision \
      ~/Library/Developer/Xcode/UserData/Provisioning\ Profiles/
   ```

3. **iOS device connected** (for device builds) and trusted

### iOS Build Commands

```bash
# Build signed IPA
make ios-build-release

# Build and install on connected device
cd iosApp && fastlane deploy_device
```

**Output**:
- IPA: `iosApp/build/Deadly.ipa`

---

## Complete Release Process

### Step-by-Step Guide

1. **Make commits using conventional format**
   ```bash
   git commit -m "feat: add playlist sharing"
   git commit -m "fix: resolve playback issue"
   ```

2. **Preview the release**
   ```bash
   make release-dry-run
   ```

   Review:
   - What version will be created
   - Which commits will be included
   - Generated changelog content

3. **Create the release**
   ```bash
   make release
   # Or for specific version:
   make release-version VERSION=1.2.3
   ```

   This will:
   - Update `version.properties`
   - Generate `CHANGELOG.md`
   - Commit changes
   - Create git tag `v1.2.3`
   - Push to GitHub

4. **GitHub Actions automatically build AND deploy**

   - Android workflow builds APK/AAB and deploys to Play Store Internal Testing
   - iOS workflow builds IPA and deploys to TestFlight
   - Both upload backup artifacts

5. **Test the releases**

   **Android**:
   - Go to Play Console → Testing → Internal testing
   - Install from Play Store on your test device
   - Verify functionality

   **iOS**:
   - Go to App Store Connect → TestFlight
   - Wait for build processing (5-15 minutes)
   - Install via TestFlight app on your test device
   - Verify functionality

6. **(Optional) Promote to production**

   **Android**:
   ```bash
   # After testing, promote through tracks
   make android-promote-internal-to-alpha  # Or via Play Console
   # Eventually promote to production via Play Console
   ```

   **iOS**:
   - In App Store Connect, submit for App Review from TestFlight
   - Or promote to production via App Store Connect web interface

---

## Troubleshooting

### Android Issues

#### "keystore.properties not found"

**Solution**: Create `.secrets/keystore.properties` with your signing credentials:
```properties
storeFile=.secrets/my-release-key.jks
storePassword=YOUR_PASSWORD
keyAlias=YOUR_ALIAS
keyPassword=YOUR_PASSWORD
```

#### "Keystore was tampered with, or password was incorrect"

**Solution**: Verify passwords in `keystore.properties` match your keystore

#### APK installs but won't run on device

**Solution**: Ensure device is not enforcing Play Protect restrictions for side-loaded apps

#### Fastlane can't find gradle

**Solution**: Ensure you're running from `androidApp/` directory or use Makefile from root

### iOS Issues

#### "No provisioning profile matches"

**Solution**:
1. Check provisioning profile name matches "DeadlyApp_AppStore2"
2. Install profile: `open .secrets/DeadlyApp_AppStore2.mobileprovision`
3. Or copy manually:
   ```bash
   cp .secrets/DeadlyApp_AppStore2.mobileprovision \
      ~/Library/Developer/Xcode/UserData/Provisioning\ Profiles/
   ```
4. Verify profile hasn't expired
5. Check profile UUID matches in Xcode project settings

#### "Code signing identity not found"

**Solution**:
1. Import P12 certificate:
   ```bash
   security import .secrets/DeadlyApp_AppStore2.p12 \
      -k ~/Library/Keychains/login.keychain-db
   ```
2. Verify identity exists:
   ```bash
   security find-identity -v -p codesigning
   ```

#### "No iOS device detected"

**Solution**:
1. Ensure device is connected via USB
2. Trust computer on device if prompted
3. Check device appears: `xcrun xctrace list devices`

#### "xcrun: error: unable to find utility"

**Solution**: Install Xcode Command Line Tools:
```bash
xcode-select --install
```

### GitHub Actions Issues

#### "Decode keystore failed"

**Solution**: Verify base64 encoding has no line breaks:
```bash
# macOS
base64 -i file.jks | pbcopy

# Then paste directly as secret value
```

#### "Certificate import failed"

**Solution**:
1. Ensure P12 password is correct in `IOS_CERTIFICATE_PASSWORD` secret
2. Verify P12 is properly base64 encoded
3. Check certificate hasn't expired

#### iOS build fails with "xcodebuild: error"

**Solution**: Check that:
1. Provisioning profile and certificate match
2. Bundle ID in Xcode matches your provisioning profile
3. Certificate is valid and not expired

#### Workflow doesn't trigger on tag push

**Solution**:
1. Ensure tag was pushed: `git push origin v1.0.0`
2. Tag must match pattern `v*` (e.g., `v1.0.0`, not `1.0.0`)
3. Check GitHub Actions are enabled in repository settings

---

## Security Best Practices

1. **Never commit secrets** - All signing materials stay in `.secrets/` (gitignored)
2. **Rotate secrets regularly** - Update keystores, certificates, and passwords periodically
3. **Use separate keystores** - Different keystores for debug and release builds
4. **Limit secret access** - Only give GitHub secret access to necessary people
5. **Enable 2FA** - Require 2FA on accounts with access to signing credentials

---

## Quick Reference

### Makefile Commands

```bash
# Release management
make release                          # Auto-version release
make release-version VERSION=1.2.3    # Manual version release
make release-dry-run                  # Preview release
make setup-github-secrets             # Upload secrets to GitHub

# Android builds
make android-build-release            # Build APK
make android-build-bundle             # Build AAB
make android-deploy-testing           # Deploy to Play Store

# iOS builds
make ios-build-release                # Build IPA
make ios-deploy-testflight            # Deploy to TestFlight
```

### File Locations

- Version source: `version.properties`
- Release script: `scripts/release.sh`
- Secrets script: `scripts/setup-github-secrets.sh`
- Android fastlane: `androidApp/fastlane/`
- iOS fastlane: `iosApp/fastlane/`
- Android workflows: `.github/workflows/android-release.yml`
- iOS workflows: `.github/workflows/ios-release.yml`

---

**Next Steps:**
- Review [Version Management](version-management.md)
- Set up secrets with `make setup-github-secrets`
- Test local builds with `make release-dry-run`

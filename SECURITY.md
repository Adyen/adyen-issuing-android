## Security Integration Guide

The Adyen Issuing SDKs handle sensitive data and functionality. To ensure your integration meets security best practices, follow the steps below.

### Security Checklist

- [Require User Authentication](#1-require-user-authentication)
- [Prevent Screenshots and Screen Recording](#2-prevent-screenshots-and-screen-recording)
- [Store Credentials Securely](#3-store-credentials-securely)
- [Restrict Backup of Sensitive Data](#4-restrict-backup-of-sensitive-data)
- [Handle Sensitive Data in Memory Carefully](#5-handle-sensitive-data-in-memory-carefully)
- [Use Secure Networking](#6-use-secure-networking)
- [Respond to Tamper Signals](#7-respond-to-tamper-signals)
- [Prevent Tapjacking and Overlay Attacks](#8-prevent-tapjacking-and-overlay-attacks)
- [Verify Release Builds Are Not Debuggable](#9-verify-release-builds-are-not-debuggable)
- [Protect Exported Components](#10-protect-exported-components)
- [Secure WebViews](#11-secure-webviews)
- [ProGuard and R8 Configuration](#12-proguard-and-r8-configuration)

---

<a id="10-require-user-authentication"></a>

### 1. Require User Authentication

Ask the user to confirm their identity before sensitive operations such as adding a card to Google Wallet. For device biometrics or PIN, pattern, or password authentication, you can use [`BiometricPrompt`](https://developer.android.com/identity/sign-in/biometric-auth) from AndroidX Biometric, as shown below, or an equivalent flow provided by another authentication library.

The example below creates the prompt when authentication starts, which keeps it short. A prompt delivers its result to the Activity that created it. If the Activity can be recreated while the prompt is showing, for example after rotation, create the `BiometricPrompt` in `onCreate()` and call only `authenticate()` from your action. Otherwise, the replacement Activity does not receive the result.

```kotlin
fun requireAuthentication(activity: FragmentActivity, onAuthenticated: () -> Unit) {
    // Strong biometrics combined with the device credential is supported from Android 11 (API 30).
    val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
    if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        // No screen lock or enrolled biometric: decide whether to block the operation.
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onAuthenticated()
            }
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm it's you")
            .setAllowedAuthenticators(authenticators)
            .build()
    )
}
```

---

<a id="1-prevent-screenshots-and-screen-recording"></a>

### 2. Prevent Screenshots and Screen Recording

Apply `FLAG_SECURE` to any Activity that displays sensitive SDK screens. This also prevents the screen appearing in the recent apps thumbnail.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
    )
    setContentView(R.layout.activity_sensitive)
}
```

If you are using Jetpack Compose and every screen in the Activity is sensitive, set the flag in `onCreate` as shown above and do not clear it. If only some Compose screens are sensitive, count the screens that need the flag, so one screen leaving cannot clear it while another is still visible:

```kotlin
// Screens that currently need FLAG_SECURE, per window. Compose effects run on the main thread.
private val secureScreenCounts = WeakHashMap<Window, Int>()

@Composable
fun SensitiveScreen() {
    val window = LocalContext.current.findActivity().window
    DisposableEffect(window) {
        // If the Activity set the flag itself, never clear it from here.
        val managed = secureScreenCounts.containsKey(window) ||
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) == 0
        if (managed) {
            secureScreenCounts[window] = (secureScreenCounts[window] ?: 0) + 1
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (managed) {
                val remaining = secureScreenCounts.getValue(window) - 1
                if (remaining == 0) {
                    secureScreenCounts.remove(window)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    secureScreenCounts[window] = remaining
                }
            }
        }
    }
    // your content
}

// LocalContext can be a ContextWrapper (for example, a themed context), so unwrap it.
private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("SensitiveScreen must be hosted in an Activity")
}
```

> **Note:** `FLAG_SECURE` does not prevent all forms of screen capture on rooted devices. For more information, see the [Android documentation on window flags](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE).

---

<a id="11-store-credentials-securely"></a>

### 3. Store Credentials Securely

Do not store session tokens, API keys, or other credentials in plain `SharedPreferences`, files, or databases.

- **Keep Adyen API keys on your backend.** Never ship an Adyen API key in your app.
- **Do not cache session tokens.** The Sessions SDKs ask your `SessionTokenProvider` for a fresh token whenever they need one.
- **Encrypt anything sensitive you must store.** Use a key held in the [Android Keystore](https://developer.android.com/privacy-and-security/keystore), and exclude the file from backup as described in [Restrict Backup of Sensitive Data](#4-restrict-backup-of-sensitive-data).

---

<a id="3-restrict-backup-of-sensitive-data"></a>

### 4. Restrict Backup of Sensitive Data

Android's auto-backup can inadvertently include sensitive SDK data. Disable backup entirely or use backup rules to exclude sensitive files. See the [Android documentation on Backing up app data](https://developer.android.com/guide/topics/data/autobackup) for details.

In your `AndroidManifest.xml`:

```xml
<application
    android:allowBackup="false"
    ... >
```

If you allow backup for other app data, exclude the Sessions SDKs' encrypted DataStore files: `device_id_store`, `os_version_store`, and `signature_store`, all directly under `filesDir` (backup domain `file`). These contain device-bound state and must not be restored onto another device. Add separate exclusions for any sensitive files your application creates; the rules below cover only SDK-managed files.

For Android 11 and lower, use `res/xml/backup_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="file" path="device_id_store" />
    <exclude domain="file" path="os_version_store" />
    <exclude domain="file" path="signature_store" />
</full-backup-content>
```

For Android 12 and higher, exclude the same files from both cloud backup and device transfer in `res/xml/data_extraction_rules.xml`. Some manufacturers allow device transfer even with `allowBackup="false"`, so configure these rules explicitly.

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="device_id_store" />
        <exclude domain="file" path="os_version_store" />
        <exclude domain="file" path="signature_store" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="device_id_store" />
        <exclude domain="file" path="os_version_store" />
        <exclude domain="file" path="signature_store" />
    </device-transfer>
</data-extraction-rules>
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ... >
```

---

<a id="7-handle-sensitive-data-in-memory-carefully"></a>

### 5. Handle Sensitive Data in Memory Carefully

Avoid passing sensitive values (tokens and keys) as `String` objects where possible, as strings are immutable and cannot be zeroed out after use. Prefer `CharArray` or `ByteArray` so you can explicitly clear them when done.


Never log sensitive values, even in debug builds:

```kotlin
// ❌ Never do this
Log.d("SDK", "User token: $token")

// Log an event without token content or token-derived metadata.
Log.d("SDK", "Session token received")
```

---

<a id="12-use-secure-networking"></a>

### 6. Use Secure Networking

Use HTTPS for every call between your app and your backend, including the session token and activation data requests. From Android 9 (API 28), apps block cleartext HTTP by default. Do not re-enable it with `android:usesCleartextTraffic="true"` or a permissive [network security configuration](https://developer.android.com/privacy-and-security/security-config):

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ... >
```

For extra protection against man-in-the-middle attacks on your own backend, consider certificate pinning with a `<pin-set>` in the same configuration. Include a backup pin and set an `expiration`, so a certificate rotation cannot lock users out of your app.

---

<a id="9-respond-to-tamper-signals"></a>

### 7. Respond to Tamper Signals

The Adyen Issuing SDKs perform Runtime Application Self-Protection (RASP) checks at sensitive call sites (see the [Environment Constraints](README.md#environment-constraints) section of the main README). In the Card Provisioning Sessions SDK, when a threat is detected (root, hooking, memory scanning, emulator, virtual environment, compromised hardware, or a debugger attached to a release build), the SDK stops performing sensitive work and returns a `TamperError` result to your code.


The client-side SDK intentionally does not wipe keys or cached state on tamper detection:

* Session private keys are generated in hardware-backed Android Keystore (StrongBox / TEE), which is designed to prevent key export. This does not guarantee that a compromised process cannot invoke a key or observe plaintext around its use.
* Persisted state is encrypted with device-bound Keystore keys and is not portable to another device.
* An attacker capable of bypassing RASP could equally hook out any client-side wipe logic, so "refuse to operate" is a stronger invariant than "try to delete".

Because of this, **server-side session invalidation is your responsibility**. When an SDK call returns a `TamperError` variant, treat it as a cue to terminate the user's session on your backend (logout, session revoke, risk flag) so the session token is refused regardless of what the compromised client does next. Combine this with short-lived session tokens and server-side rate-limiting for defence-in-depth.

Example for the Card Provisioning Sessions SDK:

```kotlin
when (val result = provisioningSession.provisioningState(paymentInstrumentId)) {
    is ProvisioningState.CannotProvision.TamperError -> {
        // Invalidate the user's session on your backend, then surface a
        // neutral "unable to continue" UI. Do not hint at the reason.
        backend.invalidateSession()
        showGenericErrorAndLogOut()
    }
    // … handle other result variants
    else -> { /* … */ }
}
```

The same pattern applies to `ProvisionResult.Failure.TamperError` returned from `provisionCard`.


Do not surface the specific tamper reason to the user — a neutral error message avoids giving attackers feedback on which check triggered.

---

<a id="2-prevent-tapjacking-and-overlay-attacks"></a>

### 8. Prevent Tapjacking and Overlay Attacks

Tapjacking tricks users into tapping UI elements hidden beneath a malicious overlay. Filter out touch events that have been obscured by another window. For more information see the [Android documentation on Tapjacking](https://developer.android.com/privacy-and-security/risks/tapjacking).

Apply this to any View handling sensitive interactions, such as confirmation buttons:

```kotlin
sensitiveButton.setOnTouchListener { view, event ->
    val obscured = MotionEvent.FLAG_WINDOW_IS_OBSCURED or
        MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED // API 29+; safe no-op on earlier devices
    if (event.flags and obscured != 0) {
        // Touch event obscured by an overlay — reject it
        return@setOnTouchListener true
    }
    view.onTouchEvent(event)
}
```

Or apply it globally across your Activity by overriding `dispatchTouchEvent`:

```kotlin
override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    val obscured = MotionEvent.FLAG_WINDOW_IS_OBSCURED or
        MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
    if (event.flags and obscured != 0) {
        return false
    }
    return super.dispatchTouchEvent(event)
}
```

You can also declare this in XML on individual Views:

```xml
<Button
    android:filterTouchesWhenObscured="true"
    ... />
```

---

<a id="4-verify-release-builds-are-not-debuggable"></a>

### 9. Verify Release Builds Are Not Debuggable

Ensure your release build is not debuggable. A debuggable app allows attackers to attach a debugger, inspect memory, and bypass security checks. The Android Gradle Plugin sets `isDebuggable = false` by default for the release build type, but you should verify it is not accidentally overridden or inherited in custom build types.

---

<a id="5-protect-exported-components"></a>

### 10. Protect Exported Components

Ensure Activities, Services, and Receivers that are not intended for external use are not exported. Any exported component is a potential entry point for attack.

```xml
<!-- Unexported — internal use only -->
<activity
    android:name=".SensitiveActivity"
    android:exported="false" />

<!-- If a component must be exported, restrict with a signature-level permission -->
<activity
    android:name=".DeeplinkActivity"
    android:exported="true"
    android:permission="com.yourapp.permission.DEEPLINK" />

<permission
    android:name="com.yourapp.permission.DEEPLINK"
    android:protectionLevel="signature" />
```

---

<a id="6-secure-webviews"></a>

### 11. Secure WebViews

If your integration uses WebViews, apply the following hardening. See the [Android Web App Security documentation](https://developer.android.com/privacy-and-security/security-webview) for a complete overview.

```kotlin
webView.settings.apply {
    javaScriptEnabled = true // only if strictly required
    allowFileAccess = false
    allowContentAccess = false
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
    setSupportMultipleWindows(false)
    javaScriptCanOpenWindowsAutomatically = false
}

// Remove interfaces not in use
webView.removeJavascriptInterface("searchBoxJavaBridge_")
webView.removeJavascriptInterface("accessibility")
webView.removeJavascriptInterface("accessibilityTraversal")

// Restrict navigation to known hosts
webView.webViewClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val host = request.url.host ?: return true
        return if (host.equals("your-trusted-domain.com", ignoreCase = true) || host.endsWith(".your-trusted-domain.com", ignoreCase = true)) {
            false // allow
        } else {
            true // block
        }
    }
}
```

---

<a id="8-proguard-and-r8-configuration"></a>

### 12. ProGuard and R8 Configuration

Ensure minification is enabled in your release builds. The SDK ships with its own consumer ProGuard rules, but verify your setup keeps R8 active:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
    }
}
```

If you are using any SDK models in your own ProGuard rules, only keep rules that are strictly necessary. Overly broad `-keep` rules will undo obfuscation and make reverse engineering easier. For detailed documentation on setting up code shrinking and obfuscation, see the [Android Shrink, obfuscate, and optimize your app documentation](https://developer.android.com/build/shrink-code).

```proguard
# ❌ Too broad — avoid
-keep class com.example.** { *; }

# ✅ Keep only what your code references directly
-keep class com.example.sdk.MySdk { public *; }
```

For advanced security and obfuscation beyond what R8 provides, you may want to consider using a commercial application protection product such as DexGuard, Appdome, or Promon. These tools offer features like string encryption, class encryption, and runtime application self-protection (RASP).

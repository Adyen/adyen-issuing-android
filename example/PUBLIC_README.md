# Example App

This app demonstrates both Adyen Card Provisioning integrations for Google Wallet:

- **[Card Provisioning Sessions](https://adyen.github.io/adyen-issuing-android/AdyenIssuingSdks/latest/network-token-provisioning/provisioning-sessions/)**, recommended for new integrations and selected by default.
- **[Card Provisioning (Offline)](https://adyen.github.io/adyen-issuing-android/AdyenIssuingSdks/latest/network-token-provisioning/provisioning-sdk/)**, for backends that manage the activation-data exchange directly.

The segmented buttons switch between the two integrations.

> **Non-functional reference.** This app shows how to add card provisioning to your
> own app. It uses in-memory fakes and a placeholder certificate, does not connect to
> a backend, and cannot provision cards. Your integration uses your own backend and
> the application certificate Adyen issues for your app.

## Sessions integration

`provisioningsessions/repository/ProvisioningSessionsRepository.kt` demonstrates:

1. Creating a session with an application `Context` and `ProvisioningSessionConfiguration`
   containing DER-encoded certificate bytes, payment-instrument IDs and `SessionEnvironment.Test`.
2. Wrapping each fresh backend token in `SessionToken` inside the `SessionTokenProvider`
   callback. Tokens must not be cached. The SDK retries provider exceptions up to twice;
   a returned token that fails local validation is not retried.
3. Calling `provisioningState(paymentInstrumentId)` and handling `CanProvision`,
   `Provisioned` and `CannotProvision`.
4. Calling `provisionCard(paymentInstrumentId, cardDisplayName, cardAddress)` without
   a per-step OPC callback. The display name is a card label, not the cardholder's name.
5. Handling `TamperError` from either operation by invalidating the user's backend session,
   blocking further operations, and showing neutral UI without the tamper code.

Your app needs to support two backend operations for this flow:

- Fetch a fresh session token on demand, scoped to the requested payment instruments and
  the `Network Token: Create` role. Keep your Adyen API key on the backend, never in
  the app.
- Invalidate or revoke the user's session on your backend when handling a `TamperError`.
  A production backend should make revocation reliable even if the device loses connectivity.

The example groups these operations in a `SessionsBackend` interface with suspend functions,
as one possible coroutine-based implementation. This is application code, not an Adyen SDK
type or a required class or set of method names; structure these backend calls to fit your app.
See [Getting Started](https://adyen.github.io/adyen-issuing-android/AdyenIssuingSdks/latest/network-token-provisioning/provisioning-sessions/essentials/getting-started/)
for token-provider behavior and failure handling.

For the rest of the integration setup:

- Embed the certificate Adyen issues for your app's `applicationId`. The bundled
  `res/raw/fake_app_cert.cer` is a placeholder; `MainActivity` converts it to the DER
  bytes the SDK configuration accepts.
- Match `SessionEnvironment` to your backend's session host. This example explicitly
  uses `Test`; use `Live` for production and `Beta` only when directed by Adyen.

See [Getting Started](https://adyen.github.io/adyen-issuing-android/AdyenIssuingSdks/latest/network-token-provisioning/provisioning-sessions/essentials/getting-started/)
for the session request body, host URLs and complete result-type guidance. Backend
failures can carry an optional `traceId` for support. This example displays generic
messages rather than exposing raw backend errors; production diagnostics should
retain only safe result categories and correlation IDs, never tokens or payloads.

## Direct SDK integration

`provisioningsdk/repository/ProvisioningSdkRepository.kt` demonstrates fetching
activation data from your backend, creating a `CardProvisioning` client, calling
`canProvision()` and then `provision(...)` with an `OpcProvider`.
The provider sends the opaque `sdkOutput` to your backend and returns the matching
opaque card data. Return within 20 seconds and do not retry a single-use `sdkOutput`.
`FakeSdkBackend` illustrates the payload shape but cannot provision a real card.

## Lifecycle and UI

`MainViewModel` holds the repositories across rotation. `ResumedActivityProvider`
keeps only a weak reference to the currently resumed Activity, updated from
`MainActivity.onResume()` and cleared in `onPause()`. Both SDKs resolve the host
on demand rather than retaining the Activity that created the ViewModel.
Provisioning disables repeated taps and mode switching until the operation completes.
Cancellation propagates rather than being shown as an error.

## Build and run

1. Open the project in Android Studio. Use JDK 17 or a newer JDK supported by the
   project's [Gradle version](https://docs.gradle.org/9.5.0/userguide/compatibility.html#java_runtime).
   The Java and Kotlin compilation targets remain Java 17.
2. Build with `./gradlew :example:assembleDebug`.
3. Run on a **physical Android device** with Google Play services. The app requires
   Android 12 (API 31) or later. This minimum applies to the example app; the SDKs
   support Android 8.0 (API 26) or later. Emulators are not supported for SDK operations.

Your own app must also be [allowlisted by Google](https://adyen.github.io/adyen-issuing-android/AdyenIssuingSdks/latest/network-token-provisioning/google-push-provisioning-access/).

From version `0.6.0` onward, `provisioning-sdk` includes Google's Tap and Pay library.
You do not need to download or host that library separately.
The public example resolves `provisioning-sdk` and `provisioning-sessions` from Maven
Central at the coordinated SDK version.

Run example checks with `./gradlew :example:testDebugUnitTest :example:lintDebug`.
The example is illustrative, not a prescribed production architecture. Never log
or hardcode real tokens, activation data or API credentials. Review the
[Security Guide](https://adyen.github.io/adyen-issuing-android/AdyenIssuingSdks/latest/security/security-guide/)
before adapting it to your banking application.

## License

This example app uses the MIT License. See [LICENSE](https://github.com/Adyen/adyen-issuing-android/blob/main/example/LICENSE).

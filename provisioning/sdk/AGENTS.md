# Card Provisioning (Offline) — Agent Integration Guide

Add Adyen-issued payment cards to Google Wallet from an Android app.

## When to use this SDK

Use the **offline provisioning SDK** (`provisioning-sdk`) when your backend already exposes the two `networkTokenActivationData` endpoints and you need step-by-step control over the provisioning flow. For new integrations, prefer the **Card Provisioning Sessions SDK** (`provisioning-sessions`) which has a smaller backend surface area.

## Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.adyen.issuing.provisioning:provisioning-sdk:<version>")
}
```

From release `0.6.0` the published `provisioning-sdk` artifact bundles `com.google.android.gms:play-services-tapandpay` — the app does not need to declare or host it, and can drop a previously declared direct dependency if nothing else in the app uses it. Earlier releases still require the Tap and Pay artifact setup. The app must still be allowlisted by Google for the Push Provisioning API.

## Prerequisites

- `minSdk` 26, `compileSdk` 36+.
- Adyen Configuration API credential with the **Bank Issuing PaymentInstrument Network Token Activation Data** role (used by your backend for the `networkTokenActivationData` calls).
- App ID allowlisted by Google for Push Provisioning API.
- The SDK entry point `CardProvisioning` is annotated `@AdyenExperimentalApi` — add `@OptIn(AdyenExperimentalApi::class)` at your call sites.

## Integration flow (4 steps)

### Step 1 — Fetch activation data from your backend

Your backend calls `GET /paymentInstruments/{id}/networkTokenActivationData` on the Adyen API and returns the opaque `sdkInput` string to the app. The app never parses this value.

### Step 2 — Create a CardProvisioning instance

```kotlin
val result = CardProvisioning.create(
    sdkInput = sdkInput,
    activityProvider = { hostActivity },
)
val cardProvisioning = when (result) {
    is CardProvisioningCreateResult.Success -> result.cardProvisioning
    is CardProvisioningCreateResult.Failed.GooglePayNotSupported -> return // hide button
    is CardProvisioningCreateResult.Failed.InvalidSdkInput -> return       // surface error
}
```

`activityProvider` must return the currently-resumed Activity hosting the card-issuing UI.

### Step 3 — Check eligibility

```kotlin
val canProvision = cardProvisioning.canProvision() == CanProvisionResult.CanBeProvisioned
```

`canProvision()` is a `suspend` function. Re-check if the user revisits the screen.

Handle all `CanProvisionResult` branches:

| Result | Meaning |
|---|---|
| `CanBeProvisioned` | Card is eligible and not already in the wallet. |
| `CannotBeProvisioned.AlreadyExistsInWallet` | Card already added — hide the button. |
| `CannotBeProvisioned.ApiError(statusCode)` | Tap & Pay API error. |
| `CannotBeProvisioned.Error(throwable)` | Exception during check. |
| `CannotBeProvisioned.UnknownFailure` | Failed with no exception. |

### Step 4 — Provision the card

Implement an `OpcProvider` that fetches Opaque Payment Card data from your backend:

```kotlin
val opcProvider = OpcProvider { paymentInstrumentId, sdkOutput ->
    // POST /paymentInstruments/{paymentInstrumentId}/networkTokenActivationData
    // Body: { "sdkOutput": sdkOutput }
    backend.fetchOpc(paymentInstrumentId, sdkOutput)
}
```

`fetchOpc` is a `suspend` function running on a background dispatcher. If it throws, the SDK surfaces the exception as `ProvisionResult.Failure`.

**`fetchOpc` must return within 20 seconds.** The Google Tap & Pay SDK cannot complete its request until your callback returns; after 20 seconds, the Adyen provisioning SDK stops waiting and fails the attempt with `ProvisionResult.Failure.OpcFetchError`. Keep client timeouts inside that budget. Do not retry inside `fetchOpc`: the `sdkOutput` is single-use and re-sending it risks a second network-token activation.

**Prefer an HTTP client that provides `suspend` functions, for example Retrofit or Ktor, over a blocking client.** The SDK cancels the call when the budget expires, and only a suspending client can act on that and abort the request. A blocking call cannot be interrupted: it runs to completion in the background holding a thread, result discarded. Blocking is still supported and the budget is enforced either way — the work just outlives the attempt.

Then call `provision()`:

```kotlin
val result = cardProvisioning.provision(
    cardDisplayName = "Adyen Business Card",
    cardAddress = CardAddress(),   // empty = Google prompts for address
    opcProvider = opcProvider,
)
when (result) {
    is ProvisionResult.Success -> handleSuccess(result.tokenId)
    is ProvisionResult.Failure -> handleFailure(result)
}
```

`provision()` is a `suspend` function and presents Google's native "Add to Wallet" UI. Call it from `lifecycleScope.launch { }`.

## Key types

| Type | Package | Purpose |
|---|---|---|
| `CardProvisioning` | `com.adyen.issuing.mobile.provisioning.client` | Entry point — factory + operations. |
| `CardProvisioningCreateResult` | `com.adyen.issuing.mobile.provisioning.client.results` | Factory result (Success / Failed). |
| `CanProvisionResult` | `com.adyen.issuing.mobile.provisioning.client.results` | Eligibility check result. |
| `ProvisionResult` | `com.adyen.issuing.mobile.provisioning.client.results` | Provision result (Success / Failure.*). |
| `OpcProvider` | `com.adyen.issuing.mobile.provisioning.client.data` | SAM interface for OPC callback. |
| `CardAddress` | `com.adyen.issuing.mobile.provisioning.client.data` | Billing address (all fields nullable). |

## CardAddress fields

All fields are optional (`String?`, default `null`). Pass an empty `CardAddress()` to let Google Wallet prompt the user.

- `addressLine1`, `addressLine2`, `locality`, `administrativeArea`, `countryCode` (ISO 3166), `postalCode`, `phoneNumber`

## ProvisionResult.Failure variants

All failure variants (sealed subtypes of `ProvisionResult.Failure`):

| Variant | Retryable? | Action |
|---|---|---|
| `Canceled` | — | User cancelled; no action needed. |
| `ProvisioningInProgress` | — | Another provisioning or wallet-creation operation is running in the application process, possibly for a different card. Wait for it to complete. |
| `AlreadyProvisioned` | No | Card already in wallet. |
| `OpcFetchError(errorMessage)` | Maybe | OPC callback failed — depends on whether the error message indicates a temporary or permanent error. |
| `NetworkError` | Yes | Retry after connectivity is restored. |
| `Unavailable` | No | App not allowlisted for Tap & Pay API. |
| `InvalidSdkInput(errorMessage)` | No | The sdkInput string is malformed. |
| `AttestationError` | No | Device failed a compatibility check. |
| `ApiNotConnected` | Yes | Could not connect to Google Play services. |
| `ConnectionSuspendedDuringCall` | Yes | Connection dropped mid-call — retry. |
| `DeveloperError` | No | App misconfigured (check setup). |
| `Error` | Maybe | Generic failure with no details. |
| `InternalError` | Maybe | Internal Google Play services error. |
| `Interrupted` | Yes | Blocking call was interrupted — retry. |
| `InvalidAccount` | No | Invalid Google account specified. |
| `ReconnectionTimedOut` | Yes | Timed out reconnecting — retry. |
| `ReconnectionTimedOutDuringUpdate` | Yes | Timed out waiting for Play update. |
| `RemoteException` | Maybe | Non-DeadObject RemoteException. |
| `ResolutionRequired` | — | Resolution step needed (rare). |
| `SignInRequired` | — | User not signed in to Google. |
| `Timeout` | Yes | Operation timed out — retry. |
| `NoActiveWallet` | No | No active wallet on device. |
| `TokenNotFound` | No | Specified token not in wallet. |
| `InvalidTokenState` | No | Token exists but in wrong state. |
| `UnknownStatusCode(statusCode)` | Maybe | Unmapped status code — log and surface. |
| `Other(error)` | Maybe | Catch-all — log and surface. |

> **Note for `when` expressions:** This is a sealed interface with many subtypes. Use an
> `else ->` branch or `is ProvisionResult.Failure ->` catch-all to stay forward-compatible.

## `when` expression guidance

All result types are sealed interfaces. When handling results in `when` expressions:

- **Match the branches you care about explicitly**, then use `else ->` or a broad `is Failure ->` branch for the rest.
- Do **not** attempt exhaustive matching without `else` — new variants may be added in future SDK versions.

```kotlin
when (result) {
    is ProvisionResult.Success -> handleSuccess(result.tokenId)
    is ProvisionResult.Failure.Canceled -> { /* no-op */ }
    is ProvisionResult.Failure.OpcFetchError -> showRetry(result.errorMessage)
    is ProvisionResult.Failure -> logAndShowError(result) // catch-all for other failures
}
```

## Common pitfalls

- **Stale `activityProvider`**: Both `{ this }` and a stored reference capture one Activity. Recreate the SDK client with its host, or use a lifecycle-aware provider that resolves the resumed provisioning host dynamically.
- **`create()` is synchronous**: `CardProvisioning.create()` is **not** a `suspend` function. Only `canProvision()` and `provision()` are `suspend`.
- **Missing Google allowlist**: The app must be allowlisted by Google for the Push Provisioning API, or `provision()` fails with `Unavailable` and `canProvision()` fails with `CannotBeProvisioned.ApiError`.

## Constraints

- **Emulator**: SDK operations return errors. Test on a physical device.
- **One instance per card**: Each `CardProvisioning` instance handles a single payment instrument.
- **Thread safety**: Safe to call from coroutines — `canProvision()` and `provision()` are `suspend`; `create()` is synchronous.

## Backend endpoints required

1. `GET /paymentInstruments/{id}/networkTokenActivationData` → returns `sdkInput`
2. `POST /paymentInstruments/{id}/networkTokenActivationData` with `{ "sdkOutput": "..." }` → returns OPC string

Both are Adyen Issuing API endpoints; your backend proxies them and handles authentication.

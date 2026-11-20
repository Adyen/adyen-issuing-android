# Card Provisioning Sessions — Agent Integration Guide

Add Adyen-issued payment cards to Google Wallet through a session-based integration. The SDK wraps the offline provisioning SDK behind a single `ProvisioningSession` interface; this is the module consumers depend on directly.

**Maven coordinates:** `com.adyen.issuing.provisioning:provisioning-sessions`

## When to use this SDK

Use the **Card Provisioning Sessions SDK** (`provisioning-sessions`) for new integrations. It manages the Adyen back-and-forth behind a session token, reducing your backend to a single session-token endpoint and eliminating the per-step OPC callback. Prefer the **offline provisioning SDK** (`provisioning-sdk`) only if you need step-by-step control over `sdkInput`/`sdkOutput` exchange.

## Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.adyen.issuing.provisioning:provisioning-sessions:<version>")
}
```

The SDK resolves `provisioning-sdk` transitively, and from release `0.6.0` the published `provisioning-sdk` artifact bundles `com.google.android.gms:play-services-tapandpay` — the app does not need to declare or host it, and can drop a previously declared direct dependency if nothing else in the app uses it. Earlier releases still require the Tap and Pay artifact setup. The app must still be allowlisted by Google for the Push Provisioning API. `minSdk` 26, `compileSdk` 36+.

## Module overview

| Package | Responsibility |
|---------|---------------|
| `sessions` | `ProvisioningSession` interface, `ProvisioningSessionConfiguration`, `SessionEnvironment`, `SessionToken`, and `SessionTokenProvider` types |
| `sessions.results` | Sealed result types: `ProvisioningSessionCreateResult`, `ProvisioningState`, `ProvisionResult` |

## Prerequisites

- **Android `Context`** — `ProvisioningSession.create()` requires an application `Context`.
- **Application certificate** — a `.cer` file provided by Adyen, specific to your `applicationId` (embed it in `res/raw/`), supplied to the SDK as DER-encoded bytes (`ByteArray`) via `ProvisioningSessionConfiguration`. The SDK parses it internally using BouncyCastle; bytes that cannot be parsed yield `ProvisioningSessionCreateResult.Failure.ApplicationCertificateError`.
- **App ID allowlisted by Google** for the Push Provisioning API.
- **Adyen API credential** with the **Network Token: Create** role.
- **Experimental opt-in** — the public API is annotated `@AdyenExperimentalApi`; add `@OptIn(AdyenExperimentalApi::class)` at your call sites.

## Public API entry points

```kotlin
ProvisioningSession.create(
    context: Context,
    configuration: ProvisioningSessionConfiguration,
    activityProvider: () -> Activity,
    tokenProvider: SessionTokenProvider
): ProvisioningSessionCreateResult
```

`ProvisioningSessionConfiguration` groups the creation inputs:

```kotlin
ProvisioningSessionConfiguration(
    appCertificate: ByteArray,                       // DER-encoded application certificate bytes
    paymentInstrumentIds: Set<String>,
    environment: SessionEnvironment = SessionEnvironment.Live
)
```

`create()` is synchronous (not a `suspend` function). `activityProvider` is invoked on demand when the SDK needs to present Google's provisioning UI and must return the currently resumed `Activity` hosting that UI — not a captured or finished one.

### `ProvisioningSessionCreateResult` subtypes

| Subtype | Description |
|---------|-------------|
| `Success(val session: ProvisioningSession)` | Session created; use `session` to call the operations |
| `Failure.ApplicationCertificateError` | The `appCertificate` bytes could not be parsed as X.509 |
| `Failure.Error(val message: String?)` | Any other creation error (e.g. internal exception) |

On success, the `ProvisioningSession` exposes two suspend functions:

- `provisioningState(paymentInstrumentId: String)` → `ProvisioningState` — reports whether the card can be added to `Google Wallet`, is already there, or cannot be added
- `provisionCard(paymentInstrumentId: String, cardDisplayName: String, cardAddress: CardAddress)` → `ProvisionResult` — drives Google's native "Add to Wallet" flow

## `tokenProvider` contract

The `tokenProvider` parameter is a `SessionTokenProvider` — a `fun interface` with `suspend fun retrieveToken(paymentInstrumentIds: Set<String>): SessionToken` — that the host app must implement (a lambda works, since the interface is SAM-convertible). It must:

1. Call the host app's backend, which in turn calls the Adyen session authentication endpoint (`POST {sessionHost}/authe/api/v1/sessions`).
2. Return a fresh session token wrapped in a `SessionToken` on every invocation; do not cache or reuse tokens.

If the provider throws, retrieval is retried up to two times (three invocations in total). Returned tokens that fail local validation are not retried.

`SessionToken` is an opaque wrapper: its constructor is public so the host app can wrap the raw token its backend returns, but the wrapped value is internal to the SDK, so client code cannot read it back — keeping the token out of integrator logs and crash reports.

The token must carry the **Network Token: Create** role and cover the payment instruments passed in `ProvisioningSessionConfiguration`. The SDK validates these claims locally before use; a provider that throws or returns an unusable token surfaces as `SessionTokenRetrievalFailed`.

Each `SessionEnvironment` variant implies a corresponding session host that the **integrator's backend** must use when calling the Adyen session endpoint. These hosts are **not part of the `SessionEnvironment` interface** — the SDK never uses them directly. They are listed here for integrator backend reference only:

| `SessionEnvironment` variant | Session host (for integrator backend) |
|------------------------------|---------------------------------------|
| `SessionEnvironment.Live` | `https://authe-live.adyen.com/` |
| `SessionEnvironment.Test` | `https://test.adyen.com/` |
| `SessionEnvironment.Beta` | `https://authe-beta.adyen.com/` |

### `SessionEnvironment` variants

| Variant | Description |
|---------|-------------|
| `SessionEnvironment.Live` | Production (default) |
| `SessionEnvironment.Test` | Test environment |
| `SessionEnvironment.Beta` | Targets a pre-release Adyen backend (`platform-components-external-beta`) for validating upcoming API changes before they reach `Live`. Use it only when directed by Adyen. |

## Result types

### `ProvisioningState`

Returned by `provisioningState(paymentInstrumentId)`:

| Variant | Meaning |
|---|---|
| `CanProvision` | The card can be provisioned — show the Add to Wallet button. |
| `Provisioned` | The card is already in the wallet — a positive state; there is nothing left to do for this card. |
| `CannotProvision.CouldNotEstablishSession(message, traceId?)` | The secure session with the Adyen backend could not be established, for example because the application certificate was rejected. |
| `CannotProvision.SessionTokenRetrievalFailed(message)` | The `SessionTokenProvider` threw an exception or returned an unusable token. |
| `CannotProvision.ActivationDataUnavailable(message, traceId?)` | The card activation data could not be retrieved or read, for example because it failed to decrypt, deserialize, or parse. |
| `CannotProvision.HttpError` — sealed: `Authentication(traceId, message?)` for 401/403, `Validation(traceId, message?)` for 422, `Server` for 500 and other unexpected codes | An HTTP error occurred while communicating with the Adyen backend; `message` contains decrypted backend error text when available. |
| `CannotProvision.ProvisioningFailed(message)` | The wallet could not report whether the card is provisionable, for example a Google Tap and Pay API error. |
| `CannotProvision.WalletNotSupported` | The wallet does not support the card. |
| `CannotProvision.PaymentInstrumentNotAllowed` | The id is not in the set passed to `create()`. |
| `CannotProvision.TamperError(tamperCode)` | A RASP violation was detected — invalidate the session on the backend. |

### `ProvisionResult`

Returned by `provisionCard(...)`:

| Variant | Meaning |
|---|---|
| `Success(tokenId: String?)` | Card provisioned; `tokenId` identifies the newly provisioned card. |
| `Failure.PaymentInstrumentNotAllowed` | The id is not in the session's set. |
| `Failure.AlreadyProvisioned` | Card is already in the wallet. |
| `Failure.ProvisioningCancelled` | The user cancelled Google's provisioning flow. |
| `Failure.ProvisioningAlreadyInProgress` | Another provisioning or wallet-creation operation is running in the application process, possibly for a different card. Wait for it to complete. |
| `Failure.TapAndPayNotAvailable` | The app cannot call the Tap and Pay API (not allowlisted). |
| `Failure.CouldNotEstablishSession(message, traceId?)` | The secure session with the Adyen backend could not be established. |
| `Failure.SessionTokenRetrievalFailed(message)` | The `SessionTokenProvider` threw an exception or returned an unusable token. |
| `Failure.ActivationDataUnavailable(message, traceId?)` | Activation data could not be retrieved or processed. |
| `Failure.HttpError` — sealed: `Authentication(traceId, message?)` for 401/403, `Validation(traceId, message?)` for 422, `Server` for 500 and other unexpected codes | An HTTP error occurred while communicating with the Adyen backend; `message` contains decrypted backend error text when available. |
| `Failure.ProvisioningFailed(message)` | Provisioning failed for an unexpected SDK or wallet reason — the catch-all for Google Play service and tokenization errors. |
| `Failure.TamperError(tamperCode)` | A RASP violation was detected — invalidate the session on the backend. |

### `traceId`

`CouldNotEstablishSession`, `ActivationDataUnavailable`, and `HttpError` carry a `traceId` field: the trace-id segment extracted from the W3C `traceparent` response header of the failed Adyen backend call (e.g. `4bf92f3577b34da6a3ce929d0e0e4736` from `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`). Forward it to Adyen support or your own telemetry so the failure can be correlated with Adyen backend logs. It is `null` when the response carried no valid header (for example an edge-proxy error that never reached the app tier); a malformed header is reported as `null` rather than forwarded as garbage. `HttpError` is a sealed class with three variants: `Authentication` covers HTTP 401 and 403, `Validation` covers 422, and `Server` covers 500 plus any other unexpected non-2xx status. `Authentication` and `Validation` carry an optional decrypted error `message`, or `null` when the encrypted body is unavailable or cannot be decrypted. `Server` derives its `message` from fixed guidance text — "Contact Adyen Support" for HTTP 500, "Contact Adyen Support unexpected response code: {httpCode}" otherwise — and does not expose the HTTP code. `SessionTokenRetrievalFailed`, `PaymentInstrumentNotAllowed`, and `TamperError` deliberately do not carry a `traceId` — token retrieval is client-side and there is no backend response to correlate against.

## `CardAddress` fields

`CardAddress` comes from `com.adyen.issuing.mobile.provisioning.client.data` and is shared unchanged with `provisioning-sdk`. All fields are optional (`String?`, default `null`). Pass an empty `CardAddress()` to let Google Wallet prompt the user.

- `addressLine1`, `addressLine2`, `locality`, `administrativeArea`, `countryCode` (ISO 3166), `postalCode`, `phoneNumber`

## Responding to `TamperError`

When you receive a `TamperError` from `provisioningState()` or `provisionCard()`:

1. **Invalidate the session on your backend** so the token is refused regardless of what the client does next. The SDK halts sensitive work on the client side, but it does not terminate the server-side session — that responsibility is delegated to you.
2. Surface a **neutral error** to the user — do not reveal the tamper reason.
3. The `tamperCode` is a unique 4-digit code identifying the threat type, for Adyen support diagnostics only — not for end-user display.

## `when` expression guidance

All result types are sealed interfaces/classes. Match the branches you care about explicitly, then use a broad `is Failure ->` / `is CannotProvision ->` or `else ->` branch for the rest. Do **not** attempt exhaustive matching without a catch-all — new variants may be added in future SDK versions. Always handle `TamperError` explicitly — it requires backend invalidation.

## Constraints and pitfalls

- **Emulator**: SDK operations return errors. Test on a physical device.
- **One session per card set**: a `ProvisioningSession` covers a `Set<String>` of payment instrument ids.
- **Certificate per `applicationId`**: if your `applicationId` changes, request a new certificate from Adyen — the existing one will not match.
- **`create()` is synchronous**: do not wrap it in `launch {}`. Only `provisioningState()` and `provisionCard()` are `suspend`.
- **Stale `activityProvider`**: both `{ this }` and a stored reference capture one Activity. Recreate the session with its host, or use a lifecycle-aware provider that resolves the resumed provisioning host dynamically.

## Migrating from provisioning-sdk

Step-by-step for upgrading an existing `provisioning-sdk` integration to `provisioning-sessions`. Old-API names below appear only in the "Before" snippets and mapping tables.

### 1. Swap the dependency

```diff
- implementation("com.adyen.issuing.provisioning:provisioning-sdk:<version>")
+ implementation("com.adyen.issuing.provisioning:provisioning-sessions:<version>")
```

### 2. Obtain an application certificate (new requirement)

The sessions SDK requires a `.cer` file provided by Adyen, specific to your `applicationId`. The old SDK did not require this. Request the certificate from Adyen, embed it in `res/raw/`, and load its DER-encoded bytes.

### 3. Replace backend endpoints

| Old (provisioning-sdk) | New (provisioning-sessions) |
|---|---|
| `GET /paymentInstruments/{id}/networkTokenActivationData` → returns `sdkInput` | **Remove** |
| `POST /paymentInstruments/{id}/networkTokenActivationData` with `sdkOutput` → returns OPC | **Remove** |
| *(none)* | `POST {sessionHost}/authe/api/v1/sessions` → returns session token |

The two `networkTokenActivationData` endpoints are replaced by a single session-token endpoint.

### 4. Update imports

All result types move to a new package. `CardAddress` is shared and unchanged.

```diff
- import com.adyen.issuing.mobile.provisioning.client.CardProvisioning
- import com.adyen.issuing.mobile.provisioning.client.results.CardProvisioningCreateResult
- import com.adyen.issuing.mobile.provisioning.client.results.CanProvisionResult
- import com.adyen.issuing.mobile.provisioning.client.results.ProvisionResult
- import com.adyen.issuing.mobile.provisioning.client.data.OpcProvider
+ import com.adyen.issuing.mobile.provisioning.sessions.ProvisioningSession
+ import com.adyen.issuing.mobile.provisioning.sessions.ProvisioningSessionConfiguration
+ import com.adyen.issuing.mobile.provisioning.sessions.SessionEnvironment
+ import com.adyen.issuing.mobile.provisioning.sessions.SessionToken
+ import com.adyen.issuing.mobile.provisioning.sessions.SessionTokenProvider
+ import com.adyen.issuing.mobile.provisioning.sessions.results.ProvisioningSessionCreateResult
+ import com.adyen.issuing.mobile.provisioning.sessions.results.ProvisioningState
+ import com.adyen.issuing.mobile.provisioning.sessions.results.ProvisionResult
```

**Note:** `ProvisionResult` keeps the same simple name but is a different type in a different package with different variants. Do not mix them.

### 5. Replace creation logic

Old — one instance per card, created with `sdkInput`:

```kotlin
val result = CardProvisioning.create(
    sdkInput = sdkInput,
    activityProvider = { hostActivity },
)
```

New — one session per card set, created with a configuration and a token provider:

```kotlin
val result = ProvisioningSession.create(
    context = applicationContext,
    configuration = ProvisioningSessionConfiguration(
        appCertificate = appCertificateBytes,   // DER-encoded bytes
        paymentInstrumentIds = setOf("PI_001", "PI_002"),
    ),
    activityProvider = { hostActivity },
    tokenProvider = { ids -> SessionToken(backend.fetchSessionToken(ids)) },
)
```

### 6. Replace the eligibility check

Old:

```kotlin
val canProvision = cardProvisioning.canProvision() == CanProvisionResult.CanBeProvisioned
```

New — `provisioningState(paymentInstrumentId)` takes the id and reports three top-level states, with `Provisioned` as a positive state rather than a failure:

```kotlin
when (val state = session.provisioningState(paymentInstrumentId)) {
    ProvisioningState.CanProvision -> showAddToWalletButton()
    ProvisioningState.Provisioned -> hideAddToWalletButton()
    is ProvisioningState.CannotProvision -> handleCannotProvision(state)
}
```

### 7. Replace the provisioning call

Old — required an `OpcProvider` callback:

```kotlin
val result = cardProvisioning.provision(
    cardDisplayName = "Adyen Business Card",
    cardAddress = CardAddress(),
    opcProvider = opcProvider,
)
```

New — no `OpcProvider`; the session handles backend communication internally:

```kotlin
val result = session.provisionCard(
    paymentInstrumentId = paymentInstrumentId,
    cardDisplayName = "Adyen Business Card",
    cardAddress = CardAddress(),
)
```

### 8. Handle `TamperError` (new)

The sessions SDK includes RASP tamper detection, which the old SDK did not have. Handle `TamperError` in both `provisioningState()` and `provisionCard()` results — see [Responding to TamperError](#responding-to-tampererror).

### 9. Delete old code

Remove the `OpcProvider` implementation and its backend call, `sdkInput`/`sdkOutput` handling, `createSdkOutput()` calls, and the two `networkTokenActivationData` backend endpoints (once no old SDK consumers remain).

## Complete integration example

Self-contained example showing session creation, token provider, state check, provisioning, and error handling:

```kotlin
@OptIn(AdyenExperimentalApi::class)
suspend fun addToWallet(
    context: Context,
    appCertificateBytes: ByteArray,
    paymentInstrumentId: String,
    environment: SessionEnvironment = SessionEnvironment.Live
) {
    // 1. Create the session
    val createResult = ProvisioningSession.create(
        context = context.applicationContext,
        configuration = ProvisioningSessionConfiguration(
            appCertificate = appCertificateBytes,
            paymentInstrumentIds = setOf(paymentInstrumentId),
            environment = environment
        ),
        activityProvider = { hostActivity }, // must return the currently resumed Activity
        tokenProvider = { ids ->
            // Call your backend, which calls POST {sessionHost}/authe/api/v1/sessions
            // and returns the session token; wrap it in SessionToken.
            SessionToken(backend.fetchSessionToken(ids))
        }
    )

    val session = when (createResult) {
        is ProvisioningSessionCreateResult.Success -> createResult.session
        is ProvisioningSessionCreateResult.Failure.ApplicationCertificateError -> {
            showError("Invalid application certificate")
            return
        }
        is ProvisioningSessionCreateResult.Failure.Error -> {
            showError("Session creation failed: ${createResult.message}")
            return
        }
    }

    // 2. Check the provisioning state
    when (val state = session.provisioningState(paymentInstrumentId)) {
        ProvisioningState.CanProvision -> Unit // proceed to provisioning below
        ProvisioningState.Provisioned -> { showAlreadyInWallet(); return }
        is ProvisioningState.CannotProvision.TamperError -> {
            backend.invalidateSession()
            showGenericErrorAndLogOut()
            return
        }
        is ProvisioningState.CannotProvision -> {
            showCannotProvisionReason(state)
            return
        }
    }

    // 3. Provision the card
    when (val result = session.provisionCard(
        paymentInstrumentId = paymentInstrumentId,
        cardDisplayName = "Adyen Business Card",
        cardAddress = CardAddress()
    )) {
        is ProvisionResult.Success -> showSuccess(result.tokenId)
        is ProvisionResult.Failure.TamperError -> {
            backend.invalidateSession()
            showGenericErrorAndLogOut()
        }
        is ProvisionResult.Failure -> showFailure(result)
    }
}
```

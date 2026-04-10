# Adyen Issuing Android

This is the home page of the Adyen Issuing SDKs.

The repository contains the following SDKs:

- [Adyen Google Wallet Provisioning](#adyen-google-wallet-provisioning)

## Adyen Google Wallet Provisioning

`Adyen Google Wallet Provisioning` Android SDK simplifies integration with Google Wallet.

### Installation

The SDK is available from [Maven Central](https://central.sonatype.com/artifact/com.adyen.issuing.provisioning/provisioning-sdk).

#### Import from Maven Central

1. Import the SDK by adding this line to your `build.gradle` file.
```kotlin
implementation("com.adyen.issuing.provisioning:provisioning-sdk:0.4.1")
```

#### Get the Google Push Provisioning API

See [here](https://adyen.github.io/adyen-issuing-android/0.4.1/Api/#get-the-google-push-provisioning-api) for instructions on how to request and install the `Google Push Provisioning API`.

### Usage

The SDK interaction is performed through calls to an instance of `CardProvisioning`. This instance is provided by a static function of the `CardProvisioning` class:

```kotlin
fun create(
        sdkInput: String,
        activityProvider: () -> Activity,
    ): CardProvisioningCreateResult
```

The `CardProvisioning` class utilises `suspend` functions which return when the requested operation has either completed or failed and return objects which can be queried for the request state:

```kotlin
suspend fun canProvision(): CanProvisionResult
suspend fun provision(cardDisplayName: String, cardAddress: CardAddress, opcProvider: OpcProvider): ProvisionResult
```

#### Creating a provisioning client instance

Perform a `GET` request to endpoint `/paymentInstruments/{paymentInstrumentId}/networkTokenActivationData` from your backend. The response contains `sdkInput` data.

Make a call to the `CardProvisioning.create()` static function passing in the `sdkInput` data and a function which returns an `Activity` (this function should return the `Activity` which hosts your application's card issuing functionality, or the main `Activity` for a single `Activity` application). 

The `create()` function returns a result object which indicates success or failure of the operation. Possible failure reasons are that Google Pay is not supported for the given card or the the `sdkInput` data is invalid and cannot be parsed.
```kotlin
val result = CardProvisioning.create(
    sdkInput = sdkInput,
    activityProvider = { cardIssuingActivity },
)

val cardProvisioning = when(result) {
    is CardProvisioningCreateResult.Success -> result.cardProvisioning
    is CardProvisioningCreateResult.Failure.GooglePayNotSupported -> throw Exception("The card does not support Google Pay")
    is CardProvisioningCreateResult.Failure.InvalidSdkInput -> throw Exception("The sdk input data is invalid")
}
```

#### Checking if a card can be provisioned

Check if the cardholder can add a payment card to their Google Wallet.
```kotlin
val canProvision = cardProvisioning.canProvision() == CanProvisionResult.CanBeProvisioned
```

#### Initiate card provisioning

When the cardholder opts to add a card to Google Wallet, initiate provisioning by performing the following steps:

1. Implement the `OpcProvider` interface. This interface is responsible for fetching the Opaque Payment Card (OPC) data from your backend.
   The `fetchOpc` function will be called by the SDK during the provisioning flow.
   Inside `fetchOpc`, make a `POST` `paymentInstruments/{paymentInstrumentId}/networkTokenActivationData` request from your backend. The body must contain the `sdkOutput` provided by the SDK. The response contains the `sdkInput` object (which is the OPC).

```kotlin
val opcProvider = OpcProvider { paymentInstrumentId, sdkOutput ->
    // Make network request to your backend to get the OPC
    // POST /paymentInstruments/{paymentInstrumentId}/networkTokenActivationData
    // Body: { "sdkOutput": sdkOutput }
    // Return the OPC string from the response
    return@OpcProvider opcString
}
```

2. Make a call to `cardProvisioning.provision()` passing the cardholder name string, an instance of `CardAddress`, and the `opcProvider`:
```kotlin
cardProvisioning.provision(
    cardDisplayName = "John Doe",
    cardAddress = CardAddress(),
    opcProvider = opcProvider
)
```

**Note:** If you provide an empty `CardAddress`, as above, then the user will be prompted to enter their address during the Google Wallet provisioning flow.

For more documentation refer to our [complete documentation](https://docs.adyen.com/issuing/digital-wallets/google-wallet-provisioning/)

## See also

* [Example App](example/README.md)
* [Full Documentation](https://adyen.github.io/adyen-issuing-android/0.4.1/Api/)
* [SDK Reference Adyen Google Pay Provisioning](https://adyen.github.io/adyen-issuing-android/0.4.1/AdyenGoogleWalletProvisioning//)
* [Data security at Adyen](https://docs.adyen.com/development-resources/adyen-data-security)
* [Migrating from SDK versions prior to 0.4.0](https://adyen.github.io/adyen-issuing-android/0.4.1/Api/#migrating-from-sdk-versions-prior-to-040)
* [Troubleshooting](https://adyen.github.io/adyen-issuing-android/0.4.1/Api/#troubleshooting)

## License

This SDK is available under the Apache License, Version 2.0. For more information, see the [LICENSE](https://github.com/Adyen/adyen-issuing-android/blob/main/LICENSE) file.

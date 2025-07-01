# Adyen Issuing Android

This is the home page of the Adyen Issuing SDKs.

The repository contains the following SDKs:

- [Adyen Google Wallet Provisioning](#adyen-google-wallet-provisioning)

## Adyen Google Wallet Provisioning

`Adyen Google Wallet Provisioning` Android SDK simplifies integration with Google Wallet.

### Installation

The SDK is available from [Maven Central](https://central.sonatype.com/artifact/com.adyen.issuing/provisioning).

#### Import from Maven Central

1. Import the SDK by adding this line to your `build.gradle` file.
```kotlin
implementation("com.adyen.issuing:provisioning:0.3.3")
```

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
suspend fun createSdkOutput(): GetSdkOutputResult
suspend fun provision(sdkInput: String, cardDisplayName: String, cardAddress: CardAddress): ProvisionResult
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

1. Make a call to `cardProvisioning.createSdkOuput()` to retrieve the `sdkOuput` string required for step 2. At this point, it is advisable to prevent the user from making further provisioning attempts (e.g. by disabling the `Add to Google Wallet` button) until the provisioning flow completes or is terminated.
2. Make a `POST` `paymentInstruments/{paymentInstrumentId}/networkTokenActivationData` request from your backend to provision the payment instrument. The body must contain the `sdkOuput` obtained from step 1. The response contains the `sdkInput` object.
3. Make a call to `cardProvisioning.provision()` passing the `sdkInput` value plus the cardholder name string and an instance of `CardAddress`:
```kotlin
cardProvisioning.provision(
    sdkInput = sdkInput,
    cardDisplayName = "John Doe",
    cardAddress = CardAddress()
)
```

**Note:** If you provide an empty `CardAddress`, as above, then the user will be prompted to enter their address during the Google Wallet provisioning flow.

For more documentation refer to our [complete documentation](https://docs.adyen.com/issuing/digital-wallets/google-wallet-provisioning/)

## See also

* [Full Documentation](https://adyen.github.io/adyen-issuing-android/0.3.3/Api/)
* [SDK Reference Adyen Google Pay Provisioning](https://adyen.github.io/adyen-issuing-android/0.3.3/AdyenGoogleWalletProvisioning//)
* [Data security at Adyen](https://docs.adyen.com/development-resources/adyen-data-security)

## License

This SDK is available under the Apache License, Version 2.0. For more information, see the [LICENSE](https://github.com/Adyen/adyen-issuing-android/blob/main/LICENSE) file.

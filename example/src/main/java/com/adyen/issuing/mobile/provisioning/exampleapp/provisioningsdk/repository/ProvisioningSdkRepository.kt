package com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsdk.repository

import android.app.Activity
import com.adyen.issuing.mobile.provisioning.client.CardProvisioning
import com.adyen.issuing.mobile.provisioning.client.annotation.AdyenExperimentalApi
import com.adyen.issuing.mobile.provisioning.client.data.CardAddress
import com.adyen.issuing.mobile.provisioning.client.results.CanProvisionResult
import com.adyen.issuing.mobile.provisioning.client.results.CardProvisioningCreateResult
import com.adyen.issuing.mobile.provisioning.client.results.ProvisionResult
import com.adyen.issuing.mobile.provisioning.exampleapp.data.CardState
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsdk.backend.SdkBackend
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsdk.data.CardActivationResult
import com.adyen.issuing.mobile.provisioning.exampleapp.repository.ProvisioningRepository
import kotlinx.coroutines.CancellationException

/**
 * Implementation of the [ProvisioningRepository] interface utilising the provisioning SDK.
 */
@OptIn(AdyenExperimentalApi::class)
class ProvisioningSdkRepository(private val sdkBackend: SdkBackend, private val activityProvider: () -> Activity) :
    ProvisioningRepository {

    private var cardProvisioning: CardProvisioning? = null

    /**
     * Calls the backend to get the card activation data, uses that to initialize a
     * [CardProvisioning] instance and then requests the card state from the SDK.
     */
    override suspend fun getCardState(paymentInstrumentId: String): CardState {
        // Reset on each call so a stale client from a previous successful fetch cannot be
        // reused if this invocation ends on a Failed or Disabled branch.
        cardProvisioning = null
        return try {
            sdkBackend.requestCardActivation(paymentInstrumentId).let { cardActivationResult ->
                when (cardActivationResult) {
                    // Use the activation data to initialize the provisioning SDK.
                    is CardActivationResult.Active -> CardProvisioning.create(
                        cardActivationResult.sdkInput,
                        activityProvider
                    ).let { provisioningCreateResult ->
                        when (provisioningCreateResult) {
                            is CardProvisioningCreateResult.Success -> {
                                cardProvisioning = provisioningCreateResult.cardProvisioning
                                getCardStateFromProvisioningSdk()
                            }

                            is CardProvisioningCreateResult.Failed.GooglePayNotSupported -> CardState.NotSupported
                            is CardProvisioningCreateResult.Failed.InvalidSdkInput -> CardState.Error(
                                message = "Activation data is invalid"
                            )
                        }
                    }

                    CardActivationResult.Disabled -> CardState.Disabled
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CardState.Error(message = "Could not fetch activation data")
        }
    }

    private suspend fun getCardStateFromProvisioningSdk(): CardState = cardProvisioning?.canProvision()?.let {
        when (it) {
            // Card can be provisioned.
            is CanProvisionResult.CanBeProvisioned -> CardState.NotAddedToWallet
            // Card has already been added to the wallet.
            is CanProvisionResult.CannotBeProvisioned.AlreadyExistsInWallet -> CardState.AddedToWallet
            // The Google Tap and Pay API returned an error.
            is CanProvisionResult.CannotBeProvisioned.ApiError -> CardState.Error(
                message = "A Google Tap and Pay API error occurred, Error Code: ${it.statusCode}"
            )
            // The operation failed with an exception.
            is CanProvisionResult.CannotBeProvisioned.Error -> CardState.Error(
                message = "Could not check provisioning eligibility"
            )
            // Something unexpected happened!
            is CanProvisionResult.CannotBeProvisioned.UnknownFailure -> CardState.Error(
                message = "Unknown failure"
            )
        }
    } ?: CardState.Error(message = "Provisioning client not set")

    /**
     * Use the [CardProvisioning] instance created in the [getCardState] method to provision a payment
     * instrument using opaque card data from the backend.
     *
     * [getCardState] must be called before this method to initialize [cardProvisioning].
     */
    override suspend fun provisionCard(
        paymentInstrumentId: String,
        cardDisplayName: String,
        cardAddress: CardAddress
    ): CardState {
        val provisioning = cardProvisioning
            ?: return CardState.Error("Must call getCardState before provisionCard")

        return try {
            when (
                provisioning.provision(
                    cardDisplayName = cardDisplayName,
                    cardAddress = cardAddress,
                    opcProvider = { paymentInstrumentId, sdkOutput ->
                        // This lambda is called by the SDK when it needs the Opaque Payment Card (OPC) data.
                        // You should make a network request to your backend here to fetch the OPC data.
                        // The backend will need the `sdkOutput` string to generate the OPC.
                        //
                        // If the request fails, you should throw an exception to propagate the error.
                        // The SDK will catch the exception and return a failure result.
                        // Return within 20 seconds. Do not retry this single-use sdkOutput.

                        // For this example app, we just return a dummy value since we don't have a real backend.
                        // In a real app, you would return the OPC string here.
                        val opcResponse =
                            sdkBackend.requestOpaquePaymentCardData(paymentInstrumentId, sdkOutput)
                        opcResponse.sdkInput
                    }
                )
            ) {
                is ProvisionResult.Success -> CardState.AddedToWallet
                ProvisionResult.Failure.AlreadyProvisioned -> CardState.AddedToWallet
                ProvisionResult.Failure.Canceled -> CardState.NotAddedToWallet
                is ProvisionResult.Failure -> CardState.Error("Could not provision the card")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CardState.Error(message = "Could not provision the card")
        }
    }
}

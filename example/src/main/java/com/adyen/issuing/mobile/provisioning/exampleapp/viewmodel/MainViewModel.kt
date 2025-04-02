/*
 * Copyright (c) 2025 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by davidw on 8/5/2024.
 */

package com.adyen.issuing.mobile.provisioning.exampleapp.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adyen.issuing.mobile.provisioning.client.CardProvisioning
import com.adyen.issuing.mobile.provisioning.client.annotation.AdyenExperimentalApi
import com.adyen.issuing.mobile.provisioning.client.data.CardAddress
import com.adyen.issuing.mobile.provisioning.client.results.CanProvisionResult
import com.adyen.issuing.mobile.provisioning.client.results.CardProvisioningCreateResult
import com.adyen.issuing.mobile.provisioning.client.results.CreateSdkOutputResult
import com.adyen.issuing.mobile.provisioning.client.results.ProvisionResult
import com.adyen.issuing.mobile.provisioning.exampleapp.backend.Backend
import com.adyen.issuing.mobile.provisioning.exampleapp.data.CardActivationResult
import com.adyen.issuing.mobile.provisioning.exampleapp.data.CardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(AdyenExperimentalApi::class)
class MainViewModel(
    private val paymentInstrumentId: String,
    private val backend: Backend,
    private val activityProvider: () -> Activity,
) : ViewModel() {

    private var cardProvisioning: CardProvisioning? = null

    private val mutableCardState = MutableStateFlow<CardState>(CardState.Loading)
    val cardState: StateFlow<CardState> = mutableCardState.asStateFlow()

    init {
        fetchState()
    }

    /**
     * Fetch the card activation data from the backend and initialize the provisioning SDK with it
     */
    private fun fetchState() {
        viewModelScope.launch {
            // Fetch activation data from the backend for the given payment instrument ID.
            val cardState = backend.requestCardActivation(paymentInstrumentId).let { cardActivationResult ->
                when (cardActivationResult) {
                    // Use the activation data to initialize the provisioning SDK.
                    is CardActivationResult.Active -> CardProvisioning.create(
                        cardActivationResult.sdkInput, activityProvider
                    ).let { provisioningCreateResult ->
                        when (provisioningCreateResult) {
                            is CardProvisioningCreateResult.Success -> {
                                cardProvisioning = provisioningCreateResult.cardProvisioning
                                getCardState()
                            }
                            is CardProvisioningCreateResult.Failed.GooglePayNotSupported -> CardState.NotSupported
                            is CardProvisioningCreateResult.Failed.InvalidSdkInput -> CardState.Error(message = "Activation data is invalid")
                        }
                    }
                    CardActivationResult.Disabled -> CardState.Disabled
                }
            }
            mutableCardState.emit(cardState)
        }
    }

    private suspend fun getCardState(): CardState =
        cardProvisioning?.canProvision()?.let {
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
                    message = it.throwable.message
                )
                // Something unexpected happened!
                is CanProvisionResult.CannotBeProvisioned.UnknownFailure -> CardState.Error(
                    message = "Unknown failure")

            }

        } ?: CardState.Error(message = "Provisioning client not set")

    /**
     * Try to provision the card.
     *
     * This will fail in this example app due to the lack of live backend data which is needed to
     * construct valid Opaque Payment Card data.
     */
    fun provisionCard(
        cardholderName: String = "John Doe",
        cardAddress: CardAddress = CardAddress()
    ) {
            viewModelScope.launch {
                // Disable the `Add to Google Wallet` button while we attempt to provision the card.
                mutableCardState.emit(CardState.Provisioning)
                // Request the SDK Output value from the provisioning SDK.
                cardProvisioning?.createSdkOutput()?.let {
                    val state = when (it) {
                        is CreateSdkOutputResult.Failure -> CardState.Error("Failed to get SDK Output value")
                        // Use the SDK Output value to request the Opaque Payment Card data.
                        is CreateSdkOutputResult.Success -> {
                            val opcResponse = backend.requestOpaquePaymentCardData(
                                paymentInstrumentId,
                                it.sdkOutput
                            )
                            // Use the Opaque Payment Card data to provision the card.
                            cardProvisioning?.provision(
                                opcResponse.sdkInput,
                                cardholderName,
                                cardAddress
                            )
                                ?.let { provisioningResult ->
                                    when (provisioningResult) {
                                        is ProvisionResult.Success -> CardState.AddedToWallet
                                        else -> CardState.Error("Failed to provision card $provisioningResult")
                                    }
                                }
                        }
                    } ?: CardState.Error("Provisioning Failed")
                    mutableCardState.emit(state)
                }
            }
    }
}
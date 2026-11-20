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
import com.adyen.issuing.mobile.provisioning.client.data.CardAddress
import com.adyen.issuing.mobile.provisioning.exampleapp.ResumedActivityProvider
import com.adyen.issuing.mobile.provisioning.exampleapp.data.CardState
import com.adyen.issuing.mobile.provisioning.exampleapp.data.ProvisioningMethod
import com.adyen.issuing.mobile.provisioning.exampleapp.repository.ProvisioningRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val paymentInstrumentId: String,
    private val provisioningSdkRepository: ProvisioningRepository,
    private val provisioningSessionsRepository: ProvisioningRepository,
    private val activityProvider: ResumedActivityProvider
) : ViewModel() {

    private var fetchJob: Job? = null
    private var provisionJob: Job? = null

    private val mutableCardState = MutableStateFlow<CardState>(CardState.Loading)
    val cardState: StateFlow<CardState> = mutableCardState.asStateFlow()

    private val mutableProvisioningMethod = MutableStateFlow(ProvisioningMethod.SESSIONS)
    val provisioningMethod: StateFlow<ProvisioningMethod> = mutableProvisioningMethod.asStateFlow()

    fun onHostResumed(activity: Activity) {
        activityProvider.onResume(activity)
        if (fetchJob == null) fetchState()
    }

    fun onHostPaused(activity: Activity) {
        activityProvider.onPause(activity)
    }

    /**
     * Set the [ProvisioningMethod] which specifies whether the provisioning is performed using the
     * Provisioning SDK or Provisioning Sessions.
     */
    fun onProvisioningMethodSelected(method: ProvisioningMethod) {
        if (method == mutableProvisioningMethod.value || provisionJob?.isActive == true || isSessionInvalidated()) {
            return
        }
        mutableProvisioningMethod.value = method
        mutableCardState.value = CardState.Loading
        fetchState()
    }

    fun retry() {
        if (provisionJob?.isActive == true || isSessionInvalidated()) return
        mutableCardState.value = CardState.Loading
        fetchState()
    }

    // The repository flag is set before backend revocation finishes, while the card state is still Loading.
    // Checking it stops a method switch from cancelling the fetch before SessionInvalidated is published.
    private fun isSessionInvalidated() =
        mutableCardState.value == CardState.SessionInvalidated || provisioningSessionsRepository.isSessionInvalidated

    /**
     * Fetch the card activation data from the backend and initialize the provisioning SDK with it
     */
    private fun fetchState() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
                val cardState = when (mutableProvisioningMethod.value) {
                    ProvisioningMethod.SDK -> provisioningSdkRepository.getCardState(paymentInstrumentId)
                    ProvisioningMethod.SESSIONS -> provisioningSessionsRepository.getCardState(
                        paymentInstrumentId
                    )
                }
                mutableCardState.emit(cardState)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableCardState.emit(CardState.Error("Could not check the card state"))
            }
        }
    }

    /**
     * Try to provision the card.
     *
     * This will fail in this example app due to the lack of live backend data which is needed to
     * construct valid Opaque Payment Card data.
     */
    fun provisionCard(cardDisplayName: String = "Adyen Business Card", cardAddress: CardAddress = CardAddress()) {
        if (
            provisionJob?.isActive == true ||
            mutableCardState.value != CardState.NotAddedToWallet ||
            isSessionInvalidated()
        ) {
            return
        }
        mutableCardState.value = CardState.Provisioning
        provisionJob = viewModelScope.launch {
            try {
                val state = when (mutableProvisioningMethod.value) {
                    ProvisioningMethod.SDK -> provisioningSdkRepository.provisionCard(
                        paymentInstrumentId,
                        cardDisplayName,
                        cardAddress
                    )

                    ProvisioningMethod.SESSIONS -> provisioningSessionsRepository.provisionCard(
                        paymentInstrumentId,
                        cardDisplayName,
                        cardAddress
                    )
                }
                mutableCardState.emit(state)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableCardState.emit(CardState.Error("Could not provision the card"))
            }
        }
    }
}

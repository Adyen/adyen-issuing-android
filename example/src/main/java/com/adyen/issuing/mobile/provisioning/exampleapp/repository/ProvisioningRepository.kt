package com.adyen.issuing.mobile.provisioning.exampleapp.repository

import com.adyen.issuing.mobile.provisioning.client.data.CardAddress
import com.adyen.issuing.mobile.provisioning.exampleapp.data.CardState

interface ProvisioningRepository {
    /**
     * True as soon as a tamper error has invalidated the session, even while backend revocation is still in progress.
     */
    val isSessionInvalidated: Boolean get() = false

    suspend fun getCardState(paymentInstrumentId: String): CardState
    suspend fun provisionCard(paymentInstrumentId: String, cardDisplayName: String, cardAddress: CardAddress): CardState
}

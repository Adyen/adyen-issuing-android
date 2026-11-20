package com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsessions.repository

import android.app.Activity
import android.content.Context
import com.adyen.issuing.mobile.provisioning.client.annotation.AdyenExperimentalApi
import com.adyen.issuing.mobile.provisioning.client.data.CardAddress
import com.adyen.issuing.mobile.provisioning.exampleapp.data.CardState
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsessions.backend.SessionsBackend
import com.adyen.issuing.mobile.provisioning.exampleapp.repository.ProvisioningRepository
import com.adyen.issuing.mobile.provisioning.sessions.ProvisioningSession
import com.adyen.issuing.mobile.provisioning.sessions.ProvisioningSessionConfiguration
import com.adyen.issuing.mobile.provisioning.sessions.SessionEnvironment
import com.adyen.issuing.mobile.provisioning.sessions.SessionToken
import com.adyen.issuing.mobile.provisioning.sessions.results.ProvisionResult
import com.adyen.issuing.mobile.provisioning.sessions.results.ProvisioningSessionCreateResult
import com.adyen.issuing.mobile.provisioning.sessions.results.ProvisioningState
import kotlinx.coroutines.CancellationException

/**
 * Session-based provisioning. The backend owns authentication and returns a fresh token on demand.
 */
@OptIn(AdyenExperimentalApi::class)
class ProvisioningSessionsRepository(
    private val applicationContext: Context,
    private val applicationCertificateBytes: ByteArray,
    private val sessionsBackend: SessionsBackend,
    private val activityProvider: () -> Activity,
    private val environment: SessionEnvironment = SessionEnvironment.Test
) : ProvisioningRepository {
    private var cardProvisioningSession: ProvisioningSession? = null
    private var cachedPaymentInstrumentId: String? = null
    override var isSessionInvalidated = false
        private set

    override suspend fun getCardState(paymentInstrumentId: String): CardState {
        if (isSessionInvalidated) return CardState.SessionInvalidated
        cardProvisioningSession = null
        cachedPaymentInstrumentId = null
        val creationResult = ProvisioningSession.create(
            context = applicationContext,
            configuration = ProvisioningSessionConfiguration(
                appCertificate = applicationCertificateBytes,
                paymentInstrumentIds = setOf(paymentInstrumentId),
                environment = environment
            ),
            activityProvider = activityProvider,
            tokenProvider = { paymentInstrumentIds ->
                // Do not cache tokens. The SDK calls this provider whenever it needs a fresh one.
                SessionToken(sessionsBackend.fetchToken(paymentInstrumentIds))
            }
        )
        val session = when (creationResult) {
            is ProvisioningSessionCreateResult.Success -> creationResult.session
            is ProvisioningSessionCreateResult.Failure.ApplicationCertificateError ->
                return CardState.Error("Invalid application certificate")
            is ProvisioningSessionCreateResult.Failure -> return CardState.Error("Could not create session")
        }
        cardProvisioningSession = session
        cachedPaymentInstrumentId = paymentInstrumentId
        return when (session.provisioningState(paymentInstrumentId)) {
            ProvisioningState.CanProvision -> CardState.NotAddedToWallet
            ProvisioningState.Provisioned -> CardState.AddedToWallet
            ProvisioningState.CannotProvision.WalletNotSupported -> CardState.NotSupported
            is ProvisioningState.CannotProvision.TamperError -> invalidateSession()
            is ProvisioningState.CannotProvision.SessionTokenRetrievalFailed ->
                CardState.Error("Could not retrieve a session token. Configure your Sessions backend.")
            is ProvisioningState.CannotProvision ->
                CardState.Error("Could not check provisioning state. Check your integration or contact support.")
        }
    }

    override suspend fun provisionCard(
        paymentInstrumentId: String,
        cardDisplayName: String,
        cardAddress: CardAddress
    ): CardState {
        if (isSessionInvalidated) return CardState.SessionInvalidated
        val session = cardProvisioningSession
        if (session == null || cachedPaymentInstrumentId != paymentInstrumentId) {
            return CardState.Error("Call getCardState for this payment instrument before provisioning")
        }
        return when (
            session.provisionCard(
                paymentInstrumentId = paymentInstrumentId,
                cardDisplayName = cardDisplayName,
                cardAddress = cardAddress
            )
        ) {
            is ProvisionResult.Success, ProvisionResult.Failure.AlreadyProvisioned -> CardState.AddedToWallet
            ProvisionResult.Failure.ProvisioningCancelled -> CardState.NotAddedToWallet
            ProvisionResult.Failure.ProvisioningAlreadyInProgress ->
                CardState.Error(
                    "Another provisioning or wallet-creation operation is in progress. Wait for it to finish."
                )
            is ProvisionResult.Failure.TamperError -> invalidateSession()
            is ProvisionResult.Failure ->
                CardState.Error("Could not provision the card. Check your integration or contact support.")
        }
    }

    private suspend fun invalidateSession(): CardState {
        isSessionInvalidated = true
        cardProvisioningSession = null
        cachedPaymentInstrumentId = null
        try {
            // Revoke the authenticated user's server-side session, not just the local SDK instance.
            sessionsBackend.invalidateSession()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Remain blocked even if revocation fails. Production backends must arrange reliable revocation.
        }
        return CardState.SessionInvalidated
    }
}

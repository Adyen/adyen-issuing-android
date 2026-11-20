package com.adyen.issuing.mobile.provisioning.exampleapp

import android.app.Activity
import android.content.Context
import com.adyen.issuing.mobile.provisioning.client.annotation.AdyenExperimentalApi
import com.adyen.issuing.mobile.provisioning.client.data.CardAddress
import com.adyen.issuing.mobile.provisioning.exampleapp.data.CardState
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsessions.backend.SessionsBackend
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsessions.repository.ProvisioningSessionsRepository
import com.adyen.issuing.mobile.provisioning.sessions.ProvisioningSession
import com.adyen.issuing.mobile.provisioning.sessions.ProvisioningSessionConfiguration
import com.adyen.issuing.mobile.provisioning.sessions.SessionEnvironment
import com.adyen.issuing.mobile.provisioning.sessions.SessionTokenProvider
import com.adyen.issuing.mobile.provisioning.sessions.results.ProvisionResult
import com.adyen.issuing.mobile.provisioning.sessions.results.ProvisioningSessionCreateResult
import com.adyen.issuing.mobile.provisioning.sessions.results.ProvisioningState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(AdyenExperimentalApi::class)
class ProvisioningSessionsRepositoryTest {
    private val context = mockk<Context>()
    private val activity = mockk<Activity>()
    private val backend = mockk<SessionsBackend>()
    private val session = mockk<ProvisioningSession>()
    private val configuration = slot<ProvisioningSessionConfiguration>()
    private val tokenProvider = slot<SessionTokenProvider>()
    private val address = CardAddress()
    private val certificate = byteArrayOf(1, 2, 3)
    private lateinit var repository: ProvisioningSessionsRepository

    @Before
    fun setUp() {
        mockkObject(ProvisioningSession.Companion)
        every {
            ProvisioningSession.create(eq(context), capture(configuration), any(), capture(tokenProvider))
        } returns ProvisioningSessionCreateResult.Success(session)
        coEvery { session.provisioningState("PI-example") } returns ProvisioningState.CanProvision
        coEvery { backend.invalidateSession() } returns Unit
        repository = ProvisioningSessionsRepository(context, certificate, backend, { activity })
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `creation uses current configuration and retrieves a fresh wrapped token each time`() = runTest {
        coEvery { backend.fetchToken(setOf("PI-example")) } returnsMany listOf("first", "second")

        assertEquals(CardState.NotAddedToWallet, repository.getCardState("PI-example"))
        assertArrayEquals(certificate, configuration.captured.appCertificate)
        assertEquals(setOf("PI-example"), configuration.captured.paymentInstrumentIds)
        assertEquals(SessionEnvironment.Test, configuration.captured.environment)
        assertNotNull(tokenProvider.captured.retrieveToken(setOf("PI-example")))
        assertNotNull(tokenProvider.captured.retrieveToken(setOf("PI-example")))
        coVerify(exactly = 2) { backend.fetchToken(setOf("PI-example")) }
    }

    @Test
    fun `provisioned and unsupported states map to the matching UI state`() = runTest {
        coEvery { session.provisioningState("PI-example") } returns ProvisioningState.Provisioned
        assertEquals(CardState.AddedToWallet, repository.getCardState("PI-example"))
        coEvery { session.provisioningState("PI-example") } returns ProvisioningState.CannotProvision.WalletNotSupported
        assertEquals(CardState.NotSupported, repository.getCardState("PI-example"))
    }

    @Test
    fun `backend error text is not shown to the user`() = runTest {
        coEvery { session.provisioningState("PI-example") } returns
            ProvisioningState.CannotProvision.HttpError.Authentication(null, "private backend detail")
        val state = repository.getCardState("PI-example")
        assertTrue(state is CardState.Error)
        assertTrue(!(state as CardState.Error).message.orEmpty().contains("private backend detail"))
    }

    @Test
    fun `failed creation discards the previous session`() = runTest {
        repository.getCardState("PI-example")
        every { ProvisioningSession.create(any(), any(), any(), any()) } returns
            ProvisioningSessionCreateResult.Failure.ApplicationCertificateError
        assertTrue(repository.getCardState("PI-example") is CardState.Error)
        assertTrue(repository.provisionCard("PI-example", "Business Card", address) is CardState.Error)
        coVerify(exactly = 0) { session.provisionCard(any(), any(), any()) }
    }

    @Test
    fun `provisioning requires the same initialized payment instrument`() = runTest {
        assertTrue(repository.provisionCard("PI-example", "Business Card", address) is CardState.Error)
        repository.getCardState("PI-example")
        assertTrue(repository.provisionCard("other", "Business Card", address) is CardState.Error)
        coVerify(exactly = 0) { session.provisionCard(any(), any(), any()) }
    }

    @Test
    fun `provision success already provisioned and cancellation use current result types`() = runTest {
        repository.getCardState("PI-example")
        coEvery { session.provisionCard("PI-example", "Business Card", address) } returnsMany listOf(
            ProvisionResult.Success("example-token-id"),
            ProvisionResult.Failure.AlreadyProvisioned,
            ProvisionResult.Failure.ProvisioningCancelled
        )
        assertEquals(CardState.AddedToWallet, repository.provisionCard("PI-example", "Business Card", address))
        assertEquals(CardState.AddedToWallet, repository.provisionCard("PI-example", "Business Card", address))
        assertEquals(CardState.NotAddedToWallet, repository.provisionCard("PI-example", "Business Card", address))
    }

    @Test
    fun `state tamper failure revokes backend session and prevents reuse`() = runTest {
        coEvery { session.provisioningState("PI-example") } returns ProvisioningState.CannotProvision.TamperError(1234)
        assertEquals(CardState.SessionInvalidated, repository.getCardState("PI-example"))
        assertEquals(CardState.SessionInvalidated, repository.getCardState("PI-example"))
        assertEquals(CardState.SessionInvalidated, repository.provisionCard("PI-example", "Business Card", address))
        coVerify(exactly = 1) { backend.invalidateSession() }
        coVerify(exactly = 1) { session.provisioningState(any()) }
        coVerify(exactly = 0) { session.provisionCard(any(), any(), any()) }
    }

    @Test
    fun `tamper failure reports invalidation before backend revocation completes`() = runTest {
        val revocation = CompletableDeferred<Unit>()
        coEvery { backend.invalidateSession() } coAnswers { revocation.await() }
        coEvery { session.provisioningState("PI-example") } returns ProvisioningState.CannotProvision.TamperError(1234)
        assertFalse(repository.isSessionInvalidated)
        val state = async { repository.getCardState("PI-example") }
        runCurrent()
        assertTrue(repository.isSessionInvalidated)
        revocation.complete(Unit)
        assertEquals(CardState.SessionInvalidated, state.await())
    }

    @Test
    fun `provision tamper failure remains blocked when backend revocation fails`() = runTest {
        repository.getCardState("PI-example")
        coEvery { session.provisionCard(any(), any(), any()) } returns ProvisionResult.Failure.TamperError(1234)
        coEvery { backend.invalidateSession() } throws IllegalStateException("offline")
        assertEquals(CardState.SessionInvalidated, repository.provisionCard("PI-example", "Business Card", address))
        assertEquals(CardState.SessionInvalidated, repository.getCardState("PI-example"))
        coVerify(exactly = 1) { backend.invalidateSession() }
    }

    @Test
    fun `coroutine cancellation propagates`() = runTest {
        val cancellation = CancellationException("cancelled")
        coEvery { session.provisioningState(any()) } throws cancellation
        try {
            repository.getCardState("PI-example")
            throw AssertionError("Expected cancellation")
        } catch (caught: CancellationException) {
            assertSame(cancellation, caught)
        }
    }
}

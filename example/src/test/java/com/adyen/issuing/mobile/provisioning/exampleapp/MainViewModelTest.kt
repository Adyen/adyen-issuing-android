package com.adyen.issuing.mobile.provisioning.exampleapp

import android.app.Activity
import com.adyen.issuing.mobile.provisioning.exampleapp.data.CardState
import com.adyen.issuing.mobile.provisioning.exampleapp.data.ProvisioningMethod
import com.adyen.issuing.mobile.provisioning.exampleapp.repository.ProvisioningRepository
import com.adyen.issuing.mobile.provisioning.exampleapp.viewmodel.MainViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sdkRepository = mockk<ProvisioningRepository>()
    private val sessionsRepository = mockk<ProvisioningRepository>()
    private val activity = mockk<Activity>()
    private val activityProvider = ResumedActivityProvider()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { sessionsRepository.getCardState("PI-example") } returns CardState.NotAddedToWallet
        coEvery { sdkRepository.getCardState("PI-example") } returns CardState.AddedToWallet
        every { sessionsRepository.isSessionInvalidated } returns false
        every { sdkRepository.isSessionInvalidated } returns false
        viewModel = MainViewModel("PI-example", sdkRepository, sessionsRepository, activityProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sessions is selected by default and checking waits for a resumed Activity`() = runTest {
        runCurrent()
        coVerify(exactly = 0) { sessionsRepository.getCardState(any()) }
        viewModel.onHostResumed(activity)
        runCurrent()
        assertEquals(ProvisioningMethod.SESSIONS, viewModel.provisioningMethod.value)
        assertEquals(CardState.NotAddedToWallet, viewModel.cardState.value)
        coVerify(exactly = 0) { sdkRepository.getCardState(any()) }
    }

    @Test
    fun `rotation replaces the host without retaining or refetching the old Activity`() = runTest {
        viewModel.onHostResumed(activity)
        runCurrent()
        viewModel.onHostPaused(activity)
        val replacement = mockk<Activity>()
        viewModel.onHostResumed(replacement)
        viewModel.onHostPaused(activity)
        assertSame(replacement, activityProvider.requireActivity())
        coVerify(exactly = 1) { sessionsRepository.getCardState(any()) }
    }

    @Test
    fun `duplicate taps mode switching and retry cannot interrupt active provisioning`() = runTest {
        val result = CompletableDeferred<CardState>()
        coEvery { sessionsRepository.provisionCard(any(), any(), any()) } coAnswers { result.await() }
        viewModel.onHostResumed(activity)
        runCurrent()
        viewModel.provisionCard()
        runCurrent()
        assertEquals(CardState.Provisioning, viewModel.cardState.value)
        viewModel.provisionCard()
        viewModel.onProvisioningMethodSelected(ProvisioningMethod.SDK)
        viewModel.retry()
        runCurrent()
        assertEquals(ProvisioningMethod.SESSIONS, viewModel.provisioningMethod.value)
        coVerify(exactly = 1) { sessionsRepository.provisionCard("PI-example", "Adyen Business Card", any()) }
        result.complete(CardState.AddedToWallet)
        runCurrent()
        assertEquals(CardState.AddedToWallet, viewModel.cardState.value)
    }

    @Test
    fun `switching to direct SDK checks state through its repository`() = runTest {
        viewModel.onHostResumed(activity)
        runCurrent()
        viewModel.onProvisioningMethodSelected(ProvisioningMethod.SDK)
        runCurrent()
        assertEquals(CardState.AddedToWallet, viewModel.cardState.value)
        coVerify(exactly = 1) { sdkRepository.getCardState("PI-example") }
    }

    @Test
    fun `invalidated session cannot be retried or bypassed by switching SDKs`() = runTest {
        coEvery { sessionsRepository.getCardState("PI-example") } returns CardState.SessionInvalidated
        viewModel.onHostResumed(activity)
        runCurrent()
        viewModel.retry()
        viewModel.onProvisioningMethodSelected(ProvisioningMethod.SDK)
        viewModel.provisionCard()
        runCurrent()
        assertEquals(CardState.SessionInvalidated, viewModel.cardState.value)
        assertEquals(ProvisioningMethod.SESSIONS, viewModel.provisioningMethod.value)
        coVerify(exactly = 1) { sessionsRepository.getCardState(any()) }
        coVerify(exactly = 0) { sdkRepository.getCardState(any()) }
        coVerify(exactly = 0) { sessionsRepository.provisionCard(any(), any(), any()) }
    }

    @Test
    fun `switching SDKs and retry are blocked while tamper invalidation is still revoking`() = runTest {
        var invalidated = false
        val revocation = CompletableDeferred<Unit>()
        every { sessionsRepository.isSessionInvalidated } answers { invalidated }
        coEvery { sessionsRepository.getCardState("PI-example") } coAnswers {
            invalidated = true
            revocation.await()
            CardState.SessionInvalidated
        }
        viewModel.onHostResumed(activity)
        runCurrent()
        assertEquals(CardState.Loading, viewModel.cardState.value)
        viewModel.onProvisioningMethodSelected(ProvisioningMethod.SDK)
        viewModel.retry()
        runCurrent()
        assertEquals(ProvisioningMethod.SESSIONS, viewModel.provisioningMethod.value)
        coVerify(exactly = 0) { sdkRepository.getCardState(any()) }
        revocation.complete(Unit)
        runCurrent()
        assertEquals(CardState.SessionInvalidated, viewModel.cardState.value)
        coVerify(exactly = 1) { sessionsRepository.getCardState(any()) }
    }
}

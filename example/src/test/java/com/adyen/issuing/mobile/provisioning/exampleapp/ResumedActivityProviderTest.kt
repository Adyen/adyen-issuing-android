package com.adyen.issuing.mobile.provisioning.exampleapp

import android.app.Activity
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test

class ResumedActivityProviderTest {
    @Test(expected = IllegalStateException::class)
    fun `no Activity is available before resume`() {
        ResumedActivityProvider().requireActivity()
    }

    @Test(expected = IllegalStateException::class)
    fun `paused Activity is not returned`() {
        val provider = ResumedActivityProvider()
        val activity = mockk<Activity>()
        provider.onResume(activity)
        provider.onPause(activity)
        provider.requireActivity()
    }

    @Test
    fun `pausing old Activity cannot clear its replacement`() {
        val provider = ResumedActivityProvider()
        val first = mockk<Activity>()
        val second = mockk<Activity>()
        provider.onResume(first)
        provider.onResume(second)
        provider.onPause(first)
        assertSame(second, provider.requireActivity())
    }
}

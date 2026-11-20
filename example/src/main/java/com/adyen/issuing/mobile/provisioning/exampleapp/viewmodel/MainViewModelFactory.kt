/*
 * Copyright (c) 2025 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by davidw on 8/5/2024.
 */

package com.adyen.issuing.mobile.provisioning.exampleapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.adyen.issuing.mobile.provisioning.exampleapp.ResumedActivityProvider
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsdk.backend.SdkBackend
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsdk.fake.FakeSdkBackend
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsdk.repository.ProvisioningSdkRepository
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsessions.backend.SessionsBackend
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsessions.fake.FakeSessionsBackend
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsessions.repository.ProvisioningSessionsRepository

@Suppress("UNCHECKED_CAST")
class MainViewModelFactory(
    private val paymentInstrumentId: String = "PAYMENT_INSTRUMENT_ID",
    private val sdkBackend: SdkBackend = FakeSdkBackend(),
    private val sessionsBackend: SessionsBackend = FakeSessionsBackend(),
    private val applicationContext: Context,
    private val applicationCertificateBytes: ByteArray
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        val activityProvider = ResumedActivityProvider()
        return MainViewModel(
            paymentInstrumentId = paymentInstrumentId,
            activityProvider = activityProvider,
            provisioningSdkRepository = ProvisioningSdkRepository(
                sdkBackend = sdkBackend,
                activityProvider = activityProvider::requireActivity
            ),
            provisioningSessionsRepository = ProvisioningSessionsRepository(
                applicationContext = applicationContext.applicationContext,
                applicationCertificateBytes = applicationCertificateBytes,
                sessionsBackend = sessionsBackend,
                activityProvider = activityProvider::requireActivity
            )
        ) as T
    }
}

/*
 * Copyright (c) 2025 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by davidw on 8/5/2024.
 */

package com.adyen.issuing.mobile.provisioning.exampleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.adyen.issuing.mobile.provisioning.exampleapp.ui.MainScreen
import com.adyen.issuing.mobile.provisioning.exampleapp.ui.theme.ExampleAppTheme
import com.adyen.issuing.mobile.provisioning.exampleapp.viewmodel.MainViewModel
import com.adyen.issuing.mobile.provisioning.exampleapp.viewmodel.MainViewModelFactory
import java.security.cert.CertificateFactory

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        // Normalize the bundled PEM placeholder to the DER bytes required by the Sessions API.
        val certBytes = resources.openRawResource(R.raw.fake_app_cert).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it).encoded
        }
        MainViewModelFactory(
            applicationContext = applicationContext,
            applicationCertificateBytes = certBytes
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.onHostResumed(this)
    }

    override fun onPause() {
        viewModel.onHostPaused(this)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val cardState by viewModel.cardState.collectAsState()
            val provisioningMethod by viewModel.provisioningMethod.collectAsState()
            ExampleAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        card = cardState,
                        // Static last-four matching the hardcoded demo payment instrument.
                        // Replace with a value from the card data API if dynamic card display is needed.
                        lastFour = "8952",
                        onAddToWalletClicked = viewModel::provisionCard,
                        selectedMethod = provisioningMethod,
                        onMethodSelected = viewModel::onProvisioningMethodSelected,
                        onRetryClicked = viewModel::retry
                    )
                }
            }
        }
    }
}

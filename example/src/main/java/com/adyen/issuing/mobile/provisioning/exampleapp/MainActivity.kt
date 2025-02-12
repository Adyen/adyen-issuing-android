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

class MainActivity : ComponentActivity() {

    // Create the MainViewModel and pass in the Activity provider.
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory { this }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val cardState by viewModel.cardState.collectAsState()
            ExampleAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        card = cardState,
                        lastFour = "1234",
                        onAddToWalletClicked = viewModel::provisionCard
                    )
                }
            }
        }
    }
}

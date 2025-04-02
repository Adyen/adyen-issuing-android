/*
 * Copyright (c) 2025 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by davidw on 8/5/2024.
 */

package com.adyen.issuing.mobile.provisioning.exampleapp.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adyen.issuing.mobile.provisioning.exampleapp.R
import com.adyen.issuing.mobile.provisioning.exampleapp.data.CardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    lastFour: String,
    card: CardState,
    onAddToWalletClicked: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.main_screen_title))
                }
            )
        }
    ) { padding ->

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CardDetails(lastFour = lastFour)
                Spacer(Modifier.height(40.dp))
                AdyenCardDisplay(lastFour = lastFour)
                Spacer(Modifier.height(40.dp))
                when (card) {
                    CardState.NotAddedToWallet -> GoogleWalletButton(onClick = onAddToWalletClicked)
                    CardState.Provisioning -> GoogleWalletButton(onClick = {})
                    CardState.AddedToWallet -> CardLabel(R.string.already_added_to_google_pay)
                    CardState.Disabled -> CardLabel(R.string.disabled)
                    CardState.NotSupported -> CardLabel(R.string.does_not_support_google_pay)
                    CardState.Loading -> Loading()
                    is CardState.Error -> CardLabel(card.message ?: "An error occurred")
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CardDetails(
    modifier: Modifier = Modifier,
    lastFour: String
) {
    Column(
        modifier = modifier
            .padding(start = 30.dp)
            .align(Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.manage_card_label),
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = stringResource(R.string.account, lastFour)
        )
    }
}

@Composable
fun CardLabel(label: String) {
    Text(
        text = label,
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp),
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun CardLabel(@StringRes stringResourceId: Int) {
    CardLabel(label = stringResource(id = stringResourceId))
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen(
        lastFour = "1234",
        card = CardState.NotAddedToWallet,
        onAddToWalletClicked = {}
    )
}

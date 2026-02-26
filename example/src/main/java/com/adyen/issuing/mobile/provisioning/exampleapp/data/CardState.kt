/*
 * Copyright (c) 2025 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by davidw on 8/5/2024.
 */

package com.adyen.issuing.mobile.provisioning.exampleapp.data

/**
 * Represents the state of the card in relation to the Google Wallet.
 */
sealed interface CardState {
    /**
     * The card state is being loaded.
     */
    data object Loading : CardState

    /**
     * The card is not added to the Google Wallet and can be provisioned.
     */
    data object NotAddedToWallet : CardState

    /**
     * The card is currently being provisioned.
     */
    data object Provisioning: CardState

    /**
     * The card has already been added to the Google Wallet.
     */
    data object AddedToWallet : CardState

    /**
     * Google Pay is not supported for this card.
     */
    data object NotSupported : CardState

    /**
     * The card is disabled and cannot be provisioned.
     */
    data object Disabled : CardState

    /**
     * An error occurred while checking the card state or provisioning the card.
     */
    data class Error(val message: String?) : CardState
}
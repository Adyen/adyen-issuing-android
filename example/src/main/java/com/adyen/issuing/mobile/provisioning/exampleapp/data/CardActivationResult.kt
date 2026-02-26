/*
 * Copyright (c) 2025 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by davidw on 8/5/2024.
 */

package com.adyen.issuing.mobile.provisioning.exampleapp.data

/**
 * Result of the card activation request to the backend.
 */
sealed interface CardActivationResult {
    /**
     * The card is active and can be provisioned.
     * @param sdkInput The activation data required to initialize the provisioning SDK.
     */
    data class Active(val sdkInput: String) : CardActivationResult

    /**
     * The card is disabled or cannot be provisioned.
     */
    data object Disabled : CardActivationResult
}
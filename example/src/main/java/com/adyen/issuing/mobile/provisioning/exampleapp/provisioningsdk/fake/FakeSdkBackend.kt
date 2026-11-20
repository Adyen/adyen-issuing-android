/*
 * Copyright (c) 2025 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by davidw on 8/5/2024.
 */

package com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsdk.fake

import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsdk.backend.SdkBackend
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsdk.data.CardActivationResult
import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsdk.data.OpaqueCardDataResponse

/**
 * Mock backend implementation for demonstration purposes.
 * In a real application, this would make network requests to your backend server.
 */
class FakeSdkBackend : SdkBackend {

    /**
     * Request card activation data (`sdkInput`) by making a **GET** request to
     * `paymentInstruments/$[paymentInstrumentId]/networkTokenActivationData` endpoint.
     */
    override suspend fun requestCardActivation(paymentInstrumentId: String): CardActivationResult =
        CardActivationResult.Active(dummySdkInput)

    /**
     * Request opaque payment card data by making **POST** request to
     * `paymentInstruments/$[paymentInstrumentId]/networkTokenActivationData` endpoint and passing
     * [sdkOutput] in the body.
     *
     * Both [sdkOutput] and the returned value are opaque to your application - pass them straight
     * through to and from your backend.
     */
    override suspend fun requestOpaquePaymentCardData(
        paymentInstrumentId: String,
        sdkOutput: String
    ): OpaqueCardDataResponse = OpaqueCardDataResponse(DUMMY_OPAQUE_CARD_DATA)

    companion object {
        // This is a dummy base64 encoded JSON string representing the activation data.
        // In a real scenario, this would be returned by your backend.
        private val dummySdkInput: String =
            "eyJwYXltZW50SW5zdHJ1bWVudElkIjoiUEFZTUVOVF9JTlNUUlVNRU5UX0lEIiwiY2FyZGhvb" +
                "GRlck5hbWUiOiJKb2hubnkgQXBwbGVzZWVkIiwiYnJhbmQiOiJ2aXNhIiwibGFzdEZvdXIiOiI4OTUyIiwic3" +
                "VwcG9ydGVkV2FsbGV0cyI6WyJnb29nbGVQYXkiLCJhcHBsZVBheSJdfQ=="

        // This is a dummy base64 encoded JSON string representing the two opaque payment cards
        // returned under Unified Push Provisioning: the TSP card in `opaquePaymentCardData` and
        // Google's in `opaqueGoogleCardData`. In a real scenario, this would be returned by your
        // backend, and the payload would be encrypted rather than plain base64 - which is why this
        // value only illustrates the field names and cannot actually be provisioned.
        private const val DUMMY_OPAQUE_CARD_DATA: String =
            "eyJvcGFxdWVQYXltZW50Q2FyZERhdGEiOiJaSFZ0YlhsVWMzQlBjR009IiwidG9rZW5TZXJ2" +
                "aWNlUHJvdmlkZXIiOiJUT0tFTl9QUk9WSURFUl9WSVNBIiwibmV0d29ya1Byb3ZpZGVyIjoi" +
                "Q0FSRF9ORVRXT1JLX1ZJU0EiLCJvcGFxdWVHb29nbGVDYXJkRGF0YSI6IlpIVnRiWGxIYjI5" +
                "bmJHVlBjR009In0="
    }
}

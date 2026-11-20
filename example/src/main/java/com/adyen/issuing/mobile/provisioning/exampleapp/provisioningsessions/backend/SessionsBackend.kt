package com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsessions.backend

interface SessionsBackend {
    /** Obtain a fresh token from an authenticated backend endpoint. Never embed an Adyen API key in the app. */
    suspend fun fetchToken(paymentInstrumentIds: Set<String>): String

    /** Invalidate the user's session on your backend after a TamperError. */
    suspend fun invalidateSession()
}

package com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsessions.fake

import com.adyen.issuing.mobile.provisioning.exampleapp.provisioningsessions.backend.SessionsBackend

/** No live credentials or tokens are shipped with the example. Replace this with your own backend. */
class FakeSessionsBackend : SessionsBackend {
    override suspend fun fetchToken(paymentInstrumentIds: Set<String>): String =
        error("Configure an authenticated Sessions backend to obtain a fresh session token")

    override suspend fun invalidateSession() {
        // No server-side session exists in this fake. A real implementation must revoke it on the backend.
    }
}

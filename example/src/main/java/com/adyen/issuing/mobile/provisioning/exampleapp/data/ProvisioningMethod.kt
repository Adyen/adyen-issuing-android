package com.adyen.issuing.mobile.provisioning.exampleapp.data

import com.adyen.issuing.mobile.provisioning.exampleapp.R

enum class ProvisioningMethod(val labelResource: Int) {
    SDK(R.string.sdk),
    SESSIONS(R.string.sessions)
}

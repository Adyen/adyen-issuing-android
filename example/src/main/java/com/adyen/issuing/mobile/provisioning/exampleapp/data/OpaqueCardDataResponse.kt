/*
 * Copyright (c) 2025 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by davidw on 8/5/2024.
 */

package com.adyen.issuing.mobile.provisioning.exampleapp.data

/**
 * Response from the backend containing the Opaque Payment Card (OPC) data.
 * @param sdkInput The Base64 encoded OPC string.
 */
data class OpaqueCardDataResponse(val sdkInput: String)
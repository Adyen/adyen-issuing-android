/*
 * Copyright (c) 2025 Adyen N.V.
 *
 * This file is open source and available under the MIT license. See the LICENSE file for more info.
 *
 * Created by davidw on 8/5/2024.
 */

package com.adyen.issuing.mobile.provisioning.exampleapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adyen.issuing.mobile.provisioning.exampleapp.R

@Composable
internal fun AdyenCardDisplay(
    modifier: Modifier = Modifier,
    lastFour: String?,
    disabled: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    val isLightTheme = !isSystemInDarkTheme()
    val borderColor = if (isLightTheme) Color.Black else Color.White.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .shadow(elevation = 18.dp, shape),
    ) {
        val colorFilter = if (disabled) {
            ColorFilter.colorMatrix(grayScaleMatrix)
        } else {
            null
        }

        Image(
            modifier = Modifier
                .clip(shape)
                .border(
                    BorderStroke(1.dp, borderColor),
                    shape,
                ),

            painter = painterResource(id = R.drawable.adyen_mastercard),
            contentDescription = null,
            colorFilter = colorFilter,
        )
        Text(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            text = "•••• •••• •••• $lastFour",
            color = Color.White,
            fontSize = 16.sp,
        )
    }
}

@Preview
@Composable
internal fun AdyenCardDisplayPreview() {
    AdyenCardDisplay(Modifier, "6239")
}

private val grayScaleMatrix = ColorMatrix(
    floatArrayOf(
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package br.ufg.akcit.smartglasses.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A single onboarding tip: a leading icon next to a title and a short description. */
@Composable
fun TipRow(
    iconResId: Int,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.fillMaxWidth()) {
    Icon(
        painter = painterResource(id = iconResId),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).width(24.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
          text = title,
          fontSize = 20.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
          text = text,
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
      )
    }
  }
}

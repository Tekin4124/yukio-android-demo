package com.example.test.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.test.ai.MatchResult
import com.example.test.data.isFirstRunTestOffer
import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.local.displayTonnage
import com.example.test.ui.format.displayRoute
import com.example.test.ui.format.inferredVehicleDisplayText
import com.example.test.ui.format.vehicleDisplayText

@OptIn(ExperimentalLayoutApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun LoadCard(
    load: LoadOfferEntity,
    matchResult: MatchResult?,
    parserSource: String? = null,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val priceText = load.priceTry?.let { "%,d ₺".format(it) }
    val tonnageText = load.displayTonnage()
    val vehicleText = vehicleDisplayText(load.vehicleType, load.vehicleCanonical)
        ?: inferredVehicleDisplayText(load.vehicleInferredCandidates)
        ?: "Araç belirtilmemiş"
    val routeText = load.displayRoute()
    val isTestOffer = load.isFirstRunTestOffer()
    val isFresh = System.currentTimeMillis() - load.createdAt <= 30 * 60 * 1000L
    val cardShape = RoundedCornerShape(12.dp)
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = if (isTestOffer) {
                    "Test ilanı. $routeText güzergâhı"
                } else {
                    "$routeText güzergâhlı yük ilanı"
                }
            },
        shape = cardShape,
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (isFresh) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (isFresh) 11.dp else 14.dp,
                        top = 12.dp,
                        end = 14.dp,
                        bottom = 12.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isTestOffer) {
                        TestOfferBadge()
                    }
                    if (load.sourceGroup == com.example.test.data.remote.SHARED_LISTING_SOURCE) {
                        Text("DEMO İLANI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = routeText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmartTag(label = vehicleText)
                        SmartTag(label = tonnageText)
                        if (priceText != null) {
                            PriceTag(label = priceText)
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = relativeLoadTime(load.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                    CallButton(
                        enabled = false,
                        onClick = onCallClick
                    )
                }
            }
        }
    }
}

@Composable
private fun TestOfferBadge() {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
        )
    ) {
        Text(
            text = "TEST İLANI",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1
        )
    }
}

@Composable
private fun SmartTag(label: String) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Text(
            text = label,
            modifier = Modifier
                .heightIn(min = 24.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

@Composable
private fun PriceTag(label: String) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
    ) {
        Text(
            text = label,
            modifier = Modifier
                .heightIn(min = 24.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false
        )
    }
}

@Composable
private fun CallButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(76.dp)
            .height(40.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "DEMO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

private fun relativeLoadTime(createdAt: Long): String {
    val diffMs = (System.currentTimeMillis() - createdAt).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    return when {
        minutes < 1 -> "şimdi"
        minutes < 60 -> "$minutes dk önce"
        minutes < 24 * 60 -> "${minutes / 60} sa önce"
        minutes < 48 * 60 -> "dün"
        else -> "${minutes / (24 * 60)} gün önce"
    }
}

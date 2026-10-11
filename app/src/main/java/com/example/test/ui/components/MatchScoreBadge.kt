package com.example.test.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.test.ai.MatchResult
import com.example.test.ai.RiskLevel
import com.example.test.ui.theme.AppError
import com.example.test.ui.theme.AppAccent
import com.example.test.ui.theme.AppOnAccent
import com.example.test.ui.theme.AppSuccess
import com.example.test.ui.theme.AppWarning

@Composable
fun MatchScoreBadge(
    matchResult: MatchResult,
    modifier: Modifier = Modifier
) {
    val badgeColor = matchBadgeColor(matchResult.grade)
    val darkText = matchResult.grade in listOf("B+", "B", "C")
    val contentColor = if (darkText) AppOnAccent else Color.White
    val highRisk = matchResult.riskLevel == RiskLevel.HIGH || matchResult.riskLevel == RiskLevel.CRITICAL

    Surface(
        modifier = modifier.defaultMinSize(minHeight = 24.dp),
        shape = RoundedCornerShape(8.dp),
        color = badgeColor,
        contentColor = contentColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = matchResult.grade,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            if (highRisk) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Risk uyarısı",
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

fun matchBadgeColor(grade: String): Color = when (grade) {
    "A+", "A" -> AppSuccess
    "B+", "B" -> AppAccent
    "C" -> AppWarning
    else -> AppError
}

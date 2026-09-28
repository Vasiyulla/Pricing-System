package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.Category
import com.example.ui.theme.spacing

@Composable
fun CategoryPillStrip(
    categories: List<Category>,
    selectedCategoryId: String,
    onCategorySelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = MaterialTheme.spacing.spaceMd)
    ) {
        categories.forEachIndexed { index, category ->
            val isSelected = category.id == selectedCategoryId

            val (backgroundColor, textColor, borderModifier) = if (isSelected) {
                Triple(
                    MaterialTheme.colorScheme.onSurface,
                    MaterialTheme.colorScheme.surfaceBright,
                    Modifier
                )
            } else {
                Triple(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.colorScheme.onSurface,
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(borderModifier)
                    .background(backgroundColor, CircleShape)
                    .clickable { onCategorySelect(category.id) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("category_pill_${category.id}")
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor
                )
            }

            if (index < categories.size - 1) {
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.spaceSm))
            }
        }
    }
}

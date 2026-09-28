package com.example.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PriceBridgeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // sm: 0.25rem
    small = RoundedCornerShape(8.dp),      // DEFAULT/md: 0.5rem
    medium = RoundedCornerShape(12.dp),    // lg/xl: 0.75rem
    large = RoundedCornerShape(16.dp),     // 2xl: 1rem
    extraLarge = CircleShape               // full: 9999px (pills, badges, floating FAB)
)

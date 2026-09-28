package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.spacing

enum class BottomBarDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Home("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    Search("search", R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
    Scan("scan", R.string.nav_scan, Icons.Filled.QrCodeScanner, Icons.Filled.QrCodeScanner),
    Saved("saved", R.string.nav_saved, Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    Profile("profile", R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.PersonOutline)
}

@Composable
fun PriceBridgeBottomBar(
    currentDestination: BottomBarDestination,
    onDestinationClick: (BottomBarDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home
                NavDestinationItem(
                    destination = BottomBarDestination.Home,
                    isSelected = currentDestination == BottomBarDestination.Home,
                    onClick = { onDestinationClick(BottomBarDestination.Home) },
                    modifier = Modifier.weight(1f)
                )

                // Search
                NavDestinationItem(
                    destination = BottomBarDestination.Search,
                    isSelected = currentDestination == BottomBarDestination.Search,
                    onClick = { onDestinationClick(BottomBarDestination.Search) },
                    modifier = Modifier.weight(1f)
                )

                // Center placeholder for floating scan button
                Spacer(modifier = Modifier.weight(1f))

                // Saved
                NavDestinationItem(
                    destination = BottomBarDestination.Saved,
                    isSelected = currentDestination == BottomBarDestination.Saved,
                    onClick = { onDestinationClick(BottomBarDestination.Saved) },
                    modifier = Modifier.weight(1f)
                )

                // Profile
                NavDestinationItem(
                    destination = BottomBarDestination.Profile,
                    isSelected = currentDestination == BottomBarDestination.Profile,
                    onClick = { onDestinationClick(BottomBarDestination.Profile) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Prominent Floating Scan Button in Center
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(y = (-14).dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onDestinationClick(BottomBarDestination.Scan) }
                )
                .testTag("bottom_nav_scan_fab")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .shadow(8.dp, CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        CircleShape
                    )
                    .border(
                        4.dp,
                        MaterialTheme.colorScheme.surface,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = stringResource(R.string.nav_scan),
                    tint = MaterialTheme.colorScheme.surfaceBright,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = stringResource(R.string.nav_scan),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun NavDestinationItem(
    destination: BottomBarDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(50.dp)
            .clickable(onClick = onClick)
            .testTag("nav_item_${destination.route}")
    ) {
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = stringResource(destination.labelRes),
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

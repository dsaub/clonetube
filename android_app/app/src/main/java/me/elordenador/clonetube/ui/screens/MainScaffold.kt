package me.elordenador.clonetube.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elordenador.clonetube.ui.components.Avatar
import me.elordenador.clonetube.ui.components.HeaderDivider
import me.elordenador.clonetube.ui.components.IconButtonBox
import me.elordenador.clonetube.ui.components.IconHome
import me.elordenador.clonetube.ui.components.IconPerson
import me.elordenador.clonetube.ui.components.IconSearch
import me.elordenador.clonetube.ui.components.IconSubscriptions
import me.elordenador.clonetube.ui.components.IconUpload
import me.elordenador.clonetube.ui.components.IconVideos
import me.elordenador.clonetube.ui.components.IconPlay
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.state.Tab
import me.elordenador.clonetube.ui.theme.currentPalette

@Composable
fun MainScaffold(state: ClonetubeAppState) {
    Column(Modifier.fillMaxSize()) {
        TopBar(state)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
        ) {
            when (state.tab) {
                Tab.HOME -> HomeTab(state)
                Tab.SUBS -> SubsTab(state)
                Tab.STUDIO -> StudioTab(state)
                Tab.YOU -> YouTab(state)
            }
        }
        BottomNav(state)
    }
}

@Composable
private fun TopBar(state: ClonetubeAppState) {
    val p = currentPalette()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Brand(state.tab, Modifier.weight(1f))
            when (state.tab) {
                Tab.HOME -> SearchButton(state)
                Tab.STUDIO -> QuickUploadButton(state)
                else -> {}
            }
            ProfileButton(state)
        }
        HeaderDivider()
    }
}

/** Clonetube brand mark + wordmark, with the STUDIO eyebrow on the studio tab. */
@Composable
private fun Brand(tab: Tab, modifier: Modifier = Modifier) {
    val p = currentPalette()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(p.accent),
            contentAlignment = Alignment.Center,
        ) {
            IconPlay(size = 16.dp)
        }
        if (tab == Tab.STUDIO) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    "Clonetube",
                    color = p.textBright,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "STUDIO",
                    color = p.accentText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp,
                )
            }
        } else {
            Text(
                "Clonetube",
                color = p.textBright,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp,
            )
        }
    }
}

@Composable
private fun SearchButton(state: ClonetubeAppState) {
    val p = currentPalette()
    IconButtonBox(onClick = { state.searchOpen = !state.searchOpen }, size = 42.dp) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.surfaceAlt)
                .border(1.dp, p.borderAlt, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            IconSearch(p.accentIcon, size = 20.dp)
        }
    }
}

@Composable
private fun QuickUploadButton(state: ClonetubeAppState) {
    val p = currentPalette()
    IconButtonBox(onClick = state::openUpload, size = 42.dp) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(p.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            IconUpload(p.accentIcon, size = 22.dp)
        }
    }
}

@Composable
private fun ProfileButton(state: ClonetubeAppState) {
    val p = currentPalette()
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (state.loggedIn) p.avatar else Color.Transparent)
            .border(1.dp, if (state.loggedIn) p.border else p.borderAlt, CircleShape)
            .clickable(onClick = state::openAccount),
        contentAlignment = Alignment.Center,
    ) {
        if (state.loggedIn) {
            Text(
                text = state.accountInitial,
                color = p.accentIcon,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        } else {
            IconPerson(p.textMuted, size = 20.dp)
        }
    }
}

/** Search row shown under the header while the search button is toggled on. */
@Composable
fun SearchRow(state: ClonetubeAppState, modifier: Modifier = Modifier) {
    val p = currentPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surfaceAlt)
            .border(1.dp, p.borderAlt, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconSearch(p.accentIcon, size = 18.dp)
        BasicTextField(
            value = state.searchQuery,
            onValueChange = { state.searchQuery = it },
            singleLine = true,
            textStyle = TextStyle(color = p.text, fontSize = 14.sp),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.searchQuery.isEmpty()) {
                        Text("Buscar en Clonetube", color = p.textMuted, fontSize = 14.sp)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun BottomNav(state: ClonetubeAppState) {
    val p = currentPalette()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(p.navPill)
                .border(1.dp, p.divider, RoundedCornerShape(30.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NavItem(
                label = "Inicio",
                selected = state.tab == Tab.HOME,
                onClick = { state.selectTab(Tab.HOME) },
                modifier = Modifier.weight(1f),
            ) { IconHome(it, size = 21.dp) }
            NavItem(
                label = "Siguiendo",
                selected = state.tab == Tab.SUBS,
                onClick = { state.selectTab(Tab.SUBS) },
                modifier = Modifier.weight(1f),
            ) { IconSubscriptions(it, size = 21.dp) }
            NavItem(
                label = "Studio",
                selected = state.tab == Tab.STUDIO,
                onClick = { state.selectTab(Tab.STUDIO) },
                modifier = Modifier.weight(1f),
            ) { IconVideos(it, size = 21.dp) }
            NavItem(
                label = "Tú",
                selected = state.tab == Tab.YOU,
                onClick = { state.selectTab(Tab.YOU) },
                modifier = Modifier.weight(1f),
            ) { IconPerson(it, size = 21.dp) }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit,
) {
    val p = currentPalette()
    val tint = if (selected) p.accentIcon else p.textMuted
    Column(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) p.selectedTab else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon(tint)
        Text(
            label,
            color = if (selected) p.accentLabel else p.textMuted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/** Shared by the subscriptions rail and other small circular channel chips. */
@Composable
internal fun ChannelAvatar(initial: String) = Avatar(initial, 48.dp, 16.sp)

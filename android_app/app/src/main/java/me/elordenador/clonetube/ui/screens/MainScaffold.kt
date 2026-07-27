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
import me.elordenador.clonetube.ui.components.IconButtonBox
import me.elordenador.clonetube.ui.components.IconPerson
import me.elordenador.clonetube.ui.components.IconPlus
import me.elordenador.clonetube.ui.components.IconSearch
import me.elordenador.clonetube.ui.components.IconSubscriptions
import me.elordenador.clonetube.ui.components.IconVideos
import me.elordenador.clonetube.ui.state.ClonetubeAppState
import me.elordenador.clonetube.ui.state.Tab
import me.elordenador.clonetube.ui.theme.Accent
import me.elordenador.clonetube.ui.theme.Accent100
import me.elordenador.clonetube.ui.theme.Accent800
import me.elordenador.clonetube.ui.theme.DividerColor
import me.elordenador.clonetube.ui.theme.Neutral500
import me.elordenador.clonetube.ui.theme.SurfaceColor
import me.elordenador.clonetube.ui.theme.TextColor

@Composable
fun MainScaffold(state: ClonetubeAppState) {
    Column(Modifier.fillMaxSize()) {
        TopBar(state)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 16.dp),
        ) {
            when (state.tab) {
                Tab.HOME -> HomeTab(state)
                Tab.SUBS -> SubsTab(state)
                Tab.YOU -> YouTab(state)
            }
        }
        BottomNav(state)
    }
}

@Composable
private fun TopBar(state: ClonetubeAppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state.tab) {
            Tab.HOME -> SearchBox(state, Modifier.weight(1f))
            Tab.SUBS -> TopBarTitle("Seguidores", Modifier.weight(1f))
            Tab.YOU -> TopBarTitle("Tu cuenta", Modifier.weight(1f))
        }

        IconButtonBox(onClick = state::openUpload, size = 36.dp) {
            IconPlus(TextColor)
        }

        // Account button: the user's initial once signed in, a person glyph otherwise.
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (state.loggedIn) Accent800 else Color.Transparent)
                .border(1.dp, DividerColor, CircleShape)
                .clickable(onClick = state::openAccount),
            contentAlignment = Alignment.Center,
        ) {
            if (state.loggedIn) {
                Text(
                    text = state.accountInitial,
                    color = Accent100,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                IconPerson(TextColor)
            }
        }
    }
}

@Composable
private fun TopBarTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = TextColor,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

@Composable
private fun SearchBox(state: ClonetubeAppState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(SurfaceColor)
            .border(1.dp, DividerColor, CircleShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconSearch(Neutral500)
        BasicTextField(
            value = state.searchQuery,
            onValueChange = { state.searchQuery = it },
            singleLine = true,
            textStyle = TextStyle(color = TextColor, fontSize = 13.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.searchQuery.isEmpty()) {
                        Text("Buscar", color = Neutral500, fontSize = 13.sp)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun BottomNav(state: ClonetubeAppState) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerColor)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 8.dp),
        ) {
            NavItem(
                label = "Videos",
                selected = state.tab == Tab.HOME,
                onClick = { state.selectTab(Tab.HOME) },
                modifier = Modifier.weight(1f),
            ) { IconVideos(it) }
            NavItem(
                label = "Seguidores",
                selected = state.tab == Tab.SUBS,
                onClick = { state.selectTab(Tab.SUBS) },
                modifier = Modifier.weight(1f),
            ) { IconSubscriptions(it) }
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
    val tint = if (selected) Accent else Neutral500
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        icon(tint)
        Text(label, color = tint, fontSize = 11.sp)
    }
}

/** Shared by the subscriptions rail and other small circular channel chips. */
@Composable
internal fun ChannelAvatar(initial: String) = Avatar(initial, 48.dp, 16.sp)

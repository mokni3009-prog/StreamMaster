package com.streammaster.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.BuiltInKeyboard
import com.streammaster.tv.ui.components.KeyboardAction
import com.streammaster.tv.ui.components.KeyboardLanguage
import com.streammaster.tv.ui.components.KeyboardMode
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.BorderGold
import com.streammaster.tv.ui.theme.BorderStrong
import com.streammaster.tv.ui.theme.GoldBright
import com.streammaster.tv.ui.theme.GoldDeep
import com.streammaster.tv.ui.theme.GoldGradient
import com.streammaster.tv.ui.theme.GoldSoft
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.uiScale
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.theme.tvFocus
import com.streammaster.tv.ui.viewmodel.LoginTab
import com.streammaster.tv.ui.viewmodel.LoginViewModel
import com.streammaster.tv.ui.components.GoldButton

private enum class LoginField { SERVER, USERNAME, PASSWORD, PORTAL, MAC, M3U, NAME }

/** Model .login-box background: linear-gradient(165deg, rgba(32,23,7,.96), rgba(12,9,3,.98)) — diagonal approximation via the stops overload (start top-left, end bottom-right). */
private val LoginBoxGradient: Brush = Brush.linearGradient(
    0.0f to Color(0xF5201707),
    1.0f to Color(0xFA0C0903)
)

/**
 * Login — tabs Xtream / Stalker / M3U (CONTRACT §6).
 * Model .login-box: 800dp wide, padding 44×56dp, 36dp radius, gradient,
 * 2px strong border, shadow + gold glow. Header: 88dp logo + STREAM MASTER
 * Georgia 40sp + subtitle + version line. Tabs are local .login-tab
 * (56dp, 16dp radius, 2px border, 20sp ExtraBold; active = gold gradient).
 * Fields: label above the field (18sp gold-bright + Feather icon) +
 * ReadOnlyField (60dp, 16dp radius, 2px border, 21sp).
 * Stalker: portal URL + MAC validated as 6 hex pairs (in the ViewModel).
 * M3U: link field. All fields are read-only; the built-in keyboard
 * opens on OK over a field. On success -> Loading.
 * L08 (R-M4): while the keyboard is open the box lifts by -150dp, like
 * the model's translateY(-150px), so the keyboard sheet below the box
 * does not cover it.
 */
@Composable
fun LoginScreen(
    navController: NavHostController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var keyboardField by remember { mutableStateOf<LoginField?>(null) }
    var keyboardLang by remember { mutableStateOf(KeyboardLanguage.FR) }
    var keyboard123 by remember { mutableStateOf(false) }
    // R-M4: lift the login box by -150dp while the keyboard is open
    // (model translateY(-150px)), animated.
    // Small screens (2026-11-23, approved by the user): the box is taller
    // than a 720-design-dp canvas, so the full -150dp lift pushes the logo
    // fully off-screen (box top -203dp on 640x360). Cap the lift so the
    // logo (44dp top padding + 88dp) always stays on-screen:
    // |lift| <= centeredTop + 44dp, where centeredTop = (screenH - boxH)/2
    // from the box height measured at runtime (onSizeChanged below), so no
    // hardcoded content-height estimate is needed. At the 1080p reference
    // the model -150dp lift applies exactly, as before.
    val density = LocalDensity.current
    var boxHeightDp by remember { mutableStateOf(826.dp) }
    val screenHeightDesignDp =
        LocalConfiguration.current.screenHeightDp / uiScale()
    val centeredTopDp =
        maxOf(0f, (screenHeightDesignDp - boxHeightDp.value) / 2f)
    val maxLiftDp = centeredTopDp + 44f
    val liftMagnitudeDp = if (screenHeightDesignDp >= 1080f) 150f
    else minOf(150f, maxLiftDp)
    val boxLift by animateDpAsState(
        targetValue = if (keyboardField != null) -liftMagnitudeDp.dp else 0.dp,
        label = "loginBoxLift"
    )
    // Initial focus: the playlist-name field (first field, above the tabs).
    val firstFieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFieldFocus.requestFocus() }
    // Return focus after the keyboard closes: the focused KbKey is disposed
    // with the keyboard, so without this the D-pad is left with no focus.
    var lastEditedField by remember { mutableStateOf<LoginField?>(null) }
    val fieldReturnFocus = remember { FocusRequester() }
    LaunchedEffect(keyboardField) {
        if (keyboardField == null && lastEditedField != null) fieldReturnFocus.requestFocus()
    }
    fun openKeyboard(field: LoginField) {
        keyboardField = field
        lastEditedField = field
    }
    fun fieldModifier(field: LoginField): Modifier {
        var m: Modifier = Modifier
        if (field == LoginField.NAME) m = m.focusRequester(firstFieldFocus)
        if (field == lastEditedField) m = m.focusRequester(fieldReturnFocus)
        return m
    }
    // Back closes the keyboard first, instead of leaving the screen.
    BackHandler(enabled = keyboardField != null) { keyboardField = null }

    LaunchedEffect(state.success) {
        if (state.success) {
            navController.navigate(Routes.LOADING) { popUpTo(Routes.LOGIN) { inclusive = true } }
        }
    }

    fun fieldValue(field: LoginField): String = when (field) {
        LoginField.SERVER -> state.serverUrl
        LoginField.USERNAME -> state.username
        LoginField.PASSWORD -> state.password
        LoginField.PORTAL -> state.portalUrl
        LoginField.MAC -> state.mac
        LoginField.M3U -> state.m3uUrl
        LoginField.NAME -> state.playlistName
    }
    fun setField(field: LoginField, value: String) {
        // Typing clears a previous login error (the inline error box below).
        viewModel.clearError()
        when (field) {
            LoginField.SERVER -> viewModel.setServerUrl(value)
            LoginField.USERNAME -> viewModel.setUsername(value)
            LoginField.PASSWORD -> viewModel.setPassword(value)
            LoginField.PORTAL -> viewModel.setPortalUrl(value)
            LoginField.MAC -> viewModel.setMac(value)
            LoginField.M3U -> viewModel.setM3uUrl(value)
            LoginField.NAME -> viewModel.setPlaylistName(value)
        }
    }

    ThemedScreen {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 800.dp).fillMaxWidth()
                    .offset(y = boxLift)
                    .shadow(
                        35.dp,
                        RoundedCornerShape(36.dp),
                        ambientColor = Color.Black,
                        spotColor = Color(0xFFE5C158)
                    )
                    .background(LoginBoxGradient, RoundedCornerShape(36.dp))
                    .border(2.dp, BorderStrong, RoundedCornerShape(36.dp))
                    // UI scaling (2026-11-23): the card is ~826dp tall, so at
                    // scale 0.5 it is ~413dp — taller than a 360dp canvas.
                    // It scrolls internally there; at the reference it fits
                    // and nothing scrolls.
                    .verticalScroll(rememberScrollState())
                    // Measure the laid-out box height (min(content, screen))
                    // so the keyboard lift cap above uses the real height.
                    .onSizeChanged { size ->
                        with(density) { boxHeightDp = size.height.toDp() }
                    }
                    .padding(horizontal = 56.dp, vertical = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Header: 88dp logo + STREAM MASTER 40sp + subtitle + version.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(88.dp)
                    )
                    Text(
                        text = stringResource(R.string.app_name).uppercase(),
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        fontSize = 40.sp,
                        letterSpacing = 3.sp,
                        color = GoldWhite
                    )
                    Text(
                        text = stringResource(R.string.login_subtitle),
                        fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = GoldSoft
                    )
                    Text(
                        text = stringResource(R.string.login_version_line),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp, color = GoldSoft
                    )
                }

                // Playlist name FIRST (prototype: the name field sits above the tabs).
                LabeledField(
                    label = stringResource(R.string.field_playlist_name),
                    value = state.playlistName,
                    iconRes = R.drawable.ic_folder,
                    modifier = fieldModifier(LoginField.NAME)
                ) { openKeyboard(LoginField.NAME) }

                // Tabs — local .login-tab style (NOT SortPill).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        LoginTab.XTREAM to R.string.login_xtream,
                        LoginTab.STALKER to R.string.login_stalker,
                        LoginTab.M3U to R.string.login_m3u
                    ).forEach { (tab, label) ->
                        LoginTab(
                            text = stringResource(label),
                            selected = state.tab == tab,
                            modifier = Modifier.weight(1f)
                        ) { viewModel.setTab(tab) }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    when (state.tab) {
                        LoginTab.XTREAM -> {
                            LabeledField(
                                label = stringResource(R.string.field_server),
                                value = state.serverUrl,
                                iconRes = R.drawable.ic_globe,
                                modifier = fieldModifier(LoginField.SERVER)
                            ) { openKeyboard(LoginField.SERVER) }
                            LabeledField(
                                label = stringResource(R.string.field_username),
                                value = state.username,
                                iconRes = R.drawable.ic_user,
                                modifier = fieldModifier(LoginField.USERNAME)
                            ) { openKeyboard(LoginField.USERNAME) }
                            LabeledField(
                                label = stringResource(R.string.field_password),
                                value = "•".repeat(state.password.length),
                                iconRes = R.drawable.ic_lock,
                                modifier = fieldModifier(LoginField.PASSWORD)
                            ) { openKeyboard(LoginField.PASSWORD) }
                        }
                        LoginTab.STALKER -> {
                            LabeledField(
                                label = stringResource(R.string.field_portal_url),
                                value = state.portalUrl,
                                iconRes = R.drawable.ic_link,
                                modifier = fieldModifier(LoginField.PORTAL)
                            ) { openKeyboard(LoginField.PORTAL) }
                            LabeledField(
                                label = stringResource(R.string.field_mac),
                                value = state.mac,
                                iconRes = R.drawable.ic_wifi,
                                modifier = fieldModifier(LoginField.MAC)
                            ) { openKeyboard(LoginField.MAC) }
                        }
                        LoginTab.M3U -> {
                            LabeledField(
                                label = stringResource(R.string.field_m3u_url),
                                value = state.m3uUrl,
                                iconRes = R.drawable.ic_link,
                                modifier = fieldModifier(LoginField.M3U)
                            ) { openKeyboard(LoginField.M3U) }
                        }
                    }
                    // Inline error box (model .login-error): ic_alert + bold
                    // 19sp title + 17sp message, rgba(255,70,45,.14)
                    // background, 2px #ff6a4d border.
                    if (state.errorRes != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x24FF462D))
                                .border(2.dp, Color(0xFFFF6A4D), RoundedCornerShape(16.dp))
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_alert),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = Color(0xFFFF8A70)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = stringResource(R.string.login_error_title),
                                    fontSize = 19.sp, fontWeight = FontWeight.Black,
                                    color = Color(0xFFFFD9CF)
                                )
                                Text(
                                    text = stringResource(state.errorRes!!),
                                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB9A8)
                                )
                            }
                        }
                    }
                    GoldButton(
                        text = (if (state.loading) stringResource(R.string.loading_title) else stringResource(R.string.login_button)).uppercase(),
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) { viewModel.submit() }
                }
            }
        }

        // Built-in keyboard sheet (components agent) over the target field.
        // C-06: onClearAll clears the current field (the parameter exists
        // in BuiltInKeyboard; the ✕ key shows only when it is passed).
        keyboardField?.let { field ->
            Box(
                modifier = Modifier.fillMaxSize().padding(48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // S-01 — the echo above the keyboard masked the
                    // password like the field itself (was plain text).
                    Text(
                        text = if (field == LoginField.PASSWORD) "•".repeat(fieldValue(field).length)
                        else fieldValue(field),
                        color = GoldWhite, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp)
                    )
                    BuiltInKeyboard(
                        onKey = { c -> setField(field, fieldValue(field) + c) },
                        onAction = { action ->
                            when (action) {
                                is KeyboardAction.Char -> Unit // already handled via onKey
                                KeyboardAction.Backspace -> setField(field, fieldValue(field).dropLast(1))
                                KeyboardAction.Space -> setField(field, fieldValue(field) + " ")
                                KeyboardAction.Shift -> { /* layout handles case internally */ }
                                KeyboardAction.SwitchLanguage -> keyboardLang =
                                    if (keyboardLang == KeyboardLanguage.FR) KeyboardLanguage.AR else KeyboardLanguage.FR
                                KeyboardAction.Switch123 -> keyboard123 = !keyboard123
                                KeyboardAction.Validate -> keyboardField = null
                                KeyboardAction.Close -> keyboardField = null
                            }
                        },
                        language = keyboardLang,
                        mode = if (keyboard123) KeyboardMode.SYMBOLS else KeyboardMode.LETTERS,
                        onClearAll = { setField(field, "") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                        GoldButton(text = stringResource(R.string.validate)) { keyboardField = null }
                        GhostButton(text = stringResource(R.string.back)) { keyboardField = null }
                    }
                }
            }
        }
    }
}

/** Model .login-tab: 56dp high, 16dp radius, 2px border, 20sp ExtraBold; active = gold gradient. */
@Composable
private fun LoginTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .then(
                if (selected) Modifier.shadow(
                    8.dp,
                    RoundedCornerShape(16.dp),
                    ambientColor = Color.Black,
                    spotColor = Color(0xFFE5C158)
                ) else Modifier
            )
            .then(
                if (selected) Modifier.background(GoldGradient, RoundedCornerShape(16.dp))
                else Modifier.background(Color(0xEB080602), RoundedCornerShape(16.dp))
            )
            .border(
                2.dp,
                if (selected) Color(0xFFFFF6CC) else BorderGold,
                RoundedCornerShape(16.dp)
            )
            .tvFocus()
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) GoldDeep else GoldSoft
        )
    }
}

/**
 * Model .field-group: label above the field (18sp ExtraBold gold-bright
 * + Feather icon) over the 60dp ReadOnlyField.
 */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = GoldBright
            )
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GoldBright
            )
        }
        // The passed modifier (e.g. the initial-focus requester) goes on
        // the field itself, which is the focusable element.
        ReadOnlyField(label, value, modifier) { onClick() }
    }
}

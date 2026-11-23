package com.streammaster.tv.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.components.CardSurface
import com.streammaster.tv.ui.components.GoldButton
import com.streammaster.tv.ui.components.LoadingIndicator
import com.streammaster.tv.ui.components.SectionHeader
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.GoldWhite
import com.streammaster.tv.ui.theme.TextMuted
import com.streammaster.tv.ui.viewmodel.UsbFilesViewModel
import kotlinx.coroutines.launch

/** Route for the USB files screen (§4.52). Registered by the nav builder. */
const val USB_FILES_ROUTE = "usb_files"

/** USB local files (§4.52): pick a folder or a video file, play it with the same player. */
@Composable
fun UsbFilesScreen(
    navController: NavHostController,
    viewModel: UsbFilesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) viewModel.listFolder(uri) }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.pickSingleFile(uri) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionHeader(stringResource(R.string.ns_usb_title), stringResource(R.string.ns_usb_subtitle))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoldButton(text = stringResource(R.string.ns_usb_choose_folder), modifier = Modifier.weight(1f)) {
                folderLauncher.launch(null)
            }
            GoldButton(
                text = stringResource(R.string.ns_usb_choose_file),
                outlined = true,
                modifier = Modifier.weight(1f)
            ) { fileLauncher.launch(arrayOf("video/*")) }
        }
        if (state.folderName.isNotBlank()) {
            Text(
                text = stringResource(R.string.ns_usb_folder, state.folderName),
                fontSize = 19.sp, fontWeight = FontWeight.Bold, color = GoldWhite
            )
        }
        if (state.loading) {
            LoadingIndicator()
            return@Column
        }
        if (state.files.isEmpty()) {
            Text(text = stringResource(R.string.ns_usb_empty), fontSize = 18.sp, color = TextMuted)
            return@Column
        }
        Text(
            text = stringResource(R.string.ns_usb_count, state.files.size),
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GoldWhite
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(state.files) { file ->
                CardSurface(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = file.name,
                            fontSize = 19.sp, fontWeight = FontWeight.Bold, color = GoldWhite,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 16.dp)
                        )
                        GhostButton(text = stringResource(R.string.ns_usb_play)) {
                            scope.launch {
                                val id = viewModel.preparePlayback(file)
                                if (id != null) navController.navigate(Routes.player("recording", id))
                            }
                        }
                    }
                }
            }
        }
    }
}

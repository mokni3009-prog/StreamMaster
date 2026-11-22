package com.streammaster.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.streammaster.tv.R
import com.streammaster.tv.ui.navigation.Routes
import com.streammaster.tv.ui.theme.ThemedScreen
import com.streammaster.tv.ui.viewmodel.LoadingViewModel
import kotlinx.coroutines.delay
import com.streammaster.tv.ui.components.LoadingIndicator

/** Loading — sync animation, then home once content is ready. */
@Composable
fun LoadingScreen(
    navController: NavHostController,
    viewModel: LoadingViewModel = hiltViewModel()
) {
    val isReady by viewModel.isReady.collectAsState()
    LaunchedEffect(isReady) {
        if (isReady) {
            delay(600)
            navController.navigate(Routes.HOME) { popUpTo(Routes.LOADING) { inclusive = true } }
        }
    }
    ThemedScreen {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LoadingIndicator(stringResource(R.string.loading_sync))
        }
    }
}

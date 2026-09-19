package com.yousef.facebooky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yousef.facebooky.call.CallPhase
import com.yousef.facebooky.ui.ChatViewModel
import com.yousef.facebooky.ui.call.CallScreen
import com.yousef.facebooky.ui.chat.ChatScreen
import com.yousef.facebooky.ui.sheets.ProfileSheet
import com.yousef.facebooky.ui.theme.FaceBookyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaceBookyTheme {
                AppRoot(viewModel())
            }
        }
    }
}

@Composable
private fun AppRoot(vm: ChatViewModel) {
    val call by vm.calls.state.collectAsStateWithLifecycle()
    val inCall = call.phase != CallPhase.IDLE

    val view = LocalView.current
    DisposableEffect(inCall) {
        view.keepScreenOn = inCall
        onDispose { view.keepScreenOn = false }
    }

    if (inCall) CallScreen(vm, call) else ChatScreen(vm)

    if (vm.showProfileSheet) {
        ProfileSheet(
            current = vm.profile,
            saving = vm.savingProfile,
            onSave = vm::saveProfile,
            onDismiss = vm::dismissProfileSheet,
        )
    }
}


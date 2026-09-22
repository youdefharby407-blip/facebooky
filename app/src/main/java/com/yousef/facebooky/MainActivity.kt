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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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

    if (vm.iAmBanned) {
        BannedScreen()
        return
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

@androidx.compose.runtime.Composable
private fun BannedScreen() {
    androidx.compose.material3.Surface(
        androidx.compose.ui.Modifier.fillMaxSize(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.background,
    ) {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            androidx.compose.material3.Text("🚫", style = androidx.compose.material3.MaterialTheme.typography.displayMedium)
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(12.dp))
            androidx.compose.material3.Text(
                "تم حظر هذا الجهاز",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            )
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(8.dp))
            androidx.compose.material3.Text(
                "لا يمكنك الوصول إلى الشات.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

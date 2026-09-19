package com.yousef.facebooky.ui

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for runtime permissions only at the moment a feature is used.
 * Usage: request(arrayOf(RECORD_AUDIO)) { startRecording() }
 */
class PermissionRequester internal constructor() {
    internal var pending: (() -> Unit)? = null
    internal lateinit var launch: (Array<String>) -> Unit
    internal lateinit var isGranted: (String) -> Boolean

    fun request(permissions: Array<String>, onGranted: () -> Unit) {
        if (permissions.all(isGranted)) {
            onGranted()
        } else {
            pending = onGranted
            launch(permissions)
        }
    }
}

@Composable
fun rememberPermissionRequester(): PermissionRequester {
    val context = LocalContext.current
    val requester = remember { PermissionRequester() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = requester.pending
        requester.pending = null
        if (result.values.all { it }) {
            action?.invoke()
        } else {
            Toast.makeText(context, "Permission is needed for this feature", Toast.LENGTH_SHORT).show()
        }
    }
    requester.launch = { launcher.launch(it) }
    requester.isGranted = { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    return requester
}

package com.tarnished.grace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.tarnished.grace.service.TunnelService
import com.tarnished.grace.ui.GraceScreen
import com.tarnished.grace.ui.GraceTheme

class MainActivity : ComponentActivity() {
    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) startTunnel()
    }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            GraceTheme {
                GraceScreen(onToggle = ::toggle)
            }
        }
    }

    private fun toggle(running: Boolean) {
        if (running) {
            startService(Intent(this, TunnelService::class.java).setAction(TunnelService.ACTION_STOP))
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnLauncher.launch(prepare) else startTunnel()
    }

    private fun startTunnel() {
        Core.engine.markStarting()
        ContextCompat.startForegroundService(this, Intent(this, TunnelService::class.java))
    }
}

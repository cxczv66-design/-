package com.example.firewall

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

object FirewallController {

    fun getPrepareIntent(context: Context): Intent? {
        return VpnService.prepare(context)
    }

    fun isFirewallPrepared(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }

    fun startFirewall(context: Context) {
        val intent = Intent(context, IsolateVpnService::class.java).apply {
            action = IsolateVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopFirewall(context: Context) {
        val intent = Intent(context, IsolateVpnService::class.java).apply {
            action = IsolateVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun reloadRules(context: Context) {
        val intent = Intent(context, IsolateVpnService::class.java).apply {
            action = IsolateVpnService.ACTION_RELOAD
        }
        ContextCompat.startForegroundService(context, intent)
    }
}

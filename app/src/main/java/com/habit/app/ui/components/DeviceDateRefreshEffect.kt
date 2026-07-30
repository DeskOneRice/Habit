package com.habit.app.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun DeviceDateRefreshEffect(onRefresh: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentRefresh by rememberUpdatedState(onRefresh)

    DisposableEffect(context, lifecycleOwner) {
        val applicationContext = context.applicationContext
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentRefresh()
            }
        }
        val dateChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                currentRefresh()
            }
        }
        val dateChangeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applicationContext.registerReceiver(
                dateChangeReceiver,
                dateChangeFilter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            applicationContext.registerReceiver(dateChangeReceiver, dateChangeFilter)
        }
        currentRefresh()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            applicationContext.unregisterReceiver(dateChangeReceiver)
        }
    }
}

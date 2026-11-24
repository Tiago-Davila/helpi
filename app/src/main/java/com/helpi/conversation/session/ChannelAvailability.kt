package com.helpi.conversation.session

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Resultado de preparar un canal: si quedó disponible y, si no, el motivo visible. */
internal data class ChannelAvailability(val available: Boolean, val detail: String = "")

internal fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

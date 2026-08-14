package com.nextgenmusic.player.storage

import android.app.ActivityManager
import android.content.Context
import android.os.Build

enum class CapabilityTier { ULTRA_LITE, BALANCED, PREMIUM }

class DeviceProfile(context: Context) {
    val isLowRam: Boolean
    val tier: CapabilityTier

    init {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lowMemory = if (Build.VERSION.SDK_INT >= 19) manager.isLowRamDevice else false
        val memoryClassMb = manager.memoryClass
        isLowRam = lowMemory || memoryClassMb <= 128
        tier = when {
            isLowRam || memoryClassMb <= 256 -> CapabilityTier.ULTRA_LITE
            memoryClassMb <= 512 -> CapabilityTier.BALANCED
            else -> CapabilityTier.PREMIUM
        }
    }
}
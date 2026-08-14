package com.nextgenmusic.player

import android.app.Application
import com.nextgenmusic.player.data.MusicRepository
import com.nextgenmusic.player.storage.DeviceProfile

class NextGenMusicApplication : Application() {
    lateinit var repository: MusicRepository
        private set
    lateinit var deviceProfile: DeviceProfile
        private set

    override fun onCreate() {
        super.onCreate()
        deviceProfile = DeviceProfile(this)
        repository = MusicRepository(this)
    }
}
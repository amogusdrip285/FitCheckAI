package com.fitcheckai.app

import android.app.Application
import com.fitcheckai.feature.wardrobe.v2.MockWardrobeSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FitCheckApplication : Application() {
    @Inject lateinit var mockWardrobeSeeder: MockWardrobeSeeder

    override fun onCreate() {
        super.onCreate()
        // No-op unless FITCHECK_MOCK_WARDROBE=true in .env at build time.
        mockWardrobeSeeder.seedOnce()
    }
}

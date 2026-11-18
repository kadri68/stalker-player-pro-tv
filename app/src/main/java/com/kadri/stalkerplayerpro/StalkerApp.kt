package com.kadri.stalkerplayerpro

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.kadri.stalkerplayerpro.data.local.AppDatabase
import com.kadri.stalkerplayerpro.data.local.SettingsDataStore

class StalkerApp : Application(), ImageLoaderFactory {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsStore: SettingsDataStore by lazy { SettingsDataStore(this) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.12) // الحد الأقصى 12% من الرام لحماية أجهزة التلفاز الضعيفة
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("tv_posters_cache"))
                    .maxSizeBytes(40L * 1024 * 1024) // 40 ميجابايت كحد أقصى على القرص
                    .build()
            }
            .allowRgb565(true) // تقليل استهلاك ذاكرة الصور للنصف
            .crossfade(false) // إلغاء التأثيرات الرسومية الثقيلة
            .build()
    }
}

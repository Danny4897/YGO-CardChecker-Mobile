package com.ygochecker.android

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ygochecker.core.domain.CardRepository
import com.ygochecker.core.domain.OfflinePackRepository
import com.ygochecker.core.domain.PreferenceRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class YgoCheckerApplication : Application() {
    @Inject lateinit var cards: CardRepository
    @Inject lateinit var pack: OfflinePackRepository
    @Inject lateinit var preferences: PreferenceRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val lang = preferences.language.first()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.id))
        }
        appScope.launch {
            pack.ensureBundledKnowledge()
            cards.ensureCatalogReady()
        }
        appScope.launch {
            pack.ensureRelatedKnowledge()
        }
    }
}

package com.ygochecker.android

import android.content.Context
import androidx.room.Room
import com.ygochecker.core.domain.*
import com.ygochecker.data.cards.*
import com.ygochecker.data.deck.*
import com.ygochecker.data.social.SupabaseSocialRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
abstract class DependencyModule {
    @Binds abstract fun cardRepository(value: RoomCardRepository): CardRepository
    @Binds abstract fun deckRepository(value: RoomDeckRepository): DeckRepository
    @Binds abstract fun profileRepository(value: RoomProfileRepository): ProfileRepository
    @Binds abstract fun socialRepository(value: SupabaseSocialRepository): SocialRepository
    @Binds abstract fun preferenceRepository(value: DataStorePreferenceRepository): PreferenceRepository
    @Binds abstract fun offlinePack(value: RoomOfflinePackRepository): OfflinePackRepository
    @Binds abstract fun searchCards(value: DefaultSearchCards): SearchCards
    @Binds abstract fun browsePlayable(value: DefaultBrowsePlayableCards): BrowsePlayableCards
    @Binds abstract fun randomPlayable(value: DefaultRandomPlayableCard): RandomPlayableCard
    @Binds abstract fun listDecks(value: DefaultListDecklists): ListDecklists
    @Binds abstract fun getDeck(value: DefaultGetDecklist): GetDecklist
    @Binds abstract fun createDeck(value: DefaultCreateDecklist): CreateDecklist
    @Binds abstract fun renameDeck(value: DefaultRenameDecklist): RenameDecklist
    @Binds abstract fun deleteDeck(value: DefaultDeleteDecklist): DeleteDecklist
    @Binds abstract fun setCard(value: DefaultSetCardQuantity): SetCardQuantity
    @Binds abstract fun importText(value: DefaultImportDeckFromText): ImportDeckFromText
    @Binds abstract fun persistImportedDeck(value: DefaultPersistImportedDeck): PersistImportedDeck
    @Binds abstract fun exportText(value: DefaultExportDeckToText): ExportDeckToText
    @Binds abstract fun importYdke(value: DefaultImportDeckFromYdke): ImportDeckFromYdke
    @Binds abstract fun exportYdke(value: DefaultExportDeckToYdke): ExportDeckToYdke
    @Binds abstract fun importYdk(value: DefaultImportDeckFromYdk): ImportDeckFromYdk
    @Binds abstract fun exportYdk(value: DefaultExportDeckToYdk): ExportDeckToYdk
    @Binds abstract fun formatPreference(value: DefaultFormatPreference): FormatPreference
    @Binds abstract fun languagePreference(value: DefaultLanguagePreference): LanguagePreference
    @Binds abstract fun evaluateDeckLegality(value: DefaultEvaluateDeckLegality): EvaluateDeckLegality
    @Binds abstract fun syncCatalog(value: DefaultSyncCatalog): SyncCatalog
    @Binds abstract fun syncScripts(value: DefaultSyncEffectScripts): SyncEffectScripts
    @Binds abstract fun syncRelated(value: DefaultSyncRelated): SyncRelated
    @Binds abstract fun syncLegality(value: DefaultSyncFormatLegality): SyncFormatLegality
    @Binds abstract fun syncAllKnowledge(value: DefaultSyncAllKnowledge): SyncAllKnowledge
    @Binds abstract fun observePack(value: DefaultObserveOfflinePack): ObserveOfflinePack
    @Binds abstract fun setDeckCovers(value: DefaultSetDeckCovers): SetDeckCovers
    @Binds abstract fun setDeckPublic(value: DefaultSetDeckPublic): SetDeckPublic
    @Binds abstract fun getEffectScript(value: DefaultGetEffectScript): GetEffectScript
    @Binds abstract fun getEffectScripts(value: DefaultGetEffectScripts): GetEffectScripts
    @Binds abstract fun getRelatedCards(value: DefaultGetRelatedCards): GetRelatedCards
    @Binds abstract fun resolveCardById(value: DefaultResolveCardById): ResolveCardById
    @Binds abstract fun resolveCardByName(value: DefaultResolveCardByName): ResolveCardByName
    @Binds abstract fun getLocalizedCard(value: DefaultGetLocalizedCard): GetLocalizedCard
    @Binds abstract fun accountLinker(value: com.ygochecker.android.auth.AndroidAccountLinker): AccountLinker
    @Binds abstract fun completeDeck(value: SynergyCompleteDeck): CompleteDeck
    @Binds abstract fun appUpdateRepository(value: com.ygochecker.android.update.HttpAppUpdateRepository): AppUpdateRepository
    @Binds abstract fun checkAppUpdate(value: DefaultCheckAppUpdate): CheckAppUpdate

    companion object {
        @Provides @Singleton fun okHttpClient(): OkHttpClient = YgoProDeckClient.defaultHttpClient()
        @Provides @Singleton @Named("supabaseUrl")
        fun supabaseUrl(): String = BuildConfig.SUPABASE_URL.trim()
        @Provides @Singleton fun supabaseClient(): SupabaseClient = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL.trim(),
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY.trim(),
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
        @Provides @Singleton fun cardDatabase(@ApplicationContext context: Context): CardDatabase =
            Room.databaseBuilder(context, CardDatabase::class.java, "cards.db")
                .fallbackToDestructiveMigration()
                .build()
        @Provides fun cardDao(database: CardDatabase) = database.cards()
        @Provides @Singleton fun deckDatabase(@ApplicationContext context: Context): DeckDatabase =
            Room.databaseBuilder(context, DeckDatabase::class.java, "decks.db")
                .fallbackToDestructiveMigration()
                .build()
        @Provides fun deckDao(database: DeckDatabase) = database.decks()
        @Provides fun profileDao(database: DeckDatabase) = database.profile()
    }
}

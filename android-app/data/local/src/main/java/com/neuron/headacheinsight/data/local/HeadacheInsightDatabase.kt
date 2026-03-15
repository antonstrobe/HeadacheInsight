package com.neuron.headacheinsight.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.neuron.headacheinsight.core.model.AppSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Singleton

@Database(
    entities = [
        UserProfileEntity::class,
        BaselineQuestionAnswerEntity::class,
        EpisodeEntity::class,
        EpisodeSymptomEntity::class,
        EpisodeContextEntity::class,
        EpisodeMedicationEntity::class,
        EpisodeTranscriptEntity::class,
        QuestionTemplateEntity::class,
        QuestionAnswerEntity::class,
        AttachmentEntity::class,
        AnalysisSnapshotEntity::class,
        SyncQueueItemEntity::class,
        RedFlagEventEntity::class,
        ExportArtifactEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HeadacheInsightDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun episodeContextDao(): EpisodeContextDao
    abstract fun episodeSymptomDao(): EpisodeSymptomDao
    abstract fun episodeMedicationDao(): EpisodeMedicationDao
    abstract fun episodeTranscriptDao(): EpisodeTranscriptDao
    abstract fun questionDao(): QuestionDao
    abstract fun questionAnswerDao(): QuestionAnswerDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun redFlagDao(): RedFlagDao
    abstract fun exportDao(): ExportDao
}

val Context.appSettingsDataStore: DataStore<AppSettings> by dataStore(
    fileName = "app_settings.json",
    serializer = object : Serializer<AppSettings> {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        override val defaultValue: AppSettings = AppSettings()

        override suspend fun readFrom(input: InputStream): AppSettings =
            runCatching {
                val content = input.readBytes().decodeToString()
                if (content.isBlank()) defaultValue else json.decodeFromString(AppSettings.serializer(), content)
            }.getOrDefault(defaultValue)

        override suspend fun writeTo(t: AppSettings, output: OutputStream) {
            output.write(json.encodeToString(AppSettings.serializer(), t).encodeToByteArray())
        }
    },
)

@Module
@InstallIn(SingletonComponent::class)
object LocalDataModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): HeadacheInsightDatabase = Room.databaseBuilder(
        context,
        HeadacheInsightDatabase::class.java,
        "headacheinsight.db",
    ).fallbackToDestructiveMigration(false).build()

    @Provides
    fun provideProfileDao(db: HeadacheInsightDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideEpisodeDao(db: HeadacheInsightDatabase): EpisodeDao = db.episodeDao()

    @Provides
    fun provideEpisodeContextDao(db: HeadacheInsightDatabase): EpisodeContextDao = db.episodeContextDao()

    @Provides
    fun provideEpisodeSymptomDao(db: HeadacheInsightDatabase): EpisodeSymptomDao = db.episodeSymptomDao()

    @Provides
    fun provideEpisodeMedicationDao(db: HeadacheInsightDatabase): EpisodeMedicationDao = db.episodeMedicationDao()

    @Provides
    fun provideEpisodeTranscriptDao(db: HeadacheInsightDatabase): EpisodeTranscriptDao = db.episodeTranscriptDao()

    @Provides
    fun provideQuestionDao(db: HeadacheInsightDatabase): QuestionDao = db.questionDao()

    @Provides
    fun provideQuestionAnswerDao(db: HeadacheInsightDatabase): QuestionAnswerDao = db.questionAnswerDao()

    @Provides
    fun provideAttachmentDao(db: HeadacheInsightDatabase): AttachmentDao = db.attachmentDao()

    @Provides
    fun provideAnalysisDao(db: HeadacheInsightDatabase): AnalysisDao = db.analysisDao()

    @Provides
    fun provideSyncQueueDao(db: HeadacheInsightDatabase): SyncQueueDao = db.syncQueueDao()

    @Provides
    fun provideRedFlagDao(db: HeadacheInsightDatabase): RedFlagDao = db.redFlagDao()

    @Provides
    fun provideExportDao(db: HeadacheInsightDatabase): ExportDao = db.exportDao()

    @Provides
    @Singleton
    fun provideAppSettings(@ApplicationContext context: Context): DataStore<AppSettings> = context.appSettingsDataStore
}

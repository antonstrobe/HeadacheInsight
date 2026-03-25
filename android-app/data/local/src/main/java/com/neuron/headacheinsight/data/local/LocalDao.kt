package com.neuron.headacheinsight.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM baseline_question_answer ORDER BY answeredAtEpochMs DESC")
    fun observeBaselineAnswers(): Flow<List<BaselineQuestionAnswerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBaselineAnswers(answers: List<BaselineQuestionAnswerEntity>)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episode ORDER BY startedAtEpochMs DESC")
    fun observeEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episode WHERE status = 'ACTIVE' ORDER BY startedAtEpochMs DESC LIMIT 1")
    fun observeActiveEpisode(): Flow<EpisodeEntity?>

    @Query("SELECT * FROM episode WHERE id = :episodeId LIMIT 1")
    fun observeEpisode(episodeId: String): Flow<EpisodeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisode(entity: EpisodeEntity)

    @Query("UPDATE episode SET status = 'COMPLETED', endedAtEpochMs = :endedAt, updatedAtEpochMs = :endedAt WHERE id = :episodeId")
    suspend fun completeEpisode(episodeId: String, endedAt: Long)
}

@Dao
interface EpisodeContextDao {
    @Query("SELECT * FROM episode_context WHERE episodeId = :episodeId LIMIT 1")
    fun observeContext(episodeId: String): Flow<EpisodeContextEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContext(entity: EpisodeContextEntity)
}

@Dao
interface EpisodeSymptomDao {
    @Query("SELECT * FROM episode_symptom WHERE episodeId = :episodeId")
    fun observeSymptoms(episodeId: String): Flow<List<EpisodeSymptomEntity>>

    @Query("DELETE FROM episode_symptom WHERE episodeId = :episodeId")
    suspend fun deleteByEpisode(episodeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EpisodeSymptomEntity>)
}

@Dao
interface EpisodeMedicationDao {
    @Query("SELECT * FROM episode_medication WHERE episodeId = :episodeId ORDER BY takenAtEpochMs DESC")
    fun observeByEpisode(episodeId: String): Flow<List<EpisodeMedicationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EpisodeMedicationEntity)
}

@Dao
interface EpisodeTranscriptDao {
    @Query("SELECT * FROM episode_transcript WHERE episodeId = :episodeId ORDER BY createdAtEpochMs DESC")
    fun observeByEpisode(episodeId: String): Flow<List<EpisodeTranscriptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EpisodeTranscriptEntity)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM question_template WHERE active = 1 AND stage = :stage ORDER BY priority DESC, createdAtEpochMs ASC")
    fun observeActiveQuestions(stage: String): Flow<List<QuestionTemplateEntity>>

    @Query("SELECT * FROM question_template WHERE active = 1 ORDER BY priority DESC, createdAtEpochMs ASC")
    fun observeAllActiveQuestions(): Flow<List<QuestionTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<QuestionTemplateEntity>)

    @Query("UPDATE question_template SET active = 0 WHERE id IN (:ids)")
    suspend fun deactivate(ids: List<String>)

    @Transaction
    suspend fun replaceSeed(items: List<QuestionTemplateEntity>) {
        upsertAll(items)
    }
}

@Dao
interface QuestionAnswerDao {
    @Query("SELECT * FROM question_answer WHERE episodeId = :episodeId ORDER BY answeredAtEpochMs DESC")
    fun observeByEpisode(episodeId: String): Flow<List<QuestionAnswerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<QuestionAnswerEntity>)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachment ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachment WHERE ownerId = :ownerId ORDER BY createdAtEpochMs DESC")
    fun observeByOwner(ownerId: String): Flow<List<AttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AttachmentEntity)
}

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analysis_snapshot WHERE ownerId = :ownerId ORDER BY createdAtEpochMs DESC LIMIT 1")
    fun observeLatest(ownerId: String): Flow<AnalysisSnapshotEntity?>

    @Query("SELECT * FROM analysis_snapshot WHERE ownerId = :ownerId ORDER BY createdAtEpochMs DESC")
    fun observeByOwner(ownerId: String): Flow<List<AnalysisSnapshotEntity>>

    @Query("SELECT * FROM analysis_snapshot ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<AnalysisSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AnalysisSnapshotEntity)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue_item ORDER BY createdAtEpochMs ASC")
    fun observeQueue(): Flow<List<SyncQueueItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SyncQueueItemEntity)

    @Query("UPDATE sync_queue_item SET status = :status, lastError = :lastError WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, lastError: String?)
}

@Dao
interface RedFlagDao {
    @Query("SELECT * FROM red_flag_event WHERE episodeId = :episodeId ORDER BY triggeredAtEpochMs DESC")
    fun observeByEpisode(episodeId: String): Flow<List<RedFlagEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RedFlagEventEntity>)
}

@Dao
interface ExportDao {
    @Query("SELECT * FROM export_artifact ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<ExportArtifactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ExportArtifactEntity>)
}

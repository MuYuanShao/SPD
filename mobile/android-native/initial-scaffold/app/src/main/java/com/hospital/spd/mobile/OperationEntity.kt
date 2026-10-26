package com.hospital.spd.mobile

import androidx.room.*

@Entity(tableName = "mobile_operation", indices = [Index(value = ["userId", "operationId"], unique = true)])
data class OperationEntity(
    @PrimaryKey val localId: String,
    val userId: Long,
    val serverId: String,
    val operationId: String,
    val kind: String,
    val payload: String,
    val state: String,
    val lastError: String? = null,
    val serverResult: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Dao
interface OperationDao {
    @Query("SELECT * FROM mobile_operation WHERE state IN ('pending_validation','submitting','result_unknown') ORDER BY createdAt")
    suspend fun pending(): List<OperationEntity>
    @Query("SELECT * FROM mobile_operation WHERE operationId = :id LIMIT 1")
    suspend fun find(id: String): OperationEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: OperationEntity)
    @Update suspend fun update(operation: OperationEntity)
}

@Database(entities = [OperationEntity::class], version = 1, exportSchema = true)
abstract class MobileDatabase : RoomDatabase() { abstract fun operations(): OperationDao }

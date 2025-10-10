package com.deadly.v2.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deadly.v2.core.database.entities.DataVersionEntity

@Dao
interface DataVersionDao {
    
    // Version management
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(dataVersion: DataVersionEntity)
    
    @Query("SELECT * FROM data_version_v2 WHERE id = 1")
    suspend fun getCurrentDataVersion(): DataVersionEntity?
    
    @Query("SELECT * FROM data_version_v2 WHERE id = 1")
    suspend fun getDataVersion(): DataVersionEntity?
    
    @Query("SELECT dataVersion FROM data_version_v2 WHERE id = 1")
    suspend fun getCurrentVersion(): String?
    
    // Check if data exists
    @Query("SELECT COUNT(*) > 0 FROM data_version_v2")
    suspend fun hasDataVersion(): Boolean
    
    // Management operations
    @Query("DELETE FROM data_version_v2")
    suspend fun deleteAll()
}
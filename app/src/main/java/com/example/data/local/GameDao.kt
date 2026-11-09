package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM local_games ORDER BY is100Percent DESC, personalRating DESC, title ASC")
    fun observeAllGames(): Flow<List<LocalGame>>

    @Query("SELECT * FROM local_games WHERE isOwned = 1 ORDER BY is100Percent DESC, personalRating DESC, title ASC")
    fun observeOwnedLibraryGames(): Flow<List<LocalGame>>

    @Query("SELECT * FROM local_games WHERE isOwned = 0 ORDER BY updatedAtEpoch DESC, title ASC")
    fun observeWishlistGames(): Flow<List<LocalGame>>

    @Query("SELECT * FROM local_games WHERE id = :gameId LIMIT 1")
    fun observeGameById(gameId: String): Flow<LocalGame?>

    @Query("SELECT * FROM local_games WHERE id = :gameId LIMIT 1")
    suspend fun getGameById(gameId: String): LocalGame?

    @Query("SELECT * FROM local_games")
    suspend fun getAllGamesSnapshot(): List<LocalGame>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGame(game: LocalGame)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGames(games: List<LocalGame>)

    @Update
    suspend fun updateGame(game: LocalGame)

    @Delete
    suspend fun deleteGame(game: LocalGame)

    @Query("DELETE FROM local_games WHERE id = :gameId")
    suspend fun deleteGameById(gameId: String)
}

package com.example.data.cloud

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.LocalGame
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudGameRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun syncGameToCloud_validPayload_updatesGlobalStatsAndEmitsInRanking() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repository = CloudGameRepository(firestore)
        val uniqueGameId = "igdb_${UUID.randomUUID().toString().replace("-", "_")}"

        val game = LocalGame(
            id = uniqueGameId,
            title = "Cyberpunk 2077",
            coverImageUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co4hk8.jpg",
            isPlayed = true,
            isOwned = true,
            is100Percent = true,
            personalRating = 5,
            personalNote = "100% Platin Run abgeschlossen!",
            isRecommended = true
        )

        val syncResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.syncGameToCloud(game) }
        assertTrue("Sync should succeed: ${syncResult.exceptionOrNull()}", syncResult.isSuccess)
        val stats = syncResult.getOrThrow()
        assertEquals(5.0, stats.averageRating, 0.01)
        assertEquals(1L, stats.recommendCount)
        assertEquals(1L, stats.total100PercentRuns)
        assertEquals(1L, stats.star5Count)
        assertEquals(0L, stats.star4Count)

        val rankings = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCommunityRankings().first { list -> list.any { it.id == uniqueGameId } }
        }
        assertTrue(rankings.any { it.id == uniqueGameId && it.total100PercentRuns == 1L && it.star5Count == 1L })
    }

    @Test
    fun getUserContribution_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudGameRepository(firestore)
        val uniqueGameId = "igdb_${UUID.randomUUID().toString().replace("-", "_")}"

        val game = LocalGame(
            id = uniqueGameId,
            title = "Alan Wake 2",
            coverImageUrl = "https://example.com/aw2.jpg",
            isPlayed = true,
            isOwned = true,
            is100Percent = false,
            personalRating = 4,
            personalNote = "Alice private note",
            isRecommended = true
        )
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.syncGameToCloud(game).getOrThrow() }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudGameRepository(firestore)
        val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getUserContributionForUser(aliceUid, uniqueGameId)
        }
        assertTrue("Cross-user access must fail", bobReadResult.isFailure)
        val firestoreEx = bobReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun adminVault_nonAdminUser_cannotReadOrWriteApiKeys() = runBlocking {
        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudGameRepository(firestore)

        // Regular user attempting to read API keys from /admin_vault/api_secrets must get PERMISSION_DENIED
        val readResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.fetchAdminVaultSecrets()
        }
        assertTrue("Non-admin read of admin_vault must fail", readResult.isFailure)
        val readEx = readResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, readEx?.code)

        // Regular user attempting to write API keys to /admin_vault/api_secrets must get PERMISSION_DENIED
        val writeResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.saveAdminVaultSecrets("id", "secret", "steam")
        }
        assertTrue("Non-admin write to admin_vault must fail", writeResult.isFailure)
        val writeEx = writeResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, writeEx?.code)
    }

    @Test
    fun observeCommunityRankings_unauthenticatedUser_fails() = runBlocking {
        auth.signOut()
        val repository = CloudGameRepository(firestore)
        var threwException = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeCommunityRankings().first()
            }
        } catch (e: Exception) {
            threwException = true
        }
        assertTrue("Unauthenticated caller must be rejected", threwException)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_story@test.com"
        const val BOB_EMAIL = "bob_story@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}

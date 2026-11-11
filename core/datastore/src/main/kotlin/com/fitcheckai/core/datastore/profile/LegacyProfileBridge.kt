package com.fitcheckai.core.datastore.profile

import com.fitcheckai.core.model.ProfileRepository
import com.fitcheckai.core.model.StyleProfile
import com.fitcheckai.core.model.profile.MAX_STYLE_PREFERENCES
import com.fitcheckai.core.model.profile.ProfileStorage
import com.fitcheckai.core.model.profile.UserProfile
import com.fitcheckai.core.model.wardrobe.LoadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/**
 * Read/write adapter so v1 consumers (StubOutfitService) keep compiling while they migrate to CT-04.
 * Single source of truth is [ProfileStorage]; there is no second profile store.
 */
class LegacyProfileBridge @Inject constructor(private val storage: ProfileStorage) : ProfileRepository {
    override fun observe(): Flow<StyleProfile?> = storage.observeProfile()
        .filterNot { it is LoadState.Loading }
        .map { state -> (state as? LoadState.Ready)?.value?.takeIf { it.onboardingCompleted }?.toLegacy() }

    override suspend fun save(profile: StyleProfile): Result<Unit> {
        val current = (storage.observeProfile().first { it !is LoadState.Loading } as? LoadState.Ready)?.value
        val base = current ?: UserProfile(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis())
        val next = base.copy(
            stylePreferences = profile.styles.filter { it.isNotBlank() }.take(MAX_STYLE_PREFERENCES),
            cityKey = profile.city?.takeIf { it.isNotBlank() },
            onboardingCompleted = true,
        )
        return storage.save(next, current?.revision).map { }
    }

    private fun UserProfile.toLegacy() = StyleProfile(styles = stylePreferences.toSet(), city = cityKey)
}

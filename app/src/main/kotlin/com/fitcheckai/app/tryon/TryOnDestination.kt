package com.fitcheckai.app.tryon

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.fitcheckai.core.model.nav.Routes
import com.fitcheckai.core.model.profile.ProfileStorage
import com.fitcheckai.core.model.tryon.InMemoryTryOnSelectionHandoff
import com.fitcheckai.core.model.tryon.TryOnSelectionHandoff
import com.fitcheckai.core.model.wardrobe.MediaStore
import com.fitcheckai.feature.tryon.client.LocalDiskTryOnMediaStore
import com.fitcheckai.feature.tryon.privacy.TryOnPrivacyCoordinator
import com.fitcheckai.feature.tryon.ui.TryOnRoute
import com.fitcheckai.feature.tryon.ui.TryOnViewModel
import com.fitcheckai.feature.tryon.ui.tryOnGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TryOnAppModule {
    @Provides
    @Singleton
    fun selectionHandoff(): TryOnSelectionHandoff = InMemoryTryOnSelectionHandoff()
}

/** TryOnViewModel is not a Hilt VM (Track C keeps it framework-free), so the root pulls its deps here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface TryOnEntryPoint {
    fun handoff(): TryOnSelectionHandoff
    fun profiles(): ProfileStorage
    fun media(): MediaStore
}

/** D-007: registers CT-03 `Routes.TRYON_PATTERN` with the CORE-mode service. */
fun NavGraphBuilder.tryOnDestination(navController: NavController) {
    tryOnGraph { outfitId, snapshotId ->
        TryOnRoute(
            viewModel = rememberTryOnViewModel(outfitId, snapshotId),
            onNavigateBack = { navController.popBackStack() },
            onNavigateToProfilePhotos = { navController.navigate(Routes.PROFILE) },
        )
    }
}

@Composable
private fun rememberTryOnViewModel(outfitId: String, snapshotId: String?): TryOnViewModel {
    val appContext = LocalContext.current.applicationContext
    return viewModel(
        key = "tryon/$outfitId/$snapshotId",
        factory = viewModelFactory { initializer { createTryOnViewModel(appContext, outfitId, snapshotId) } },
    )
}

private fun createTryOnViewModel(context: Context, outfitId: String, snapshotId: String?): TryOnViewModel {
    val deps = EntryPointAccessors.fromApplication(context, TryOnEntryPoint::class.java)
    val media = deps.media()
    // A stale or missing handoff (process death, other outfit picked) opens Try-On without an outfit.
    val selection = deps.handoff().find(outfitId)
        ?.takeIf { snapshotId != null && it.outfit.wardrobeSnapshotId == snapshotId }
    return TryOnViewModel(
        tryOnService = CoreModeVirtualTryOn(),
        profilePort = ProfileStorageTryOnPort(deps.profiles(), media),
        privacyCoordinator = TryOnPrivacyCoordinator(
            mediaStore = LocalDiskTryOnMediaStore(
                filesRootDir = context.filesDir,
                cacheRootDir = context.cacheDir,
                extraAllowedReadRoots = listOf(context.noBackupFilesDir),
            ),
        ),
        initialOutfit = selection?.toSnapshot(),
        initialGarmentImages = selection?.garmentImages(media).orEmpty(),
    )
}

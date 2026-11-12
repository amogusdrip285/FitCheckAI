package com.fitcheckai.feature.tryon.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.fitcheckai.core.ui.theme.FitCheckTheme
import com.fitcheckai.feature.tryon.contract.ImageView
import com.fitcheckai.feature.tryon.contract.QualityReviewState
import com.fitcheckai.feature.tryon.contract.TryOnJobStatus
import com.fitcheckai.feature.tryon.fake.FakeTryOnFixtures

@Preview(name = "1. Ready & Consent (Dual)", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
internal fun PreviewTryOnReadyConsent() {
    FitCheckTheme {
        TryOnScreenPreviewHost(
            TryOnUiState(
                isLoadingInitial = false,
                capability = FakeTryOnFixtures.dualCapability,
                outfitSnapshot = FakeTryOnFixtures.editorialWorkOutfit,
                personProfile = FakeTryOnFixtures.personDualSnapshot,
                garmentImages = FakeTryOnFixtures.defaultGarmentImages,
                selectedViewMode = TryOnViewMode.FRONT_AND_BACK,
                selectedItemIds = listOf("item-top-linen", "item-trouser-noir"),
                consentConfirmed = true,
            ),
        )
    }
}

@Preview(name = "2. Missing Back Photo", widthDp = 360, heightDp = 740, showBackground = true)
@Composable
internal fun PreviewTryOnMissingBack() {
    FitCheckTheme {
        TryOnScreenPreviewHost(
            TryOnUiState(
                isLoadingInitial = false,
                capability = FakeTryOnFixtures.dualCapability,
                outfitSnapshot = FakeTryOnFixtures.editorialWorkOutfit,
                personProfile = FakeTryOnFixtures.personFrontOnlySnapshot,
                garmentImages = FakeTryOnFixtures.defaultGarmentImages,
                selectedViewMode = TryOnViewMode.FRONT_ONLY,
                selectedItemIds = listOf("item-top-linen", "item-trouser-noir"),
                consentConfirmed = false,
            ),
        )
    }
}

@Preview(name = "3. Core Unavailable (UNKNOWN Gate)", widthDp = 412, heightDp = 860, showBackground = true)
@Composable
internal fun PreviewTryOnUnavailableCore() {
    FitCheckTheme {
        TryOnScreenPreviewHost(
            TryOnUiState(
                isLoadingInitial = false,
                capability = FakeTryOnFixtures.coreUnknownCapability,
                outfitSnapshot = FakeTryOnFixtures.editorialWorkOutfit,
                personProfile = FakeTryOnFixtures.personDualSnapshot,
                garmentImages = FakeTryOnFixtures.defaultGarmentImages,
            ),
        )
    }
}

@Preview(name = "4. Processing Running", widthDp = 412, heightDp = 860, showBackground = true)
@Composable
internal fun PreviewTryOnProcessing() {
    FitCheckTheme {
        TryOnScreenPreviewHost(
            TryOnUiState(
                isLoadingInitial = false,
                capability = FakeTryOnFixtures.dualCapability,
                outfitSnapshot = FakeTryOnFixtures.editorialWorkOutfit,
                personProfile = FakeTryOnFixtures.personDualSnapshot,
                garmentImages = FakeTryOnFixtures.defaultGarmentImages,
                selectedItemIds = listOf("item-top-linen", "item-trouser-noir"),
                activeOrLatestResult = FakeTryOnFixtures.previewResult(TryOnJobStatus.RUNNING),
            ),
        )
    }
}

@Preview(name = "5. Partial Failure (Back Failed)", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
internal fun PreviewTryOnPartialBackFailed() {
    FitCheckTheme {
        TryOnScreenPreviewHost(
            TryOnUiState(
                isLoadingInitial = false,
                capability = FakeTryOnFixtures.dualCapability,
                outfitSnapshot = FakeTryOnFixtures.editorialWorkOutfit,
                personProfile = FakeTryOnFixtures.personDualSnapshot,
                garmentImages = FakeTryOnFixtures.defaultGarmentImages,
                selectedItemIds = listOf("item-top-linen", "item-trouser-noir"),
                activeResultTab = ImageView.BACK,
                activeOrLatestResult = FakeTryOnFixtures.previewResult(
                    status = TryOnJobStatus.PARTIAL,
                    includeBack = true,
                    backFailed = true,
                ),
            ),
        )
    }
}

@Preview(name = "6. Two-Pane Tablet / Landscape Dark", widthDp = 840, heightDp = 640, showBackground = true)
@Composable
internal fun PreviewTryOnTwoPaneDark() {
    FitCheckTheme(darkTheme = true) {
        TryOnScreenPreviewHost(
            TryOnUiState(
                isLoadingInitial = false,
                capability = FakeTryOnFixtures.dualCapability,
                outfitSnapshot = FakeTryOnFixtures.editorialWorkOutfit,
                personProfile = FakeTryOnFixtures.personDualSnapshot,
                garmentImages = FakeTryOnFixtures.defaultGarmentImages,
                selectedItemIds = listOf("item-top-linen", "item-trouser-noir"),
                activeOrLatestResult = FakeTryOnFixtures.previewResult(
                    status = TryOnJobStatus.SUCCEEDED,
                    includeBack = true,
                    qualityReview = QualityReviewState.UNREVIEWED,
                ),
            ),
        )
    }
}

@Composable
internal fun TryOnScreenPreviewHost(state: TryOnUiState) {
    TryOnScreen(
        state = state,
        onNavigateBack = {},
        onNavigateToProfilePhotos = {},
        onSelectDifferentOutfit = {},
        onSelectViewMode = {},
        onToggleItem = {},
        onConsentChanged = {},
        onSubmit = {},
        onCancelJob = {},
        onSelectResultTab = {},
        onToggleCompareOriginal = {},
        onDeleteResult = {},
        onPrepareFreshAttempt = {},
    )
}

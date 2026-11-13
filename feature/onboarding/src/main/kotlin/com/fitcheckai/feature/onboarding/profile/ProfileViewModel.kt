package com.fitcheckai.feature.onboarding.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcheckai.core.model.profile.ProfileStorage
import com.fitcheckai.core.model.profile.UserProfile
import com.fitcheckai.core.model.wardrobe.Category
import com.fitcheckai.core.model.wardrobe.ErrorCode
import com.fitcheckai.core.model.wardrobe.ImageView
import com.fitcheckai.core.model.wardrobe.LoadState
import com.fitcheckai.core.model.wardrobe.MediaScope
import com.fitcheckai.core.model.wardrobe.MediaStore
import com.fitcheckai.core.model.wardrobe.WardrobeException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

sealed interface ProfileLoad {
    data object Loading : ProfileLoad
    data object Ready : ProfileLoad
    data class Failed(val message: String) : ProfileLoad
}

data class BodyPhotoUi(val mediaId: String, val displayPath: String?)

data class ProfileUiState(
    val load: ProfileLoad = ProfileLoad.Loading,
    val form: ProfileForm = ProfileForm(),
    val fieldErrors: Map<FormField, String> = emptyMap(),
    val hasProfile: Boolean = false,
    val photos: Map<ImageView, BodyPhotoUi> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * BACK-008 + D-005. Every field is optional; Skip still reaches Today. Saving never uploads anything.
 * Person photos go through Track A's MediaStore (PERSON scope) and ProfileStorage only.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val storage: ProfileStorage,
    private val media: MediaStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()
    private var profile: UserProfile? = null
    private var formInitialized = false

    init {
        viewModelScope.launch {
            storage.observeProfile().collect { load ->
                when (load) {
                    LoadState.Loading -> Unit
                    is LoadState.Failed -> _state.update { it.copy(load = ProfileLoad.Failed(load.error.message.orEmpty())) }
                    is LoadState.Ready -> onProfile(load.value)
                }
            }
        }
    }

    private fun onProfile(value: UserProfile?) {
        profile = value
        _state.update { current ->
            current.copy(
                load = ProfileLoad.Ready,
                // Never overwrite in-progress edits with a later emission.
                form = if (formInitialized) current.form else ProfileForm.from(value),
                hasProfile = value != null,
                photos = value?.bodyImages?.refs().orEmpty().associate { it.view to BodyPhotoUi(it.id, media.displayPath(it)) },
            )
        }
        formInitialized = true
    }

    fun setHeight(text: String) = editForm { it.copy(heightText = text.take(6)) }
    fun setWeight(text: String) = editForm { it.copy(weightText = text.take(6)) }
    fun toggleStyle(style: String) = editForm { it.toggleStyle(style) }
    fun toggleCategory(category: Category) = editForm { it.toggleCategory(category) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
    fun notify(message: String) = _state.update { it.copy(message = message) }

    private fun editForm(change: (ProfileForm) -> ProfileForm) =
        _state.update { it.copy(form = change(it.form), fieldErrors = emptyMap()) }

    /** Saves the form and marks onboarding done. Invalid input stays on screen for the user to fix. */
    fun save(onSaved: () -> Unit) {
        val errors = _state.value.form.errors()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        persist(onSaved) { base -> _state.value.form.applyTo(base) }
    }

    /** Skip keeps whatever was stored (nothing for a new user) and only completes onboarding. */
    fun skip(onDone: () -> Unit) = persist(onDone) { it }

    private fun persist(onDone: () -> Unit, transform: (UserProfile) -> UserProfile) = runBusy {
        val current = profile
        val next = transform(current ?: newProfile()).copy(onboardingCompleted = true)
        storage.save(next, current?.revision).onSuccess { onProfile(it); onDone() }.onFailure(::showError)
    }

    fun importPhoto(view: ImageView, uri: String) = runBusy {
        val owner = profile ?: storage.save(newProfile(), null).onFailure(::showError).getOrNull() ?: return@runBusy
        profile = owner
        val ref = media.importImage(uri, MediaScope.PERSON, owner.id, view).onFailure(::showError).getOrNull() ?: return@runBusy
        // Leaving the screen mid-way must not orphan the imported person photo.
        withContext(NonCancellable) {
            storage.replaceBodyPhoto(view, ref, owner.revision)
                .onSuccess(::onProfile)
                .onFailure { error ->
                    media.remove(ref) // do not leave an unreferenced person photo behind
                    showError(error)
                }
        }
    }

    fun removePhoto(view: ImageView) = runBusy {
        val current = profile ?: return@runBusy
        storage.replaceBodyPhoto(view, null, current.revision).onSuccess(::onProfile).onFailure(::showError)
    }

    /** Caller must confirm with the user first. */
    fun reset() = runBusy {
        storage.clearProfile()
            .onSuccess { receipt ->
                profile = null
                val message = if (receipt.cleanupPending) {
                    "Đã xóa hồ sơ. Một số ảnh có thể vẫn còn trên máy."
                } else {
                    "Đã xóa hồ sơ và ảnh cá nhân trên máy."
                }
                _state.update { it.copy(load = ProfileLoad.Ready, form = ProfileForm(), hasProfile = false, photos = emptyMap(),
                    message = message) }
            }
            .onFailure(::showError)
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return // double tap guard
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { block() } finally { _state.update { it.copy(busy = false) } }
        }
    }

    private fun showError(error: Throwable) {
        val message = when ((error as? WardrobeException)?.code) {
            ErrorCode.CONFLICT -> "Hồ sơ vừa thay đổi ở nơi khác. Hãy thử lại."
            ErrorCode.INVALID_REQUEST, ErrorCode.PAYLOAD_TOO_LARGE -> error.message ?: "Dữ liệu không hợp lệ."
            else -> error.message?.takeIf { it.isNotBlank() } ?: "Không lưu được. Hãy thử lại."
        }
        _state.update { it.copy(message = message) }
    }

    private fun newProfile() = UserProfile(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis())
}

package com.fitcheckai.feature.wardrobe.v2

import com.fitcheckai.core.model.wardrobe.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Track B replaces this binding when its v2 client is ready. Never returns canned AI success. */
class UnavailableRecognition @Inject constructor() : ClothingRecognition {
    override val capability = RecognitionCapability("unconfigured", "2", false, false)
    override suspend fun recognize(request: RecognitionRequest) = Result.failure<RecognitionResult>(
        WardrobeException(ErrorCode.UNSUPPORTED_CAPABILITY, "Nhận diện AI chưa được kết nối. Bạn vẫn có thể lưu thông tin thủ công."))
}
@Module
@InstallIn(SingletonComponent::class)
abstract class WardrobeBindings {
    @Binds abstract fun recognition(impl: UnavailableRecognition): ClothingRecognition
}

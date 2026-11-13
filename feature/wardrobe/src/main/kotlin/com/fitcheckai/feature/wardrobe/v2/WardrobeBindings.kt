package com.fitcheckai.feature.wardrobe.v2

import com.fitcheckai.core.model.wardrobe.*
/** Unconfigured recognition for previews and tests. */
class UnavailableRecognition : ClothingRecognition {
    override val capability = RecognitionCapability("unconfigured", "2", false, false)
    override suspend fun recognize(request: RecognitionRequest) = Result.failure<RecognitionResult>(
        WardrobeException(ErrorCode.UNSUPPORTED_CAPABILITY, "Nhận diện AI chưa được kết nối. Bạn vẫn có thể lưu thông tin thủ công."))
}

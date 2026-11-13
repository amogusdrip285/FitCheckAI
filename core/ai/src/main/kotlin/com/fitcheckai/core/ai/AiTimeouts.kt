package com.fitcheckai.core.ai

/**
 * OWNER: Track B. Thời gian chờ một request AI phía app (ms), từ gradle property `fitcheck.aiTimeoutMs`
 * (mặc định CT-07 20 000). Consumer (vd. ViewModel nhận diện của A) nên dùng cùng giá trị này thay vì hằng riêng,
 * để khi nới cho gateway chậm (FOSTA) mọi lớp chờ cùng nhau.
 */
object AiTimeouts {
    val requestMs: Long = BuildConfig.AI_TIMEOUT_MS
}

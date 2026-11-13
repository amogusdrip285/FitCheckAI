package com.fitcheckai.core.model.wardrobe

/** Manual edits win, including edits made while the provider request was in flight. */
fun mergeRecognition(current: ClothingAttributes, edited: Set<AttributeField>, result: RecognitionResult): ClothingAttributes {
    val suggestion = result.suggestedAttributes
    suggestion.validate()
    val allowed = AttributeField.entries.toSet() - edited
    val sources = current.attributeSources.toMutableMap()
    allowed.forEach { field ->
        sources.remove(field)
        val known = when (field) {
            AttributeField.CATEGORY -> suggestion.category != Category.UNKNOWN
            AttributeField.COLORS -> suggestion.colors.isNotEmpty()
            AttributeField.PATTERN -> suggestion.pattern != Pattern.UNKNOWN
            AttributeField.MATERIAL -> suggestion.material != null
            AttributeField.FORMALITY -> suggestion.formality != null
            AttributeField.SEASONS -> suggestion.seasonTags.isNotEmpty()
            AttributeField.STYLES -> suggestion.styleTags.isNotEmpty()
        }
        if (known && result.source == ResultSource.AI_LIVE) sources[field] = AttributeSource.AI
    }
    return current.copy(
        category = if (AttributeField.CATEGORY in allowed) suggestion.category else current.category,
        colors = if (AttributeField.COLORS in allowed) suggestion.colors else current.colors,
        pattern = if (AttributeField.PATTERN in allowed) suggestion.pattern else current.pattern,
        material = if (AttributeField.MATERIAL in allowed) suggestion.material else current.material,
        formality = if (AttributeField.FORMALITY in allowed) suggestion.formality else current.formality,
        seasonTags = if (AttributeField.SEASONS in allowed) suggestion.seasonTags else current.seasonTags,
        styleTags = if (AttributeField.STYLES in allowed) suggestion.styleTags else current.styleTags,
        attributeSources = sources,
        needsReviewFields = (current.needsReviewFields + result.needsReviewFields + suggestion.needsReviewFields) - edited,
    )
}

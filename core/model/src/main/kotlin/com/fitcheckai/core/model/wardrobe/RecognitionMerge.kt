package com.fitcheckai.core.model.wardrobe

/** Manual edits win, including edits made while the provider request was in flight. */
fun mergeRecognition(current: ClothingAttributes, edited: Set<AttributeField>, result: RecognitionResult): ClothingAttributes {
    val suggestion = result.suggestedAttributes
    suggestion.validate()
    val typeEdited = AttributeField.GARMENT_TYPE in edited
    val protectedFields = if (typeEdited && current.garmentType != null) edited + AttributeField.CATEGORY else edited
    val allowed = AttributeField.entries.toSet() - protectedFields - AttributeField.GARMENT_TYPE
    val category = if (AttributeField.CATEGORY in allowed) suggestion.category else current.category
    val suggestedType = suggestion.garmentType?.takeIf { GarmentType.find(it)?.category == category }
    val retainedType = current.garmentType?.takeIf { GarmentType.find(it)?.category == category }
    val useSuggestedType = !typeEdited && suggestedType != null
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
            AttributeField.GARMENT_TYPE -> false
        }
        if (known && result.source == ResultSource.AI_LIVE) sources[field] = AttributeSource.AI
    }
    if (useSuggestedType && result.source == ResultSource.AI_LIVE) sources[AttributeField.GARMENT_TYPE] = AttributeSource.AI
    else if (!typeEdited && (useSuggestedType || retainedType == null)) sources.remove(AttributeField.GARMENT_TYPE)
    val mismatchedType = !typeEdited && suggestion.garmentType != null && suggestedType == null
    val previousReview = if (useSuggestedType) current.needsReviewFields - AttributeField.GARMENT_TYPE else current.needsReviewFields
    val review = previousReview + result.needsReviewFields + suggestion.needsReviewFields
    return current.copy(
        category = category,
        garmentType = if (useSuggestedType) suggestedType else retainedType,
        colors = if (AttributeField.COLORS in allowed) suggestion.colors else current.colors,
        pattern = if (AttributeField.PATTERN in allowed) suggestion.pattern else current.pattern,
        material = if (AttributeField.MATERIAL in allowed) suggestion.material else current.material,
        formality = if (AttributeField.FORMALITY in allowed) suggestion.formality else current.formality,
        seasonTags = if (AttributeField.SEASONS in allowed) suggestion.seasonTags else current.seasonTags,
        styleTags = if (AttributeField.STYLES in allowed) suggestion.styleTags else current.styleTags,
        attributeSources = sources,
        needsReviewFields = (if (mismatchedType) review + AttributeField.GARMENT_TYPE else review) - edited,
    )
}

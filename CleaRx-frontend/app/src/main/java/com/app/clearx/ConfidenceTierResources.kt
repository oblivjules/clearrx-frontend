package com.app.clearx

import androidx.annotation.ColorRes
import androidx.annotation.StringRes

@get:StringRes
val ConfidenceTier.lineRes: Int get() = when (this) {
    ConfidenceTier.HIGH -> R.string.tier_high_line
    ConfidenceTier.MEDIUM -> R.string.tier_medium_line
    ConfidenceTier.LOW -> R.string.tier_low_line
}

@get:StringRes
val ConfidenceTier.fieldFlagRes: Int? get() = when (this) {
    ConfidenceTier.HIGH -> null
    ConfidenceTier.MEDIUM -> R.string.tier_medium_field
    ConfidenceTier.LOW -> R.string.tier_low_field
}

@get:ColorRes
val ConfidenceTier.colorRes: Int get() = when (this) {
    ConfidenceTier.HIGH -> R.color.status_success
    ConfidenceTier.MEDIUM -> R.color.status_warning
    ConfidenceTier.LOW -> R.color.status_error
}

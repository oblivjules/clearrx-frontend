package com.app.clearx

import com.app.clearx.network.ParsedPrescription
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewPolicyTest {
    private fun medicine() = ParsedPrescription(
        drug_name = "Amoxicillin", frequency = "twice daily", dose_amount = "1 tablet",
        name_matched = true,
    )

    @Test fun annotationsAreHiddenWithoutChangingText() {
        assertEquals("Take 1 tablet", withoutUncertaintyMarkers("Take 1 tablet [cutoff] [UNCERTAIN]"))
    }

    @Test fun preservedUnmatchedTextDoesNotLookVerified() {
        assertEquals(ConfidenceTier.LOW,
            ConfidenceTier.forMedicine(medicine().copy(name_matched = false)))
    }

    @Test fun cutoffRequiresReviewEvenWithoutSpecificField() {
        assertEquals(ConfidenceTier.MEDIUM,
            ConfidenceTier.forMedicine(medicine().copy(review_reasons = listOf("cutoff_text"))))
    }

    @Test fun explicitReviewFlagCannotBeIgnored() {
        assertEquals(ConfidenceTier.MEDIUM,
            ConfidenceTier.forMedicine(medicine().copy(review_required = true)))
    }

    @Test fun normalizedAndLegacyScoresUseTheSameThresholds() {
        assertEquals(95, ConfidenceTier.readScore(0.95f, 0))
        assertEquals(80, ConfidenceTier.readScore(null, 80))
        assertEquals(null, ConfidenceTier.readScore(null, 0))
        assertEquals(ConfidenceTier.HIGH, ConfidenceTier.fromScore(95))
        assertEquals(ConfidenceTier.MEDIUM, ConfidenceTier.fromScore(80))
        assertEquals(ConfidenceTier.LOW, ConfidenceTier.fromScore(79))
    }

    @Test fun missingOcrScoreUsesFieldWarningsAndNeverImageQuality() {
        assertEquals(ConfidenceTier.HIGH, ConfidenceTier.overall(null, listOf(medicine()), true))
        assertEquals(ConfidenceTier.MEDIUM,
            ConfidenceTier.overall(null, listOf(medicine().copy(frequency = null)), true))
        assertEquals(ConfidenceTier.LOW, ConfidenceTier.overall(null, emptyList(), false))
    }
}

package com.app.clearx

import com.app.clearx.network.ParsedPrescription

/**
 * Single source of truth for the HIGH / MEDIUM / LOW confidence tiers shown in the UI.
 *
 * Every tier is communicated with an icon + action-oriented text (never colour alone),
 * plus a status colour as reinforcement. The cut-offs below are provisional: they keep
 * the green / amber / red boundaries the result screen already used. They should be
 * re-tuned once the calibration check (tiers vs. actual correctness on a labelled
 * test set) has been run.
 */
enum class ConfidenceTier {
    HIGH, MEDIUM, LOW;

    companion object {
        const val HIGH_MIN = 95
        const val MEDIUM_MIN = 80

        /** Backend OCR scores use 0..1; legacy trocr_confidence uses 0..100. */
        fun readScore(ocrConfidence: Float?, legacyPercent: Int): Int? = when {
            ocrConfidence != null && ocrConfidence.isFinite() && ocrConfidence in 0f..1f ->
                (ocrConfidence * 100).toInt()
            legacyPercent in 1..100 -> legacyPercent
            else -> null
        }

        fun fromScore(score: Int): ConfidenceTier = when {
            score >= HIGH_MIN   -> HIGH
            score >= MEDIUM_MIN -> MEDIUM
            else                -> LOW
        }

        /** The more cautious of two tiers (LOW > MEDIUM > HIGH). */
        fun worst(a: ConfidenceTier, b: ConfidenceTier): ConfidenceTier =
            if (a.ordinal >= b.ordinal) a else b

        /**
         * Tier for one medicine card:
         * LOW when no medicine name could be resolved (unmatched / not found),
         * MEDIUM when the backend flagged any uncertain field, or when frequency or
         * dose amount is missing (a card must not read "Clear read" while those are
         * "Not found"), otherwise HIGH.
         *
         * Provisional rule: revisit it during the calibration check.
         */
        fun forMedicine(medicine: ParsedPrescription): ConfidenceTier {
            val hasResolvedName = !medicine.brand_name.isNullOrBlank() ||
                    !medicine.drug_name.isNullOrBlank()
            val missingKeyField = medicine.frequency.isNullOrBlank() ||
                    medicine.dose_amount.isNullOrBlank()
            return when {
                !hasResolvedName || medicine.name_matched == false -> LOW
                medicine.uncertain || medicine.review_required || medicine.review_reasons.orEmpty().isNotEmpty() ||
                        medicine.uncertain_fields.orEmpty().isNotEmpty() || missingKeyField -> MEDIUM
                else -> HIGH
            }
        }

        /**
         * Overall tier for a result: the most cautious of the handwriting-reading score,
         * every medicine card, and the medicine-list match (a result with medicines but no
         * list match is never better than MEDIUM).
         */
        fun overall(
            readScore: Int?,
            medicines: List<ParsedPrescription>,
            drugMatched: Boolean,
        ): ConfidenceTier {
            val read = readScore?.let { fromScore(it) } ?: HIGH
            if (medicines.isEmpty()) return LOW
            val cards = medicines
                .map { forMedicine(it) }
                .reduce { a, b -> worst(a, b) }
            val matchFloor = if (drugMatched) HIGH else MEDIUM
            return worst(worst(read, cards), matchFloor)
        }
    }
}

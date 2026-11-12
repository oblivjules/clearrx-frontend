package com.app.clearx

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionModelTest {
    @Test fun missingOrUnsupportedPreferencesFallBackToPrimary() {
        for (value in listOf(null, "", "retired-model")) {
            assertEquals(RecognitionModel.PRIMARY, RecognitionModel.fromSavedId(value))
        }
    }

    @Test fun savedExternalChoiceSurvivesRestoration() {
        val selected = RecognitionModel.EXTERNAL
        assertEquals(selected, RecognitionModel.fromSavedId(selected.apiId))
    }
}

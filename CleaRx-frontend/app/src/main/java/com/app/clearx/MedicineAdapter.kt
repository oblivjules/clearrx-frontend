package com.app.clearx

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.LinearLayout
import android.util.TypedValue
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.app.clearx.network.ParsedPrescription
import android.graphics.Typeface

class MedicineAdapter(
    private var items: List<ParsedPrescription> = emptyList()
) : RecyclerView.Adapter<MedicineAdapter.ViewHolder>() {

    private var textScale = 1f

    fun setTextScale(scale: Float) {
        textScale = scale
        notifyDataSetChanged()
    }

    private fun scaleText(view: View) {
        if (view is TextView) {
            // Keep the system-scaled base size; repeated binds must not compound it.
            val base = (view.tag as? Float) ?: view.textSize.also { view.tag = it }
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, base * textScale)
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) scaleText(view.getChildAt(index))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val drugName:      TextView     = view.findViewById(R.id.itemDrugName)
        val mealRelation:  TextView     = view.findViewById(R.id.itemMealRelation)
        val strengthCell:  View         = view.findViewById(R.id.itemStrengthCell)
        val strengthLabel: TextView     = view.findViewById(R.id.itemStrengthLabel)
        val dosageFormCell: View        = view.findViewById(R.id.itemDosageFormCell)
        val doseAmountCell: View        = view.findViewById(R.id.itemDoseAmountCell)
        val frequencyCell: View         = view.findViewById(R.id.itemFrequencyCell)
        val timingCell: View            = view.findViewById(R.id.itemTimingCell)
        val routeCell: View             = view.findViewById(R.id.itemRouteCell)
        val quantityCell: View          = view.findViewById(R.id.itemQuantityCell)
        val durationCell: View          = view.findViewById(R.id.itemDurationCell)
        val instructionsCell: View      = view.findViewById(R.id.itemInstructionsCell)
        val frequency:     TextView     = view.findViewById(R.id.itemFrequency)
        val route:         TextView     = view.findViewById(R.id.itemRoute)
        val duration:      TextView     = view.findViewById(R.id.itemDuration)
        val strength:      TextView     = view.findViewById(R.id.itemStrength)
        val dosageForm:    TextView     = view.findViewById(R.id.itemDosageForm)
        val doseAmount:    TextView     = view.findViewById(R.id.itemDoseAmount)
        val timing:        TextView     = view.findViewById(R.id.itemTiming)
        val instructions:  TextView     = view.findViewById(R.id.itemInstructions)
        val quantity:      TextView     = view.findViewById(R.id.itemQuantity)
        val accentBar:       View     = view.findViewById(R.id.accentBar)
        val confidenceBadge: TextView = view.findViewById(R.id.itemConfidenceBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medicine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        scaleText(holder.itemView)
        val med = items[position]

        val brand   = med.brand_name?.takeIf { it.isNotBlank() }
        val generic = med.drug_name?.takeIf { it.isNotBlank() }
        val rawDrug = med.drug_name_raw?.takeIf { it.isNotBlank() }
        val uncertainFields = med.uncertain_fields.orEmpty()

        // Card-level confidence: icon + action text (colour only reinforces it).
        val cardTier = ConfidenceTier.forMedicine(med)
        holder.confidenceBadge.text = holder.confidenceBadge.context.getString(cardTier.lineRes)
        holder.confidenceBadge.setTextColor(
            ContextCompat.getColor(holder.confidenceBadge.context, cardTier.colorRes)
        )
        holder.confidenceBadge.visibility = View.VISIBLE

        val title = when {
            brand != null && generic != null -> "${brand.replaceFirstChar { it.uppercase() }} ($generic)"
            brand != null -> brand.replaceFirstChar { it.uppercase() }
            generic != null -> generic
            rawDrug != null -> rawDrug
            else -> "Medicine name not found"
        }
        // Unmatched / missing names need verifying; an uncertain name needs a second look.
        val drugNameFlag = when {
            med.name_matched == false || (generic == null && brand == null) -> ConfidenceTier.LOW
            "drug_name" in uncertainFields -> ConfidenceTier.MEDIUM
            else -> null
        }
        holder.drugName.text = withFlagSuffix(holder.drugName.context, title, drugNameFlag)

        val meal = med.meal_relation?.takeIf { it.isNotBlank() }
        holder.mealRelation.text = meal
        holder.mealRelation.visibility = if (meal == null) View.GONE else View.VISIBLE

        val strength = med.strength?.takeIf { it.isNotBlank() } ?: med.dosage?.takeIf { it.isNotBlank() }
        holder.strengthLabel.text = if (med.strength.isNullOrBlank()) "DOSAGE" else "STRENGTH"
        bindOptional(holder.strengthCell, holder.strength, strength, fieldFlag(uncertainFields, "strength"))
        bindOptional(holder.dosageFormCell, holder.dosageForm, med.dosage_form, fieldFlag(uncertainFields, "dosage_form"))
        bindOptional(holder.doseAmountCell, holder.doseAmount, med.dose_amount, fieldFlag(uncertainFields, "dose_amount"), flagWhenMissing = true)
        bindOptional(holder.frequencyCell, holder.frequency, med.frequency, fieldFlag(uncertainFields, "frequency"), flagWhenMissing = true)
        bindOptional(holder.timingCell, holder.timing, med.timing, fieldFlag(uncertainFields, "timing"))
        bindOptional(holder.instructionsCell, holder.instructions, med.administration_instructions, fieldFlag(uncertainFields, "administration_instructions"))
        bindOptional(holder.routeCell, holder.route, med.route_of_administration, fieldFlag(uncertainFields, "route_of_administration"))
        bindOptional(holder.quantityCell, holder.quantity, med.quantity, fieldFlag(uncertainFields, "quantity"))
        val duration = med.duration?.takeIf { it.isNotBlank() }?.let {
            if (med.duration_source == "derived") "$it (from quantity)" else it
        }
        bindOptional(holder.durationCell, holder.duration, duration, fieldFlag(uncertainFields, "duration"))

        // Hide wholly empty rows and stack paired fields when larger text needs room.
        val stack = textScale * holder.itemView.resources.configuration.fontScale >= 1.3f ||
                holder.itemView.resources.configuration.screenWidthDp < 340
        val rows = listOf(holder.strengthCell, holder.doseAmountCell, holder.timingCell, holder.quantityCell)
            .map { it.parent as LinearLayout }.distinct()
        for (row in rows) {
            row.orientation = if (stack) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            var visibleCount = 0
            for (index in 0 until row.childCount) {
                val cell = row.getChildAt(index)
                val params = cell.layoutParams as LinearLayout.LayoutParams
                params.width = if (stack) ViewGroup.LayoutParams.MATCH_PARENT else 0
                params.weight = if (stack) 0f else 1f
                params.marginEnd = if (!stack && index == 0) (8 * row.resources.displayMetrics.density).toInt() else 0
                params.topMargin = if (stack && cell.visibility == View.VISIBLE && visibleCount > 0)
                    (8 * row.resources.displayMetrics.density).toInt() else 0
                cell.layoutParams = params
                if (cell.visibility == View.VISIBLE) visibleCount++
            }
            row.visibility = if (visibleCount == 0) View.GONE else View.VISIBLE
        }

    }

    /** A field only gets its own flag when the backend marked it uncertain. */
    private fun fieldFlag(uncertainFields: List<String>, key: String): ConfidenceTier? =
        ConfidenceTier.MEDIUM.takeIf { key in uncertainFields }

    private fun bindOptional(
        cell: View,
        view: TextView,
        value: String?,
        flag: ConfidenceTier? = null,
        flagWhenMissing: Boolean = false,
    ) {
        val text = value?.takeIf { it.isNotBlank() }
        val essential = cell.id in setOf(R.id.itemStrengthCell, R.id.itemDoseAmountCell, R.id.itemFrequencyCell, R.id.itemRouteCell)
        cell.visibility = if (text == null && !essential) View.GONE else View.VISIBLE
        if (text == null) {
            // Key fields that are missing get the same flag as uncertain ones, so the card's
            // "Double-check" line always points at something visible.
            view.text = withFlagSuffix(
                view.context,
                "Not found",
                ConfidenceTier.MEDIUM.takeIf { flagWhenMissing },
            )
        }
        if (text != null) {
            view.text = withFlagSuffix(view.context, text, flag)
            view.setTextColor(ContextCompat.getColor(view.context, R.color.text_main))
        }
    }

    /**
     * Appends a field-level flag ("⚠ Double-check" / "✖ Verify") after [value].
     * The icon and wording carry the meaning; the tier colour only reinforces it.
     */
    private fun withFlagSuffix(context: Context, value: String, flag: ConfidenceTier?): CharSequence {
        val displayValue = withoutUncertaintyMarkers(value)
        val flagRes = flag?.fieldFlagRes ?: return displayValue
        val flagText = context.getString(flagRes)
        val styled = SpannableString("$displayValue $flagText")
        val start = displayValue.length + 1
        styled.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, flag.colorRes)),
            start,
            styled.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        styled.setSpan(
            StyleSpan(Typeface.BOLD),
            start,
            styled.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return styled
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<ParsedPrescription>) {
        items = newItems
        notifyDataSetChanged()
    }
}

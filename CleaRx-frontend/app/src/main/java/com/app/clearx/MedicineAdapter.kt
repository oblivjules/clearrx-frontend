package com.app.clearx

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.app.clearx.network.ParsedPrescription

class MedicineAdapter(
    private var items: List<ParsedPrescription> = emptyList()
) : RecyclerView.Adapter<MedicineAdapter.ViewHolder>() {

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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medicine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val med = items[position]

        val brand   = med.brand_name?.takeIf { it.isNotBlank() }
        val generic = med.drug_name?.takeIf { it.isNotBlank() }
        val rawDrug = med.drug_name_raw?.takeIf { it.isNotBlank() }
        val uncertainFields = med.uncertain_fields.orEmpty()

        val title = when {
            brand != null && generic != null -> "${brand.replaceFirstChar { it.uppercase() }} ($generic)"
            brand != null -> brand.replaceFirstChar { it.uppercase() }
            generic != null -> generic
            rawDrug != null -> rawDrug
            else -> "—"
        }
        holder.drugName.text = when {
            rawDrug != null && generic == null && brand == null ->
                withMutedSuffix(holder.drugName.context, title, "(unmatched)")
            else -> withMutedSuffix(
                holder.drugName.context,
                title,
                "(uncertain)".takeIf { "drug_name" in uncertainFields },
            )
        }
        val meal = med.meal_relation?.takeIf { it.isNotBlank() }
        holder.mealRelation.text = meal
        holder.mealRelation.visibility = if (meal == null) View.GONE else View.VISIBLE

        val strength = med.strength?.takeIf { it.isNotBlank() } ?: med.dosage?.takeIf { it.isNotBlank() }
        holder.strengthLabel.text = if (med.strength.isNullOrBlank()) "DOSAGE" else "STRENGTH"
        bindOptional(holder.strengthCell, holder.strength, strength, "strength" in uncertainFields)
        bindOptional(holder.dosageFormCell, holder.dosageForm, med.dosage_form, "dosage_form" in uncertainFields)
        bindOptional(holder.doseAmountCell, holder.doseAmount, med.dose_amount, "dose_amount" in uncertainFields)
        bindOptional(holder.frequencyCell, holder.frequency, med.frequency, "frequency" in uncertainFields)
        bindOptional(holder.timingCell, holder.timing, med.timing, "timing" in uncertainFields)
        bindOptional(holder.instructionsCell, holder.instructions, med.administration_instructions, "administration_instructions" in uncertainFields)
        bindOptional(holder.routeCell, holder.route, med.route_of_administration, "route_of_administration" in uncertainFields)
        bindOptional(holder.quantityCell, holder.quantity, med.quantity, "quantity" in uncertainFields)
        val duration = med.duration?.takeIf { it.isNotBlank() }?.let {
            if (med.duration_source == "derived") "$it (from quantity)" else it
        }
        bindOptional(holder.durationCell, holder.duration, duration, "duration" in uncertainFields)

    }

    private fun bindOptional(cell: View, view: TextView, value: String?, uncertain: Boolean = false) {
        val text = value?.takeIf { it.isNotBlank() }
        cell.visibility = if (text == null) View.GONE else View.VISIBLE
        if (text != null) {
            view.text = withMutedSuffix(
                view.context,
                text,
                "(uncertain)".takeIf { uncertain },
            )
            view.setTextColor(ContextCompat.getColor(view.context, R.color.text_main))
        }
    }

    private fun withMutedSuffix(context: Context, value: String, suffix: String?): CharSequence {
        if (suffix == null) return value
        val styled = SpannableString("$value $suffix")
        val start = value.length + 1
        styled.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_secondary)),
            start,
            styled.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        styled.setSpan(
            RelativeSizeSpan(0.85f),
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

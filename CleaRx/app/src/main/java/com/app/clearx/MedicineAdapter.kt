package com.app.clearx

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.app.clearx.network.ParsedPrescription

class MedicineAdapter(
    private var items: List<ParsedPrescription> = emptyList()
) : RecyclerView.Adapter<MedicineAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val drugName:      TextView     = view.findViewById(R.id.itemDrugName)
        val mealRelation:  TextView     = view.findViewById(R.id.itemMealRelation)
        val dosage:        TextView     = view.findViewById(R.id.itemDosage)
        val frequency:     TextView     = view.findViewById(R.id.itemFrequency)
        val route:         TextView     = view.findViewById(R.id.itemRoute)
        val duration:      TextView     = view.findViewById(R.id.itemDuration)
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

        holder.drugName.text = when {
            brand != null && generic != null -> "${brand.replaceFirstChar { it.uppercase() }} ($generic)"
            brand != null  -> brand.replaceFirstChar { it.uppercase() }
            generic != null -> generic
            else -> "—"
        }

        val meal = med.meal_relation?.takeIf { it.isNotBlank() }
        if (meal != null) {
            holder.mealRelation.text       = meal
            holder.mealRelation.visibility = View.VISIBLE
        } else {
            holder.mealRelation.visibility = View.GONE
        }

        holder.dosage.text    = med.dosage?.takeIf { it.isNotBlank() }    ?: "—"
        holder.frequency.text = med.frequency?.takeIf { it.isNotBlank() } ?: "—"
        holder.route.text     = med.route_of_administration?.takeIf { it.isNotBlank() } ?: "—"
        holder.duration.text  = med.duration?.takeIf { it.isNotBlank() }  ?: "—"
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<ParsedPrescription>) {
        items = newItems
        notifyDataSetChanged()
    }
}

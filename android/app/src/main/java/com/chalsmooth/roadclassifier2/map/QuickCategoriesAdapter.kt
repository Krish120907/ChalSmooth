package com.chalsmooth.roadclassifier2.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chalsmooth.roadclassifier2.R
import com.google.android.material.chip.Chip

data class QuickCategory(
    val name: String,
    val iconRes: Int,
    val mapplsCategory: String
)

class QuickCategoriesAdapter(
    private val categories: List<QuickCategory>,
    private val onCategoryClick: (QuickCategory) -> Unit
) : RecyclerView.Adapter<QuickCategoriesAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chip: Chip = view.findViewById(R.id.chipCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.chip.setText(category.name)
        holder.chip.setChipIconResource(category.iconRes)
        holder.chip.setOnClickListener { onCategoryClick(category) }
    }

    override fun getItemCount(): Int = categories.size
}
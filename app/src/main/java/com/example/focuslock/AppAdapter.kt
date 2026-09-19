package com.example.focuslock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class AppAdapter(
    private var allApps: List<AppInfo>,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var displayedApps: MutableList<AppInfo> = allApps.toMutableList()

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        val tvName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvPackage: TextView = itemView.findViewById(R.id.tvPackageName)
        val cbSelected: CheckBox = itemView.findViewById(R.id.cbSelected)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val app = displayedApps[position]
                    app.isSelected = !app.isSelected
                    cbSelected.isChecked = app.isSelected
                    onSelectionChanged(getSelectedCount())
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = displayedApps[position]
        holder.tvName.text = app.appName
        holder.tvPackage.text = app.packageName
        holder.cbSelected.isChecked = app.isSelected

        if (app.icon != null) {
            holder.ivIcon.setImageDrawable(app.icon)
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_lock)
        }
    }

    override fun getItemCount(): Int = displayedApps.size

    fun updateData(newApps: List<AppInfo>) {
        allApps = newApps
        displayedApps = newApps.toMutableList()
        notifyDataSetChanged()
        onSelectionChanged(getSelectedCount())
    }

    fun filter(query: String) {
        val cleanQuery = query.trim().lowercase(Locale.getDefault())
        displayedApps = if (cleanQuery.isEmpty()) {
            allApps.toMutableList()
        } else {
            allApps.filter {
                it.appName.lowercase(Locale.getDefault()).contains(cleanQuery) ||
                it.packageName.lowercase(Locale.getDefault()).contains(cleanQuery)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }

    fun selectAll() {
        for (app in displayedApps) {
            app.isSelected = true
        }
        notifyDataSetChanged()
        onSelectionChanged(getSelectedCount())
    }

    fun deselectAll() {
        for (app in allApps) {
            app.isSelected = false
        }
        notifyDataSetChanged()
        onSelectionChanged(getSelectedCount())
    }

    fun getSelectedPackages(): ArrayList<String> {
        return ArrayList(allApps.filter { it.isSelected }.map { it.packageName })
    }

    fun getSelectedCount(): Int {
        return allApps.count { it.isSelected }
    }
}

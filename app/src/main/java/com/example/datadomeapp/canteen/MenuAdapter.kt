package com.example.datadomeapp.canteen

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.squareup.picasso.Picasso
import java.io.File

class MenuAdapter(
    private val menuList: MutableList<MenuItem>,
    private val onEditClick: (MenuItem) -> Unit,
    private val onDeleteClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    class MenuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvMenuName)
        val tvPrice: TextView = itemView.findViewById(R.id.tvMenuPrice)
        val tvStatus: TextView = itemView.findViewById(R.id.tvMenuAvailability)
        val ivImage: ImageView = itemView.findViewById(R.id.ivMenuPreview)
        val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.canteen_item_menu, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val item = menuList[position]

        holder.tvName.text = item.name
        holder.tvPrice.text = "₱%.2f".format(item.price)
        holder.tvStatus.text = if (item.available) "Available" else "Out of Stock"
        holder.tvStatus.setTextColor(
            if (item.available) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        )

        if (item.imageUrl.isNotEmpty()) {
            val file = File(item.imageUrl)
            if (file.exists()) {
                Picasso.get()
                    .load(file)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_delete)
                    .into(holder.ivImage)
            } else {
                holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.btnEdit.setOnClickListener { onEditClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = menuList.size
}

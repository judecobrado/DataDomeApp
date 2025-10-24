package com.example.datadomeapp.canteen

import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R

class MenuAdapter(
    private val menuList: MutableList<MenuItem>,
    private val onEditClick: (MenuItem) -> Unit,
    private val onDeleteClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    class MenuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // ViewHolder components match the canteen_item_menu layout
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

        // --- Data Binding ---
        holder.tvName.text = item.name
        holder.tvPrice.text = "₱%.2f".format(item.price)

        // Status and Color Logic
        holder.tvStatus.text = if (item.available) "Available" else "Out of Stock"
        holder.tvStatus.setTextColor(
            if (item.available) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        )

        // --- IMAGE FIX: Use 'imageUrl' to match Firestore field ---
        // This assumes your MenuItem data class has been updated to use 'var imageUrl: String = ""'
        if (item.imageUrl.isNotEmpty()) {
            try {
                // 1. Decode Base64 string to byte array
                val bytes = Base64.decode(item.imageUrl, Base64.DEFAULT)

                // 2. Convert byte array to Bitmap
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                // 3. Set the image
                holder.ivImage.setImageBitmap(bitmap)
            } catch (e: IllegalArgumentException) {
                // Handle case where Base64 string is corrupted or invalid
                holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            // Fallback for items with no image saved
            holder.ivImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // --- Click Listeners ---
        holder.btnEdit.setOnClickListener { onEditClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = menuList.size
}
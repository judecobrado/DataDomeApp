package com.example.datadomeapp.canteen

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import java.util.*

// Assuming MenuItem is defined elsewhere, e.g., in a separate file or the main activity.
// data class MenuItem(var id: String = "", val name: String? = null, val price: Double? = null, val imageUrl: String? = null, ...)

class MenuAdapterPOS(
    private val menuList: List<MenuItem>,
    private val onAddClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<MenuAdapterPOS.MenuViewHolder>() {

    class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvMenuName)
        val tvPrice: TextView = view.findViewById(R.id.tvMenuPrice)
        val ivImage: ImageView = view.findViewById(R.id.ivMenuItemImage)
        val btnAdd: Button = view.findViewById(R.id.btnAddItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu_pos, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val item = menuList[position]
        holder.tvName.text = item.name ?: "No Name"
        holder.tvPrice.text = String.format(Locale.US, "₱%.2f", item.price ?: 0.0)

        // Image loading (Reused Base64 logic)
        item.imageUrl?.let { base64Image ->
            try {
                val bytes = Base64.decode(base64Image, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                holder.ivImage.setImageBitmap(bitmap)
            } catch (e: Exception) {
                holder.ivImage.setImageResource(R.drawable.ic_image_placeholder) // Use a placeholder
            }
        } ?: holder.ivImage.setImageResource(R.drawable.ic_image_placeholder) // Use a placeholder

        // Add to Cart Button Listener
        holder.btnAdd.setOnClickListener {
            onAddClick(item)
        }
    }

    override fun getItemCount(): Int = menuList.size
}
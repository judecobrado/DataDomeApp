package com.example.datadomeapp.student

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R

class CanteenMenuAdapter(
    private val onItemClick: ((CanteenMenuItem) -> Unit)? = null
) : RecyclerView.Adapter<CanteenMenuAdapter.MenuViewHolder>() {

    private var items: List<CanteenMenuItem> = emptyList()

    fun submitList(newList: List<CanteenMenuItem>) {
        items = newList
        notifyDataSetChanged()
    }

    inner class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.ivItemImage)
        val tvName: TextView = view.findViewById(R.id.tvItemName)
        val tvPrice: TextView = view.findViewById(R.id.tvItemPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_canteen_menu, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvPrice.text = "₱${String.format("%.2f", item.price)}"

        // Convert Base64 to Bitmap
        if (item.imageBase64.isNotEmpty()) {
            try {
                val decodedBytes = android.util.Base64.decode(item.imageBase64, android.util.Base64.DEFAULT)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                holder.ivImage.setImageBitmap(bmp)
            } catch (e: Exception) {
                e.printStackTrace()
                holder.ivImage.setImageResource(R.drawable.ic_image_placeholder)
            }
        } else {
            holder.ivImage.setImageResource(R.drawable.ic_image_placeholder)
        }

        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
    }

    override fun getItemCount() = items.size
}

/** Extension function to convert Base64 string to Bitmap safely */
fun String.toBitmapOrNull(): Bitmap? = try {
    if (this.isEmpty()) null
    else {
        val decodedBytes = Base64.decode(this, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }
} catch (e: Exception) {
    e.printStackTrace()
    null
}

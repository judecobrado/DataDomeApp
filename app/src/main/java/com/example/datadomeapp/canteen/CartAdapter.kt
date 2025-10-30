package com.example.datadomeapp.canteen

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton // <-- Import ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import java.util.*

class CartAdapter(
    private val cartList: MutableList<CartItem>,
    private val onQuantityChange: (CartItem, Int) -> Unit,
    private val onRemove: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    class CartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvCartItemName)
        val tvPrice: TextView = view.findViewById(R.id.tvCartItemPrice)
        val tvQuantity: TextView = view.findViewById(R.id.tvCartItemQuantity)

        // FIX DITO: Ginawang ImageButton para maiwasan ang ClassCastException
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveItem)

        val btnMinus: Button = view.findViewById(R.id.btnMinus)
        val btnAdd: Button = view.findViewById(R.id.btnAdd)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cart, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val item = cartList[position]

        holder.tvName.text = item.name
        holder.tvPrice.text = String.format(Locale.US, "₱%.2f", item.subtotal) // Display Subtotal
        holder.tvQuantity.text = item.quantity.toString()

        // Remove Button
        holder.btnRemove.setOnClickListener {
            onRemove(item)
        }

        // Minus Button
        holder.btnMinus.setOnClickListener {
            if (item.quantity > 1) {
                onQuantityChange(item, item.quantity - 1)
            } else {
                // Remove item if quantity goes to zero
                onRemove(item)
            }
        }

        // Add Button
        holder.btnAdd.setOnClickListener {
            onQuantityChange(item, item.quantity + 1)
        }
    }

    override fun getItemCount(): Int = cartList.size
}
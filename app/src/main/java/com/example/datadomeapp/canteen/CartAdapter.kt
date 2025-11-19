package com.example.datadomeapp.canteen

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // Import for AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.example.datadomeapp.R
import java.util.*

// --- UTILITY CLASS: Simplifies TextWatcher implementation ---
abstract class SimpleTextWatcher : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}

class CartAdapter(
    private val cartList: MutableList<CartItem>,
    private val onQuantityChange: (CartItem, Int) -> Unit,
    private val onRemove: (CartItem) -> Unit,
    private val maxQuantity: Int // Value is 20
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    inner class CartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvCartItemName)
        val tvSubtotal: TextView = view.findViewById(R.id.tvCartItemPrice)
        val etQuantity: EditText = view.findViewById(R.id.tvCartItemQuantity)

        val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveItem)
        val btnMinus: MaterialButton = view.findViewById(R.id.btnMinus)
        val btnAdd: MaterialButton = view.findViewById(R.id.btnAdd)

        var textWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cart, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val item = cartList[position]
        val context = holder.itemView.context

        // 1. CLEAR: Remove previous TextWatcher
        holder.etQuantity.removeTextChangedListener(holder.textWatcher)

        holder.tvName.text = item.name
        holder.tvSubtotal.text = String.format(Locale.US, "₱%.2f", item.subtotal)

        // Set the initial text
        holder.etQuantity.setText(item.quantity.toString())

        // --- 2. SET UP: TextWatcher for Real-time Value Validation ---

        holder.textWatcher = object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().trim()
                var newQuantity: Int

                if (input.isEmpty()) {
                    // KAHILINGAN: KAPAG BLANK, i-reset sa 1.
                    newQuantity = 1
                    // Gamitin ang post{} para i-delay ang update at panatilihin ang focus
                    holder.etQuantity.post {
                        holder.etQuantity.setText(newQuantity.toString())
                        holder.etQuantity.setSelection(holder.etQuantity.text.length)
                    }
                    // Trigger the update for the model if necessary
                    if (item.quantity != newQuantity) {
                        onQuantityChange(item, newQuantity)
                    }
                    return
                }

                newQuantity = input.toIntOrNull() ?: item.quantity

                var quantityClamped = false

                // Validation 1: Max Quantity (Resets 21+ to 20)
                if (newQuantity > maxQuantity) {
                    newQuantity = maxQuantity
                    quantityClamped = true

                    // Gamitin ang post{} para i-delay ang update at panatilihin ang focus
                    holder.etQuantity.post {
                        holder.etQuantity.setText(newQuantity.toString())
                        holder.etQuantity.setSelection(holder.etQuantity.text.length)
                        Toast.makeText(context, "Maximum quantity of $maxQuantity allowed.", Toast.LENGTH_SHORT).show()
                    }
                }

                // KAHILINGAN: Bawal ang 0 o negative. Force reset sa 1.
                if (newQuantity < 1) {
                    newQuantity = 1
                    quantityClamped = true
                    // Gamitin ang post{} para i-delay ang update at panatilihin ang focus
                    holder.etQuantity.post {
                        holder.etQuantity.setText(newQuantity.toString())
                        holder.etQuantity.setSelection(holder.etQuantity.text.length)
                    }
                }

                // Update the data model if the quantity actually changed
                if (item.quantity != newQuantity) {
                    onQuantityChange(item, newQuantity)
                }
            }
        }
        holder.etQuantity.addTextChangedListener(holder.textWatcher)

        // --- 3. Button Listeners ---

        holder.btnMinus.setOnClickListener {
            val newQuantity = item.quantity - 1
            if (newQuantity >= 1) {
                onQuantityChange(item, newQuantity)
            } else {
                // KAHILINGAN: Kapag minus at naging 0, pilitin itong maging 1.
                onQuantityChange(item, 1)
            }
        }

        holder.btnAdd.setOnClickListener {
            val newQuantity = item.quantity + 1
            if (newQuantity <= maxQuantity) {
                onQuantityChange(item, newQuantity)
            } else {
                Toast.makeText(context, "Maximum quantity of $maxQuantity reached.", Toast.LENGTH_SHORT).show()
            }
        }

        // CONFIRMATION DIALOG FOR REMOVE (X) BUTTON
        holder.btnRemove.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Remove Item?")
                .setMessage("Are you sure you want to remove (${item.name}) from the cart?")
                .setPositiveButton("Yes, Remove") { dialog, which ->
                    // I-trigger ang onRemove callback kung kinumpirma ng user
                    onRemove(item)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, which ->
                    // Isara lang ang dialog
                    dialog.cancel()
                }
                .show()
        }
    }

    override fun getItemCount(): Int = cartList.size

    // I-clear ang listener kapag ni-recycle ang view
    override fun onViewRecycled(holder: CartViewHolder) {
        super.onViewRecycled(holder)
        holder.etQuantity.removeTextChangedListener(holder.textWatcher)
        holder.textWatcher = null
    }
}
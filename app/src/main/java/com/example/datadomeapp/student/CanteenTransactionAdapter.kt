package com.example.datadomeapp.student

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import java.text.SimpleDateFormat
import java.util.*

class CanteenTransactionAdapter(
    private var transactions: List<CanteenTransaction>
) : RecyclerView.Adapter<CanteenTransactionAdapter.ViewHolder>() {

    fun updateList(newList: List<CanteenTransaction>) {
        transactions = newList
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTransaction: TextView = view.findViewById(R.id.tvTransaction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_canteen_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val t = transactions[position]
        val context = holder.itemView.context
        val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)

        val typeUpper = t.type.uppercase(Locale.US)

        val transactionLabel: String
        val colorId: Int

        // Logic para sa Cash In (Positive) at Payments (Negative)
        if (typeUpper == "CASH_IN" || typeUpper == "TOPUP") {
            // Cash In / Topup: Nagdagdag ng pondo
            transactionLabel = "${t.type.replace('_', ' ')}: +₱${String.format("%.2f", t.amount)}"
            colorId = R.color.colorPrimary // I-assume na ito ay Green/Positive color

        } else if (typeUpper == "PURCHASE" || typeUpper == "RFID_PAYMENT") {
            // Payment / Purchase: Bumili
            transactionLabel = "PAYMENT: -₱${String.format("%.2f", t.amount)}"
            colorId = R.color.colorAccent // I-assume na ito ay Red/Negative color

        } else {
            // Unknown type
            transactionLabel = "⚫ ${t.type.replace('_', ' ')}: ₱${String.format("%.2f", t.amount)}"
            colorId = android.R.color.darker_gray
        }

        // I-set ang kulay
        holder.tvTransaction.setTextColor(ContextCompat.getColor(context, colorId))

        // I-set ang kumpletong text
        holder.tvTransaction.text =
            "$transactionLabel\n" +
                    "Item: ${t.itemName}\n" +
                    "Date: ${sdf.format(t.timestamp)} | Bal: ₱${String.format("%.2f", t.finalBalance)}"
    }

    override fun getItemCount() = transactions.size
}

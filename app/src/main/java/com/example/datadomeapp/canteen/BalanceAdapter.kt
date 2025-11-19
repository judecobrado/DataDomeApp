package com.example.datadomeapp.canteen

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import java.util.Locale

// Data Model (Inilipat dito para mas madaling gamitin)
data class BalanceItem(
    // ❌ Tinanggal ang accountId dahil hindi na kailangan para sa display
    val uid: String, // Para sa history lookup
    val name: String,
    val role: String,
    val balance: Double
)

class BalanceAdapter(
    private var users: List<BalanceItem>,
    private val clickListener: (BalanceItem) -> Unit // Click handler para sa history
) : RecyclerView.Adapter<BalanceAdapter.BalanceViewHolder>() {

    fun updateList(newList: List<BalanceItem>) {
        users = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BalanceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_balance_user, parent, false)
        return BalanceViewHolder(view)
    }

    override fun onBindViewHolder(holder: BalanceViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user, clickListener)
    }

    override fun getItemCount(): Int = users.size

    class BalanceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Tiyaking ito ay ang tamang ID na ginamit sa XML (e.g., tvUserInfo)
        private val tvUserName: TextView = itemView.findViewById(R.id.tvUserInfo)
        // ❌ Tinanggal ang tvAccountId dahil hindi na ito ipapakita
        // private val tvAccountId: TextView = itemView.findViewById(R.id.tvAccountId)
        private val tvBalanceAmount: TextView = itemView.findViewById(R.id.tvBalanceAmount)

        fun bind(user: BalanceItem, clickListener: (BalanceItem) -> Unit) {

            // 🆕 PAGBABAGO: Hindi na kasama ang accountId sa display
            tvUserName.text = "${user.name}\n(${user.role})"

            // ❌ Tinanggal ang linya para sa Account ID display
            // tvAccountId.text = "ID: ${user.accountId}"

            // Format Balanse
            tvBalanceAmount.text = "₱${String.format(Locale.US, "%.2f", user.balance)}"

            // Click listener para mapunta sa History
            itemView.setOnClickListener {
                clickListener(user)
            }
        }
    }
}
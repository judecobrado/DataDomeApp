package com.example.datadomeapp.canteen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

// --- TRANSACTION DATA MODEL ---
data class TransactionItem(
    val id: String,
    val type: String, // e.g., CASH_IN
    val amount: Double,
    val finalBalance: Double,
    val timestamp: Date
)

// --- RECYCLERVIEW ADAPTER ---
class TransactionAdapter(private var transactions: List<TransactionItem>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm a", Locale.US)

    fun updateList(newList: List<TransactionItem>) {
        transactions = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction_history, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position], dateFormatter)
    }

    override fun getItemCount(): Int = transactions.size

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTransactionType: TextView = itemView.findViewById(R.id.tvTransactionType)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvFinalBalance: TextView = itemView.findViewById(R.id.tvFinalBalance)

        fun bind(item: TransactionItem, dateFormatter: SimpleDateFormat) {
            // Display Amount and Type
            tvTransactionType.text = "${item.type}: ₱${String.format(Locale.US, "%.2f", item.amount)}"

            // Display Timestamp
            tvTime.text = dateFormatter.format(item.timestamp)

            // Display Final Balance
            tvFinalBalance.text = "₱${String.format(Locale.US, "%.2f", item.finalBalance)}"

            // Highlight CASH-IN (Green)
            if (item.type == "CASH_IN") {
                // ⭐ FIX 2: Paggamit ng ContextCompat.getColor o mas simple, ang deprecated version na may null check
                tvTransactionType.setTextColor(itemView.context.resources.getColor(R.color.colorPrimary, null))
            } else {
                // ⭐ FIX 3: Paggamit ng ContextCompat.getColor o mas simple, ang deprecated version na may null check
                tvTransactionType.setTextColor(itemView.context.resources.getColor(R.color.colorAccent, null))
            }
        }
    }
}


// --- ACTIVITY LOGIC ---
class UserHistoryActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    // UI Elements
    private lateinit var tvHistoryUserName: TextView
    private lateinit var recyclerViewHistory: RecyclerView
    private lateinit var historyProgressBar: ProgressBar
    private lateinit var tvNoHistory: TextView

    private lateinit var adapter: TransactionAdapter
    private var userUid: String? = null
    private var userName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_history)

        // Get data from BalanceInquiryActivity
        userUid = intent.getStringExtra("userUID")
        userName = intent.getStringExtra("userName")

        initializeViews()

        if (userUid.isNullOrEmpty()) {
            Toast.makeText(this, "Error: User ID not provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvHistoryUserName.text = "History for: $userName"
        loadTransactionHistory(userUid!!)
    }

    private fun initializeViews() {
        tvHistoryUserName = findViewById(R.id.tvHistoryUserName)
        recyclerViewHistory = findViewById(R.id.recyclerViewHistory)
        historyProgressBar = findViewById(R.id.historyProgressBar)
        tvNoHistory = findViewById(R.id.tvNoHistory)

        recyclerViewHistory.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter(emptyList())
        recyclerViewHistory.adapter = adapter
    }

    private fun loadTransactionHistory(uid: String) {
        historyProgressBar.visibility = View.VISIBLE
        tvNoHistory.visibility = View.GONE

        // Query the 'transactions' collection filtered by userId and ordered by timestamp (descending)
        firestore.collection("transactions")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                historyProgressBar.visibility = View.GONE

                if (querySnapshot.isEmpty) {
                    tvNoHistory.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                val transactions = mutableListOf<TransactionItem>()
                for (doc in querySnapshot.documents) {
                    // FIX 1: Gumagamit ng Timestamp object mula sa Firestore
                    val timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()

                    transactions.add(
                        TransactionItem(
                            id = doc.id,
                            type = doc.getString("type") ?: "UNKNOWN",
                            amount = doc.getDouble("amount") ?: 0.0,
                            finalBalance = doc.getDouble("finalBalance") ?: 0.0,
                            timestamp = timestamp
                        )
                    )
                }

                adapter.updateList(transactions)
            }
            .addOnFailureListener { e ->
                historyProgressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading history: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
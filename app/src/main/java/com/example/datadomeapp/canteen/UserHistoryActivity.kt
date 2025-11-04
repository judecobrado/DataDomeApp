package com.example.datadomeapp.canteen

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

// --- TRANSACTION DATA MODEL ---
data class TransactionItem(
    val id: String,
    val type: String,
    val amount: Double,
    val finalBalance: Double,
    val timestamp: Date
)

// --- RECYCLERVIEW ADAPTER ---
class TransactionAdapter(private var transactions: List<TransactionItem>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)

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
            val context = itemView.context

            // Display Amount and Type
            tvTransactionType.text = "${item.type}: ₱${String.format(Locale.US, "%.2f", item.amount)}"

            // Display Timestamp
            tvTime.text = dateFormatter.format(item.timestamp)

            // Display Final Balance
            tvFinalBalance.text = "₱${String.format(Locale.US, "%.2f", item.finalBalance)}"

            val type = item.type.uppercase(Locale.US)

            // COLOR FIX: Gumagamit ng ContextCompat para sa tamang kulay at mayroong logic para sa iba't ibang type
            if (type == "CASH_IN" || type == "TOPUP") {
                tvTransactionType.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
            } else if (type == "PURCHASE" || type == "RFID_PAYMENT") {
                tvTransactionType.setTextColor(ContextCompat.getColor(context, R.color.colorAccent))
            } else {
                tvTransactionType.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            }
        }
    }
}


// --- ACTIVITY LOGIC ---
class UserHistoryActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    // UI Elements
    private lateinit var tvHistoryUserName: TextView
    private lateinit var btnSelectDate: Button
    private lateinit var tvDateRange: TextView
    private lateinit var tvMonthlySummary: TextView
    private lateinit var recyclerViewHistory: RecyclerView
    private lateinit var historyProgressBar: ProgressBar
    private lateinit var tvNoHistory: TextView

    private lateinit var adapter: TransactionAdapter
    private var userUid: String? = null
    private var userName: String? = null

    // Date Range Filter Variables
    private var startDate: Date? = null
    private var endDate: Date? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_history)

        // Get data from BalanceInquiryActivity
        userUid = intent.getStringExtra("userUID")
        userName = intent.getStringExtra("userName")

        initializeViews()
        setupListeners()

        if (userUid.isNullOrEmpty()) {
            Toast.makeText(this, "Error: User ID not provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        tvHistoryUserName.text = "History for: $userName"

        // Simulan ang proseso sa pag-set ng default filter at pag-load ng data
        setDefault30DayFilter(userUid!!)
    }

    private fun initializeViews() {
        tvHistoryUserName = findViewById(R.id.tvHistoryUserName)
        recyclerViewHistory = findViewById(R.id.recyclerViewHistory)
        historyProgressBar = findViewById(R.id.historyProgressBar)
        tvNoHistory = findViewById(R.id.tvNoHistory)

        btnSelectDate = findViewById(R.id.btnSelectDate)
        tvDateRange = findViewById(R.id.tvDateRange)
        tvMonthlySummary = findViewById(R.id.tvMonthlySummary)

        recyclerViewHistory.layoutManager = LinearLayoutManager(this)
        adapter = TransactionAdapter(emptyList())
        recyclerViewHistory.adapter = adapter
    }

    private fun setupListeners() {
        btnSelectDate.setOnClickListener {
            showDateRangePicker()
        }
    }

    private fun setDefault30DayFilter(uid: String) {
        val currentCalendar = Calendar.getInstance()

        // 1. Set End Date to the end of the current day (23:59:59)
        currentCalendar.set(Calendar.HOUR_OF_DAY, 23)
        currentCalendar.set(Calendar.MINUTE, 59)
        currentCalendar.set(Calendar.SECOND, 59)
        currentCalendar.set(Calendar.MILLISECOND, 999)
        endDate = currentCalendar.time

        // 2. Set Start Date to 30 days ago at the beginning of that day (00:00:00)
        currentCalendar.add(Calendar.DAY_OF_MONTH, -30) // Move back 30 days
        currentCalendar.set(Calendar.HOUR_OF_DAY, 0)
        currentCalendar.set(Calendar.MINUTE, 0)
        currentCalendar.set(Calendar.SECOND, 0)
        currentCalendar.set(Calendar.MILLISECOND, 0)
        startDate = currentCalendar.time

        // I-update ang UI at i-load ang data
        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        tvDateRange.text = "Default Filter: ${formatter.format(startDate!!)} - ${formatter.format(endDate!!)}"

        loadTransactionHistory(uid)
    }


    // ⭐ MODIFIED FUNCTION: Inayos ang Calendar Constraints
    private fun showDateRangePicker() {
        val today = MaterialDatePicker.todayInUtcMilliseconds()

        // 1. Kalkulahin ang petsa 30 araw na ang nakalipas
        val calendar30DaysAgo = Calendar.getInstance()
        calendar30DaysAgo.add(Calendar.DAY_OF_MONTH, -30)
        calendar30DaysAgo.set(Calendar.HOUR_OF_DAY, 0)
        calendar30DaysAgo.set(Calendar.MINUTE, 0)
        calendar30DaysAgo.set(Calendar.SECOND, 0)
        calendar30DaysAgo.set(Calendar.MILLISECOND, 0)
        val thirtyDaysAgoTime = calendar30DaysAgo.timeInMillis

        // 2. I-set ang constraints: start date ay 30 days ago, end date ay ngayon
        val constraints = CalendarConstraints.Builder()
            .setStart(thirtyDaysAgoTime) // Ito ang pinakamalayo na pwedeng piliin
            .setEnd(today)               // Ito ang pinakamalapit na pwedeng piliin (current day)
            .setValidator(DateValidatorPointBackward.now()) // Optional: Para lang mag-validate against future dates
            .build()

        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range (Last 30 Days)")
            .setCalendarConstraints(constraints)
            .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection ->

            val startSelection = selection.first
            val endSelection = selection.second

            if (startSelection == null || endSelection == null || startSelection > endSelection) {
                Toast.makeText(this, "Invalid date range selected. Please try again.", Toast.LENGTH_LONG).show()
                return@addOnPositiveButtonClickListener
            }

            // I-set ang Start Date sa 00:00:00
            val startCalendar = Calendar.getInstance().apply { timeInMillis = startSelection }
            startCalendar.set(Calendar.HOUR_OF_DAY, 0)
            startCalendar.set(Calendar.MINUTE, 0)
            startCalendar.set(Calendar.SECOND, 0)
            startCalendar.set(Calendar.MILLISECOND, 0)
            startDate = startCalendar.time

            // I-set ang End Date sa 23:59:59
            val endCalendar = Calendar.getInstance().apply { timeInMillis = endSelection }
            endCalendar.set(Calendar.HOUR_OF_DAY, 23)
            endCalendar.set(Calendar.MINUTE, 59)
            endCalendar.set(Calendar.SECOND, 59)
            endCalendar.set(Calendar.MILLISECOND, 999)
            endDate = endCalendar.time

            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            tvDateRange.text = "Custom Filter: ${formatter.format(startDate!!)} - ${formatter.format(endDate!!)}"

            // Reload data
            loadTransactionHistory(userUid!!)
        }

        dateRangePicker.show(supportFragmentManager, "DATE_RANGE_PICKER")
    }

    private fun loadTransactionHistory(uid: String) {
        historyProgressBar.visibility = View.VISIBLE
        tvNoHistory.visibility = View.GONE
        tvMonthlySummary.text = "Calculating Summary..."

        var query: Query = firestore.collection("transactions")
            // ✅ FINAL FIX: Ginamit ang "userId" (maliit na d)
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        // Apply the Date Range Filter
        if (startDate != null && endDate != null) {
            query = query
                .whereGreaterThanOrEqualTo("timestamp", startDate!!)
                .whereLessThanOrEqualTo("timestamp", endDate!!)
        }

        Log.d("HistoryQuery", "UID: $uid | Start: $startDate | End: $endDate")

        query.get()
            .addOnSuccessListener { querySnapshot ->
                historyProgressBar.visibility = View.GONE

                if (querySnapshot.isEmpty) {
                    tvNoHistory.visibility = View.VISIBLE
                    tvMonthlySummary.text = "Cash In: ₱0.00 | Payments: ₱0.00"
                    adapter.updateList(emptyList())
                    return@addOnSuccessListener
                }

                val transactions = mutableListOf<TransactionItem>()
                var totalCashIn = 0.0
                var totalPayments = 0.0

                for (doc in querySnapshot.documents) {
                    val timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                    val type = doc.getString("type") ?: "UNKNOWN"
                    val amount = doc.getDouble("amount") ?: 0.0

                    // Calculation for Summary
                    val upperType = type.uppercase(Locale.US)
                    if (upperType == "CASH_IN" || upperType == "TOPUP") {
                        totalCashIn += amount
                    } else if (upperType == "PURCHASE" || upperType == "RFID_PAYMENT") {
                        totalPayments += amount
                    }

                    transactions.add(
                        TransactionItem(
                            id = doc.id,
                            type = type,
                            amount = amount,
                            finalBalance = doc.getDouble("finalBalance") ?: 0.0,
                            timestamp = timestamp
                        )
                    )
                }

                tvMonthlySummary.text =
                    "Cash In: ₱${String.format(Locale.US, "%.2f", totalCashIn)} | " +
                            "Payments: ₱${String.format(Locale.US, "%.2f", totalPayments)}"


                adapter.updateList(transactions)
            }
            .addOnFailureListener { e ->
                historyProgressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading history: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("HistoryQuery", "Error: ${e.message}", e)
            }
    }
}
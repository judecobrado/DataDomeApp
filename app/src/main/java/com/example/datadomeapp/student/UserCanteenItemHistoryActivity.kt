package com.example.datadomeapp.student

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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

// Data Model para sa Canteen Item Transaction
data class CanteenTransaction(
    val id: String,
    val type: String,          // ✅ NEW: Para malaman kung Cash In o Payment
    val itemName: String,
    val amount: Double,
    val finalBalance: Double,
    val timestamp: Date
)

class UserCanteenItemHistoryActivity : AppCompatActivity() {

    private val TAG = "UserItemHistory"

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var rvHistory: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoHistory: TextView
    private lateinit var btnSelectDate: Button
    private lateinit var tvDateRange: TextView

    private lateinit var adapter: CanteenTransactionAdapter
    private var accountId: String = "" // DDS-XXXX or T-XXXX
    private var itemName: String? = null

    private var startDate: Date? = null
    private var endDate: Date? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_canteen_history)

        accountId = intent.getStringExtra("STUDENT_ID") ?: ""
        // Ang itemName ay gagamitin na lang sa client-side filtering kung kailangan
        itemName = intent.getStringExtra("ITEM_NAME")

        rvHistory = findViewById(R.id.rvHistory)
        progressBar = findViewById(R.id.progressBar)
        tvNoHistory = findViewById(R.id.tvNoHistory)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        tvDateRange = findViewById(R.id.tvDateRange)

        adapter = CanteenTransactionAdapter(emptyList())
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        btnSelectDate.setOnClickListener { showDateRangePicker() }

        if (accountId.isEmpty()) {
            Toast.makeText(this, "Error: Account ID is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        title = "Full History for: ${accountId}"

        setDefault30DayFilter()
    }

    private fun setDefault30DayFilter() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        endDate = cal.time

        cal.add(Calendar.DAY_OF_MONTH, -30)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        startDate = cal.time

        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        tvDateRange.text = "${formatter.format(startDate!!)} - ${formatter.format(endDate!!)}"

        loadHistory()
    }

    private fun showDateRangePicker() {
        // ... (Date Picker implementation remains the same)
        val today = MaterialDatePicker.todayInUtcMilliseconds()

        val calendar30DaysAgo = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_MONTH, -30)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val thirtyDaysAgoTime = calendar30DaysAgo.timeInMillis

        val constraints = CalendarConstraints.Builder()
            .setStart(thirtyDaysAgoTime)
            .setEnd(today)
            .setValidator(DateValidatorPointBackward.now())
            .build()

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range (Last 30 Days)")
            .setCalendarConstraints(constraints)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val startSelection = selection.first ?: return@addOnPositiveButtonClickListener
            val endSelection = selection.second ?: return@addOnPositiveButtonClickListener

            val startCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = startSelection }
            startCalendar.set(Calendar.HOUR_OF_DAY, 0)
            startCalendar.set(Calendar.MINUTE, 0)
            startCalendar.set(Calendar.SECOND, 0)
            startCalendar.set(Calendar.MILLISECOND, 0)
            startDate = startCalendar.time

            val endCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = endSelection }
            endCalendar.set(Calendar.HOUR_OF_DAY, 23)
            endCalendar.set(Calendar.MINUTE, 59)
            endCalendar.set(Calendar.SECOND, 59)
            endCalendar.set(Calendar.MILLISECOND, 999)
            endDate = endCalendar.time

            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            tvDateRange.text = "${sdf.format(startDate!!)} - ${sdf.format(endDate!!)}"

            loadHistory()
        }

        picker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun loadHistory() {
        if (accountId.isEmpty()) return

        progressBar.visibility = View.VISIBLE
        tvNoHistory.visibility = View.GONE

        var query: Query = firestore.collection("transactions")
            .whereEqualTo("accountId", accountId)

        startDate?.let { start ->
            endDate?.let { end ->
                query = query
                    .whereGreaterThanOrEqualTo("timestamp", start)
                    .whereLessThanOrEqualTo("timestamp", end)
            }
        }

        query = query.orderBy("timestamp", Query.Direction.DESCENDING)

        query.get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = View.GONE

                val rawTransactions = mutableListOf<CanteenTransaction>()

                for (doc in snapshot.documents) {
                    val type = doc.getString("type") ?: "UNKNOWN"
                    val finalBalance = doc.getDouble("finalBalance") ?: 0.0
                    val timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()

                    val upperType = type.uppercase(Locale.US)

                    if (upperType == "PURCHASE" || upperType == "RFID_PAYMENT") {
                        // Multi-item purchase
                        val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()

                        if (items.isEmpty()) {
                            // Single item or old format transaction (Fallback)
                            rawTransactions.add(
                                CanteenTransaction(
                                    id = doc.id,
                                    type = type,
                                    itemName = doc.getString("itemName") ?: "N/A (Full Payment)",
                                    amount = doc.getDouble("amount") ?: 0.0,
                                    finalBalance = finalBalance,
                                    timestamp = timestamp
                                )
                            )
                        } else {
                            // Create a record for each item
                            items.forEach { itemMap ->
                                val name = itemMap["name"] as? String ?: "Unknown Item"
                                val price = (itemMap["price"] as? Number)?.toDouble() ?: 0.0
                                val quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 1

                                rawTransactions.add(
                                    CanteenTransaction(
                                        id = doc.id,
                                        type = type,
                                        itemName = "$name (x$quantity)",
                                        amount = price * quantity,
                                        finalBalance = finalBalance,
                                        timestamp = timestamp
                                    )
                                )
                            }
                        }
                    } else if (upperType == "CASH_IN" || upperType == "TOPUP") {
                        // Cash In or Topup transaction
                        rawTransactions.add(
                            CanteenTransaction(
                                id = doc.id,
                                type = type,
                                itemName = "Load/Deposit Transaction", // Generic description
                                amount = doc.getDouble("amount") ?: 0.0,
                                finalBalance = finalBalance,
                                timestamp = timestamp
                            )
                        )
                    }
                }

                // Apply optional client-side filtering for specific item name
                val filteredList = if (itemName.isNullOrEmpty()) {
                    rawTransactions
                } else {
                    rawTransactions.filter {
                        it.itemName.startsWith(itemName!!, ignoreCase = true)
                    }
                }

                if (filteredList.isEmpty()) {
                    tvNoHistory.visibility = View.VISIBLE
                } else {
                    tvNoHistory.visibility = View.GONE
                }

                adapter.updateList(filteredList)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load history: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error loading history", e)
            }
    }
}

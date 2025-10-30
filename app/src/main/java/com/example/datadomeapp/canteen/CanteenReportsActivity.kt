package com.example.datadomeapp.canteen

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import com.github.mikephil.charting.charts.LineChart // LineChart for Dual-Axis
import com.google.firebase.firestore.PropertyName
import java.util.*
import com.github.mikephil.charting.charts.BarChart // Keep if used elsewhere, otherwise remove

// Data Models
data class Order(
    val id: String = "",
    val timestamp: Date = Date(),
    @PropertyName("amount")
    val totalAmount: Double = 0.0,
    val items: List<OrderItem> = emptyList(),
    val cashierName: String = "",
    val paymentMethod: String = ""
)

data class OrderItem(
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 0
)

// Data Model para sa Graph - May dalawang value
data class DailySales(
    val date: String,
    val totalSales: Double,      // Pera (Left Axis)
    val totalQuantitySold: Int   // Dami (Right Axis)
)

class CanteenReportsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "CanteenReportsActivity"

    // Overview Views
    private lateinit var tvTotalSales: TextView
    private lateinit var tvTotalOrders: TextView
    private lateinit var tvTopSellingItem: TextView
    private lateinit var tvDateRange: TextView      // For filter label
    private lateinit var btnFilterDate: Button      // Filter button
    private lateinit var tvSalesChartTitle: TextView // NEW: Para sa dynamic chart title

    // Graph View
    private lateinit var salesChart: LineChart // Ibinabalik sa LineChart para sa Dual-Axis

    // Order List Views
    private lateinit var rvOrders: RecyclerView
    private val orderList = mutableListOf<Order>()
    private lateinit var orderAdapter: OrderAdapter

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_canteen_reports)

        supportActionBar?.title = "Sales & Order Reports"

        // Initialize Views
        tvTotalSales = findViewById(R.id.tvTotalSales)
        tvTotalOrders = findViewById(R.id.tvTotalOrders)
        tvTopSellingItem = findViewById(R.id.tvTopSellingItem)
        tvDateRange = findViewById(R.id.tvDateRange)
        btnFilterDate = findViewById(R.id.btnFilterDate)
        salesChart = findViewById(R.id.salesChart) // LineChart na ulit
        tvSalesChartTitle = findViewById(R.id.tvSalesChartTitle) // NEW: Initialize dynamic title

        // Initialize RecyclerView
        rvOrders = findViewById(R.id.rvOrderList)
        orderAdapter = OrderAdapter(orderList)
        rvOrders.layoutManager = LinearLayoutManager(this)
        rvOrders.adapter = orderAdapter

        SalesChartHelper.initializeChart(salesChart)
        loadTodayOrders()

        btnFilterDate.setOnClickListener {
            showDateFilterDialog()
        }
    }


    // ------------------- DATA LOADING & FILTERING LOGIC -------------------

    private fun loadTodayOrders() {
        val calendar = Calendar.getInstance()

        // Start: Simula ng araw (00:00:00)
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.time

        // End: Katapusan ng araw (23:59:59.999)
        calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59); calendar.set(Calendar.MILLISECOND, 999)
        val endOfToday = calendar.time

        val label = "Today (${dateFormatter.format(startOfToday)})"
        loadOrders(startOfToday, endOfToday, label)
    }

    private fun loadOrders(start: Date, end: Date, filterLabel: String) {
        tvDateRange.text = "Showing results for: $filterLabel"

        // Update dynamic chart title
        val chartTitle = filterLabel.substringBefore(" (")
        tvSalesChartTitle.text = "Sales & Quantity Trend ($chartTitle)" // Dynamic Title

        firestore.collection("transactions")
            .whereEqualTo("type", "CASH_OUT") // Only sales transactions
            .whereGreaterThanOrEqualTo("timestamp", start)
            .whereLessThanOrEqualTo("timestamp", end)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                orderList.clear()

                val orders = snapshot.documents.mapNotNull { doc ->
                    // Manual extraction ng 'amount' field
                    val amountValue = doc.get("amount")
                    val totalAmount = when (amountValue) {
                        is Number -> amountValue.toDouble()
                        is String -> amountValue.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }

                    doc.toObject(Order::class.java)?.copy(
                        id = doc.id,
                        totalAmount = totalAmount
                    )
                }

                orderList.addAll(orders)
                orderAdapter.notifyDataSetChanged()

                calculateAndDisplaySummary(orders)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load reports: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error loading orders for report", e)
            }
    }

    // ------------------- DATE FILTER DIALOG (SAME) -------------------

    private fun showDateFilterDialog() {
        val options = arrayOf("Today", "Yesterday", "This Week", "This Month")

        AlertDialog.Builder(this)
            .setTitle("Filter Sales Report")
            .setItems(options) { dialog, which ->
                when (options[which]) {
                    "Today" -> loadTodayOrders()
                    "Yesterday" -> loadYesterdayOrders()
                    "This Week" -> loadCurrentPeriodOrders(Calendar.WEEK_OF_YEAR, "This Week")
                    "This Month" -> loadCurrentPeriodOrders(Calendar.MONTH, "This Month")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadYesterdayOrders() {
        val calendar = Calendar.getInstance()

        // End Date: Katapusan ng kahapon (23:59:59.999)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59); calendar.set(Calendar.MILLISECOND, 999)
        val endYesterday = calendar.time

        // Start Date: Simula ng kahapon (00:00:00)
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        val startYesterday = calendar.time

        val label = "Yesterday (${dateFormatter.format(startYesterday)})"
        loadOrders(startYesterday, endYesterday, label)
    }

    private fun loadCurrentPeriodOrders(periodField: Int, label: String) {
        val calendar = Calendar.getInstance()

        // End Date: Katapusan ng Araw na ito (23:59:59.999)
        calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59); calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        // Start Date: Simula ng Week/Month (00:00:00)
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)

        if (periodField == Calendar.WEEK_OF_YEAR) {
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        } else if (periodField == Calendar.MONTH) {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
        }
        val startDate = calendar.time

        val filterLabel = "$label (${dateFormatter.format(startDate)} - ${dateFormatter.format(endDate)})"
        loadOrders(startDate, endDate, filterLabel)
    }

    // ------------------- SUMMARY AND GRAPHICS -------------------

    private fun calculateAndDisplaySummary(orders: List<Order>) {
        var totalSales = 0.0
        val itemSalesMap = mutableMapOf<String, Int>()

        for (order in orders) {
            totalSales += order.totalAmount

            for (item in order.items) {
                itemSalesMap[item.name] = itemSalesMap.getOrDefault(item.name, 0) + item.quantity
            }
        }

        val topItem = itemSalesMap.maxByOrNull { it.value }

        tvTotalSales.text = String.format(Locale.US, "₱%.2f", totalSales)
        tvTotalOrders.text = orders.size.toString()
        tvTopSellingItem.text = if (topItem != null) "${topItem.key} (${topItem.value} sold)" else "N/A"

        generateSalesGraphData(orders)
    }

    /**
     * Gumagawa ng data para sa Line Chart, na may dalawang set (Sales at Quantity).
     */
    private fun generateSalesGraphData(orders: List<Order>) {
        val keyFormatter = SimpleDateFormat("MM-dd", Locale.US)

        // Map: Key=Date (MM-dd), Value=Pair<Total Sales, Total Quantity>
        val dailyDataMap = mutableMapOf<String, Pair<Double, Int>>()

        for (order in orders) {
            val dayKey = keyFormatter.format(order.timestamp)
            val orderQuantity = order.items.sumOf { it.quantity }

            val currentPair = dailyDataMap.getOrDefault(dayKey, Pair(0.0, 0))

            dailyDataMap[dayKey] = Pair(
                currentPair.first + order.totalAmount,
                currentPair.second + orderQuantity
            )
        }

        val sortFormatter = SimpleDateFormat("MM-dd", Locale.US)

        // Convert to DailySales List at i-sort chronologically
        val dailySalesList = dailyDataMap.map { (date, data) ->
            DailySales(date, data.first, data.second)
        }.sortedBy {
            sortFormatter.parse(it.date)?.time ?: 0L
        }

        // TAWAGIN ANG PLOTTING FUNCTION
        SalesChartHelper.plotDualAxisChart(salesChart, dailySalesList) // UPDATED HELPER FUNCTION

        Log.d(TAG, "Daily Sales Data Prepared: $dailySalesList")
    }
}

// -----------------------------
// RecyclerView Adapter for Orders (No change needed)
// -----------------------------
class OrderAdapter(private val items: List<Order>) :
    RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    inner class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvOrderId: TextView = view.findViewById(R.id.tvOrderId)
        val tvOrderTime: TextView = view.findViewById(R.id.tvOrderTime)
        val tvOrderTotal: TextView = view.findViewById(R.id.tvOrderTotal)
        val tvItemDetails: TextView = view.findViewById(R.id.tvItemDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_report, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = items[position]

        // Display short ID and time
        holder.tvOrderId.text = "Order #${order.id.substring(0, 6).toUpperCase(Locale.US)}"
        holder.tvOrderTime.text = timeFormatter.format(order.timestamp)
        holder.tvOrderTotal.text = String.format(Locale.US, "₱%.2f", order.totalAmount)

        // Item details summary
        val itemSummary = order.items.joinToString(separator = ", ") { item ->
            "${item.quantity}x ${item.name} (₱${String.format(Locale.US, "%.2f", item.price)})"
        }
        holder.tvItemDetails.text = itemSummary
    }

    override fun getItemCount(): Int = items.size
}
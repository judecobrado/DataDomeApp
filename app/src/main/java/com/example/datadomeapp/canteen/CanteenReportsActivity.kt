package com.example.datadomeapp.canteen

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.DatePicker
import android.widget.ProgressBar
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
import com.github.mikephil.charting.charts.LineChart
import com.google.firebase.firestore.PropertyName
import java.util.*
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout // CRITICAL: Import for SwipeRefreshLayout

// Data Models (No Changes)
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

data class DailySales(
    val date: String,
    val totalSales: Double,
    val totalQuantitySold: Int
)

class CanteenReportsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "CanteenReportsActivity"

    // Overview Views
    private lateinit var tvTotalSales: TextView
    private lateinit var tvTotalOrders: TextView
    private lateinit var tvTopSellingItem: TextView
    private lateinit var tvDateRange: TextView
    private lateinit var btnFilterDate: Button
    private lateinit var tvSalesChartTitle: TextView

    // NEW: Swipe Refresh Layout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // Loading Indicator
    private var progressBar: ProgressBar? = null
    private var tvLoadingMessage: TextView? = null

    // Graph View
    private lateinit var salesChart: LineChart

    // Order List Views
    private lateinit var rvOrders: RecyclerView
    private val orderList = mutableListOf<Order>()
    private lateinit var orderAdapter: OrderAdapter

    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    // NEW: Variables to store the currently loaded date range
    private var currentStartDate: Date? = null
    private var currentEndDate: Date? = null
    private var currentFilterLabel: String = "Today" // default value

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_canteen_reports)

        supportActionBar?.title = "Sales & Order Reports"

        // Initialize ALL Views FIRST before using them
        tvTotalSales = findViewById(R.id.tvTotalSales)
        tvTotalOrders = findViewById(R.id.tvTotalOrders)
        tvTopSellingItem = findViewById(R.id.tvTopSellingItem)
        tvDateRange = findViewById(R.id.tvDateRange)
        btnFilterDate = findViewById(R.id.btnFilterDate)
        salesChart = findViewById(R.id.salesChart)
        tvSalesChartTitle = findViewById(R.id.tvSalesChartTitle)
        rvOrders = findViewById(R.id.rvOrderList)

        // NEW: Initialize SwipeRefreshLayout
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        // Initialize Loading Views - CRITICAL: Must be initialized before loadTodayOrders()
        progressBar = findViewById(R.id.progressBar)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)

        // Initialize RecyclerView
        orderAdapter = OrderAdapter(orderList)
        rvOrders.layoutManager = LinearLayoutManager(this)
        rvOrders.adapter = orderAdapter

        // Initialize chart
        SalesChartHelper.initializeChart(salesChart)

        // Set button listener
        btnFilterDate.setOnClickListener {
            showDateFilterDialog()
        }

        // NEW: Setup Swipe-to-Refresh Listener
        swipeRefreshLayout.setOnRefreshListener {
            // Re-load the data based on the currently applied filter
            refreshCurrentReport()
        }

        // Load data LAST - after everything is initialized
        loadTodayOrders()
    }

    // ------------------- LOADING INDICATOR FUNCTIONS -------------------

    private fun showLoading(message: String = "Loading reports...") {
        // Only show the central ProgressBar if it wasn't triggered by a swipe (which uses its own indicator)
        if (!swipeRefreshLayout.isRefreshing) {
            progressBar?.visibility = View.VISIBLE
            tvLoadingMessage?.visibility = View.VISIBLE
            tvLoadingMessage?.text = message
        }

        // Hide content while loading
        rvOrders.visibility = View.GONE
        salesChart.visibility = View.GONE
    }

    private fun hideLoading() {
        // Stop the SwipeRefresh animation
        if (swipeRefreshLayout.isRefreshing) {
            swipeRefreshLayout.isRefreshing = false
        }

        progressBar?.visibility = View.GONE
        tvLoadingMessage?.visibility = View.GONE

        // Show content after loading
        rvOrders.visibility = View.VISIBLE
        salesChart.visibility = View.VISIBLE
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
        // 1. NEW: SAVE the current filter state
        currentStartDate = start
        currentEndDate = end
        currentFilterLabel = filterLabel

        // Show loading indicator
        showLoading("Loading $filterLabel reports...")

        tvDateRange.text = "Showing results for: $filterLabel"

        // Update dynamic chart title
        val chartTitle = filterLabel.substringBefore(" (")
        tvSalesChartTitle.text = "Sales & Quantity Trend ($chartTitle)"

        // 1. Query for RFID Payments
        val rfidQuery = firestore.collection("transactions")
            .whereEqualTo("type", "RFID_PAYMENT")
            .whereGreaterThanOrEqualTo("timestamp", start)
            .whereLessThanOrEqualTo("timestamp", end)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        // 2. Query for Cash Payments
        val cashQuery = firestore.collection("transactions")
            .whereEqualTo("type", "CASH_PAYMENT")
            .whereGreaterThanOrEqualTo("timestamp", start)
            .whereLessThanOrEqualTo("timestamp", end)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        // Mag-fetch ng RFID transactions
        rfidQuery.get().addOnSuccessListener { rfidSnapshot ->
            val rfidOrders = rfidSnapshot.documents.mapNotNull { doc ->
                val totalAmount = (doc.get("amount") as? Number)?.toDouble() ?: doc.getString("amount")?.toDoubleOrNull() ?: 0.0

                doc.toObject(Order::class.java)?.copy(
                    id = doc.id,
                    totalAmount = totalAmount,
                    paymentMethod = "RFID"
                )
            }

            // Mag-fetch ng Cash transactions
            cashQuery.get().addOnSuccessListener { cashSnapshot ->
                val cashOrders = cashSnapshot.documents.mapNotNull { doc ->
                    val totalAmount = (doc.get("amount") as? Number)?.toDouble() ?: doc.getString("amount")?.toDoubleOrNull() ?: 0.0

                    doc.toObject(Order::class.java)?.copy(
                        id = doc.id,
                        totalAmount = totalAmount,
                        paymentMethod = "Cash"
                    )
                }

                // Pagsasamahin, i-sort, at i-display
                val combinedOrders = (rfidOrders + cashOrders)
                    .sortedByDescending { it.timestamp }

                orderList.clear()
                orderList.addAll(combinedOrders)
                orderAdapter.notifyDataSetChanged()

                calculateAndDisplaySummary(combinedOrders)

                // Hide loading after data is loaded
                hideLoading()
            }
                .addOnFailureListener { e ->
                    hideLoading()
                    Toast.makeText(this, "Failed to load cash reports: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Error loading cash orders for report", e)
                }
        }
            .addOnFailureListener { e ->
                hideLoading()
                Toast.makeText(this, "Failed to load RFID reports: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error loading RFID orders for report", e)
            }
    }

    // NEW: Function to re-load the last applied filter
    private fun refreshCurrentReport() {
        val start = currentStartDate
        val end = currentEndDate
        val label = currentFilterLabel

        if (start != null && end != null) {
            // Re-load the data using the last applied filter dates
            loadOrders(start, end, label)
        } else {
            // If no filter was applied yet, load the default (Today)
            loadTodayOrders()
        }
    }


    // ------------------- DATE FILTER DIALOGS -------------------

    private fun showDateFilterDialog() {
        val options = arrayOf("Select Date Range", "Today", "Yesterday", "This Week", "This Month")

        AlertDialog.Builder(this)
            .setTitle("Filter Sales Report")
            .setItems(options) { dialog, which ->
                when (options[which]) {
                    "Select Date Range" -> showCustomDateRangeDialog()
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

    private fun showCustomDateRangeDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_date_range_picker, null)
        val dpStart = dialogView.findViewById<DatePicker>(R.id.dpStartDate)
        val dpEnd = dialogView.findViewById<DatePicker>(R.id.dpEndDate)

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        // --- Limit: 1 Year from Current Date ---
        val minCalendar = Calendar.getInstance().apply {
            // Itakda ang pinakaunang petsa na pwedeng piliin (1 taon ang nakalipas)
            add(Calendar.YEAR, -1)
        }
        val minDate = minCalendar.timeInMillis

        // Set the maximum date (Today)
        val maxDate = calendar.timeInMillis
        dpStart.maxDate = maxDate
        dpEnd.maxDate = maxDate

        // Apply Minimum Date (1 year ago)
        dpStart.minDate = minDate
        dpEnd.minDate = minDate

        // Initialize End Date Picker to today
        dpEnd.init(currentYear, currentMonth, currentDay, null)

        // Initialize Start Date Picker to 7 days ago (Default range)
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        dpStart.init(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), null)


        AlertDialog.Builder(this)
            .setTitle("Select Date Range")
            .setView(dialogView)
            .setPositiveButton("Apply Filter") { dialog, _ ->
                // Kumuha ng Start Date (00:00:00)
                val startCalendar = Calendar.getInstance().apply {
                    set(dpStart.year, dpStart.month, dpStart.dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // Kumuha ng End Date (23:59:59.999)
                val endCalendar = Calendar.getInstance().apply {
                    set(dpEnd.year, dpEnd.month, dpEnd.dayOfMonth, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                // Validation: Tiyaking hindi mas huli ang start date kaysa end date
                if (startCalendar.time.after(endCalendar.time)) {
                    Toast.makeText(this, "Error: Start Date cannot be after End Date.", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }

                val start = startCalendar.time
                val end = endCalendar.time

                val filterLabel = "${dateFormatter.format(start)} - ${dateFormatter.format(end)}"
                loadOrders(start, end, filterLabel)
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

        SalesChartHelper.plotDualAxisChart(salesChart, dailySalesList)

        Log.d(TAG, "Daily Sales Data Prepared: $dailySalesList")
    }
}

// -----------------------------
// RecyclerView Adapter for Orders (No Changes)
// -----------------------------
class OrderAdapter(private val items: List<Order>) :
    RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    inner class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvOrderId: TextView = view.findViewById(R.id.tvOrderId)
        val tvOrderTime: TextView = view.findViewById(R.id.tvOrderTime)
        val tvOrderTotal: TextView = view.findViewById(R.id.tvOrderTotal)
        val tvItemDetails: TextView = view.findViewById(R.id.tvItemDetails)
        val tvPaymentMethod: TextView = view.findViewById(R.id.tvPaymentMethod)
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

        holder.tvPaymentMethod.text = "Paid via: ${order.paymentMethod}"

        // Item details summary
        val itemSummary = order.items.joinToString(separator = ", ") { item ->
            "${item.quantity}x ${item.name} (₱${String.format(Locale.US, "%.2f", item.price)})"
        }
        holder.tvItemDetails.text = itemSummary
    }

    override fun getItemCount(): Int = items.size
}
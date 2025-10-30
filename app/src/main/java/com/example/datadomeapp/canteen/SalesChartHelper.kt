package com.example.datadomeapp.canteen

import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.*

object SalesChartHelper {

    /**
     * Sets up the initial styling and configuration of the LineChart.
     */
    fun initializeChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(false)
        chart.setBackgroundColor(Color.WHITE)

        // X-Axis (Bottom)
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(true)
        xAxis.textColor = Color.DKGRAY
        xAxis.textSize = 10f
        xAxis.isGranularityEnabled = true
        xAxis.granularity = 1f

        // Left Y-Axis (Sales Amount - Primary)
        val leftAxis = chart.axisLeft
        leftAxis.textColor = Color.parseColor("#009688") // Teal for Sales
        leftAxis.setDrawAxisLine(true)
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.LTGRAY
        leftAxis.granularity = 1f
        leftAxis.axisMinimum = 0f
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "₱${String.format(Locale.US, "%.0f", value)}"
            }
        }

        // Right Y-Axis (Quantity Sold - Secondary)
        val rightAxis = chart.axisRight
        rightAxis.isEnabled = true
        rightAxis.textColor = Color.parseColor("#FF9800") // Orange for Quantity
        rightAxis.setDrawAxisLine(true)
        rightAxis.setDrawGridLines(false) // Walang grid lines para hindi magulo
        rightAxis.granularity = 1f
        rightAxis.axisMinimum = 0f
        rightAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format(Locale.US, "%.0f pcs", value)
            }
        }

        chart.legend.isEnabled = true
        chart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        chart.animateX(750)
    }

    /**
     * Plots Dual-Axis Line Chart showing Sales Amount (Left) and Item Quantity (Right) per day.
     */
    fun plotDualAxisChart(chart: LineChart, dataList: List<DailySales>) {
        if (dataList.isEmpty()) {
            chart.clear()
            chart.setNoDataText("No Sales/Quantity Data Available.")
            chart.invalidate()
            return
        }

        val entriesSales = ArrayList<Entry>()
        val entriesQuantity = ArrayList<Entry>()
        val xLabels = ArrayList<String>()

        // 1. Convert DailySales to two sets of Entries
        dataList.forEachIndexed { index, dailySale ->
            entriesSales.add(Entry(index.toFloat(), dailySale.totalSales.toFloat()))
            entriesQuantity.add(Entry(index.toFloat(), dailySale.totalQuantitySold.toFloat()))
            xLabels.add(dailySale.date)
        }

        // 2. Create Data Sets

        // --- Sales Data Set (Left Axis) ---
        val dataSetSales = LineDataSet(entriesSales, "Total Sales (₱)")
        dataSetSales.color = Color.parseColor("#009688")
        dataSetSales.setCircleColor(Color.parseColor("#009688"))
        dataSetSales.axisDependency = YAxis.AxisDependency.LEFT // Link to Left Axis
        dataSetSales.lineWidth = 2.5f
        dataSetSales.valueTextSize = 10f
        dataSetSales.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSetSales.fillColor = Color.parseColor("#80009688")
        dataSetSales.setDrawFilled(true)
        dataSetSales.setDrawValues(false) // I-off ang values para hindi magulo

        // --- Quantity Data Set (Right Axis) ---
        val dataSetQuantity = LineDataSet(entriesQuantity, "Items Sold (pcs)")
        dataSetQuantity.color = Color.parseColor("#FF9800")
        dataSetQuantity.setCircleColor(Color.parseColor("#FF9800"))
        dataSetQuantity.axisDependency = YAxis.AxisDependency.RIGHT // Link to Right Axis
        dataSetQuantity.lineWidth = 2.5f
        dataSetQuantity.valueTextSize = 10f
        dataSetQuantity.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSetQuantity.setDrawValues(false) // I-off ang values

        // 3. Apply to Chart
        val lineData = LineData(dataSetSales, dataSetQuantity)
        chart.data = lineData

        // Set X-Axis Formatter
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(xLabels)
        chart.xAxis.labelCount = xLabels.size

        chart.notifyDataSetChanged()
        chart.invalidate() // Refresh the chart view
    }
}
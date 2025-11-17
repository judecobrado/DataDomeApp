package com.example.datadomeapp.teacher

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import android.util.Log
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore

class ManageGradesActivity : AppCompatActivity() {

    private lateinit var tvGradesHeader: TextView
    private lateinit var btnPrelim: Button
    private lateinit var btnMidterm: Button
    private lateinit var btnFinals: Button

    private var assignmentId: String? = null
    private var subjectCode: String? = null
    private var className: String? = null
    private val firestore = FirebaseFirestore.getInstance()
    private var currentTerm: String = ""

    // 🟢 ADDED: Section and Year Level variables
    private var sectionName: String? = null
    private var yearLevel: String? = null

    // Network callback for live monitoring
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_manage_grades)

        // --- Kunin ang Intent Data ---
        assignmentId = intent.getStringExtra("ASSIGNMENT_ID")
        subjectCode = intent.getStringExtra("SUBJECT_CODE")
        className = intent.getStringExtra("CLASS_NAME")

        // 🟢 CRITICAL FIX: Get section and year level
        sectionName = intent.getStringExtra("SECTION_NAME")
        yearLevel = intent.getStringExtra("YEAR_LEVEL")

        Log.d("ManageGrades", "Intent Data - Assignment: $assignmentId, Subject: $subjectCode, Class: $className")
        Log.d("ManageGrades", "Section: $sectionName, Year Level: $yearLevel")

        // --- View Binding ---
        tvGradesHeader = findViewById(R.id.tvGradesHeader)
        btnPrelim = findViewById(R.id.btnPrelim)
        btnMidterm = findViewById(R.id.btnMidterm)
        btnFinals = findViewById(R.id.btnFinals)

        tvGradesHeader.text = "Manage Grades for\n$className"

        // 🟢 CRITICAL: DISABLE ALL BUTTONS BY DEFAULT
        disableAllButtons()

        // Initialize network monitoring
        setupNetworkMonitoring()

        // If section/year level not passed directly, try to extract from class name
        if (sectionName.isNullOrEmpty() || yearLevel.isNullOrEmpty()) {
            extractSectionAndYearFromClassName()
        }

        // 🟢 Load class details from Firestore as backup
        if (sectionName.isNullOrEmpty() || yearLevel.isNullOrEmpty()) {
            loadClassDetailsFromFirestore()
        }

        // 🔄 Load system settings or handle offline mode
        if (isNetworkAvailable()) {
            loadSystemSettings()
        } else {
            handleOfflineMode()
        }

        // --- Button Click Listeners ---
        btnPrelim.setOnClickListener {
            navigateToGradingPeriod("Prelim")
        }

        btnMidterm.setOnClickListener {
            navigateToGradingPeriod("Midterm")
        }

        btnFinals.setOnClickListener {
            navigateToGradingPeriod("Finals")
        }
    }

    /**
     * 🟢 DISABLE ALL BUTTONS BY DEFAULT
     */
    private fun disableAllButtons() {
        runOnUiThread {
            Log.d("ManageGrades", "🔒 Disabling all buttons by default")

            // Disable all buttons
            btnPrelim.isEnabled = false
            btnMidterm.isEnabled = false
            btnFinals.isEnabled = false

            // Set visual disabled state
            btnPrelim.alpha = 0.5f
            btnMidterm.alpha = 0.5f
            btnFinals.alpha = 0.5f

            // Reset background colors to indicate disabled state
            try {
                val disabledColor = getColor(R.color.button_disabled_color)
                btnPrelim.setBackgroundColor(disabledColor)
                btnMidterm.setBackgroundColor(disabledColor)
                btnFinals.setBackgroundColor(disabledColor)
            } catch (e: Exception) {
                Log.e("ManageGrades", "Color not found, using fallback")
                // Fallback disabled color
                val disabledColor = getColor(android.R.color.darker_gray)
                btnPrelim.setBackgroundColor(disabledColor)
                btnMidterm.setBackgroundColor(disabledColor)
                btnFinals.setBackgroundColor(disabledColor)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh when returning to activity
        if (isNetworkAvailable()) {
            loadSystemSettings()
        } else {
            handleOfflineMode()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister network callback to prevent memory leaks
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e("NetworkMonitor", "Error unregistering network callback: $e")
        }
    }

    /**
     * 🟢 Extract section and year level from class name if not provided
     */
    private fun extractSectionAndYearFromClassName() {
        if (className.isNullOrEmpty()) {
            Log.w("ManageGrades", "Class name is null or empty, cannot extract section/year")
            return
        }

        try {
            Log.d("ManageGrades", "Extracting from class name: $className")

            // Multiple possible class name formats:
            // Format 1: "BSIT - 1A - CS101"
            // Format 2: "BSIT - 1st Year - A - CS101"
            // Format 3: "Course - YearSection - Subject"
            val parts = className!!.split(" - ")

            when {
                parts.size >= 3 -> {
                    // Format 2: "BSIT - 1st Year - A - CS101"
                    val yearPart = parts.getOrNull(1) ?: ""
                    val sectionPart = parts.getOrNull(2) ?: ""

                    yearLevel = yearPart
                    sectionName = sectionPart
                }
                parts.size >= 2 -> {
                    // Format 1: "BSIT - 1A - CS101"
                    val sectionYearPart = parts[1] // "1A"

                    if (sectionYearPart.length >= 2) {
                        val yearChar = sectionYearPart.first().toString()
                        val sectionChars = sectionYearPart.substring(1)

                        // Map year character to full year level
                        yearLevel = when (yearChar) {
                            "1" -> "1st Year"
                            "2" -> "2nd Year"
                            "3" -> "3rd Year"
                            "4" -> "4th Year"
                            else -> yearChar
                        }

                        sectionName = sectionChars
                    } else {
                        // Fallback: use the whole string as section
                        sectionName = sectionYearPart
                        yearLevel = "1st Year" // Default fallback
                    }
                }
                else -> {
                    // Fallback: try to find pattern in the class name
                    val pattern = """([1-4])([A-Za-z])""".toRegex()
                    val match = pattern.find(className!!)

                    if (match != null) {
                        val (yearChar, sectionChar) = match.destructured
                        yearLevel = when (yearChar) {
                            "1" -> "1st Year"
                            "2" -> "2nd Year"
                            "3" -> "3rd Year"
                            "4" -> "4th Year"
                            else -> yearChar
                        }
                        sectionName = sectionChar
                    } else {
                        // Last resort: use defaults
                        sectionName = "A"
                        yearLevel = "1st Year"
                    }
                }
            }

            Log.d("ManageGrades", "Extracted - Year: $yearLevel, Section: $sectionName")

        } catch (e: Exception) {
            Log.e("ManageGrades", "Error extracting section/year from class name: ${e.message}")
            // Set defaults on error
            sectionName = "A"
            yearLevel = "1st Year"
        }
    }

    /**
     * 🔄 Load class details to get section and year level from Firestore
     */
    private fun loadClassDetailsFromFirestore() {
        if (assignmentId.isNullOrEmpty()) {
            Log.w("ManageGrades", "No assignment ID, cannot load class details")
            return
        }

        Log.d("ManageGrades", "Loading class details from Firestore for: $assignmentId")

        firestore.collection("classAssignments").document(assignmentId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Get section and year level directly from class assignment
                    val loadedSection = document.getString("section")
                    val loadedYearLevel = document.getString("yearLevel")

                    // Only update if we got valid values
                    if (!loadedSection.isNullOrEmpty()) {
                        sectionName = loadedSection
                    }
                    if (!loadedYearLevel.isNullOrEmpty()) {
                        yearLevel = loadedYearLevel
                    }

                    Log.d("ManageGrades", "Loaded from Firestore - Year: $yearLevel, Section: $sectionName")

                    // Update UI if needed
                    updateButtonStates()

                } else {
                    Log.w("ManageGrades", "Class assignment document doesn't exist")
                }
            }
            .addOnFailureListener { e ->
                Log.e("ManageGrades", "Error loading class details: $e")
            }
    }

    /**
     * 📡 Setup live network monitoring
     */
    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(ConnectivityManager::class.java)

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d("NetworkMonitor", "✅ Network available - WiFi/Data connected")
                runOnUiThread {
                    loadSystemSettings() // Auto-reload when network returns
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d("NetworkMonitor", "❌ Network lost - No internet connection")
                runOnUiThread {
                    Toast.makeText(this@ManageGradesActivity, "📵 Network disconnected", Toast.LENGTH_LONG).show()
                    handleOfflineMode() // Auto-disable when network lost
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                // This gets called when network capabilities change (e.g., WiFi strength changes)
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                Log.d("NetworkMonitor", "🔄 Network capabilities changed - Internet: $hasInternet, Validated: $isValidated")

                if (hasInternet && isValidated) {
                    runOnUiThread {
                        Log.d("NetworkMonitor", "🌐 Internet connection validated")
                        loadSystemSettings()
                    }
                }
            }
        }

        // Register the network callback
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    /**
     * 📡 Check if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    /**
     * 📡 Load system settings - FIXED FIELD NAME to "academicTerm"
     */
    private fun loadSystemSettings() {
        if (!isNetworkAvailable()) {
            handleOfflineMode()
            return
        }

        Log.d("SystemSettings", "🔄 Attempting to load system settings...")

        firestore.collection("systemSettings").document("currentTerm")
            .get()
            .addOnSuccessListener { document ->
                Log.d("SystemSettings", "📄 Document data: ${document.data}")

                if (document.exists()) {
                    // 🔥 FIX: Use "academicTerm" instead of "currentTerm"
                    currentTerm = document.getString("academicTerm") ?: "Prelim"
                    Log.d("SystemSettings", "✅ Loaded academicTerm: $currentTerm")
                    runOnUiThread {
                        Toast.makeText(this@ManageGradesActivity, "✅ Grades loaded for $currentTerm", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w("SystemSettings", "📝 Document doesn't exist")
                    currentTerm = "Prelim" // Default fallback
                }
                updateButtonStates()
            }
            .addOnFailureListener { e ->
                Log.e("SystemSettings", "❌ Error loading system settings: $e")
                // If there's an error (including network issues), disable all buttons
                handleOfflineMode()
            }
    }

    /**
     * 🔒 Handle offline mode - disable all buttons
     */
    private fun handleOfflineMode() {
        runOnUiThread {
            Log.w("Network", "📵 No network connection - disabling all buttons")
            disableAllButtons()
        }
    }

    private fun updateButtonStates() {
        runOnUiThread {
            Log.d("ButtonStates", "🔄 Updating buttons - Current term: $currentTerm")

            // 🟢 CRITICAL: Only enable buttons if we have network AND valid section/year level
            if (!isNetworkAvailable()) {
                handleOfflineMode()
                return@runOnUiThread
            }

            // 🟢 CRITICAL: Check if we have valid section and year level
            if (sectionName.isNullOrEmpty() || yearLevel.isNullOrEmpty()) {
                Log.w("ButtonStates", "❌ Missing section or year level - disabling all buttons")
                disableAllButtons()
                Toast.makeText(this@ManageGradesActivity, "⚠️ Cannot load class details. Please try again.", Toast.LENGTH_LONG).show()
                return@runOnUiThread
            }

            // Reset all buttons first
            btnPrelim.isEnabled = true
            btnMidterm.isEnabled = true
            btnFinals.isEnabled = true

            btnPrelim.alpha = 1.0f
            btnMidterm.alpha = 1.0f
            btnFinals.alpha = 1.0f

            // Disable buttons based on current term
            when (currentTerm) {
                "Prelim" -> {
                    // Prelim lang ang enabled
                    btnMidterm.isEnabled = false
                    btnFinals.isEnabled = false

                    btnMidterm.alpha = 0.5f
                    btnFinals.alpha = 0.5f
                    Log.d("ButtonStates", "🔒 Prelim mode - Midterm and Finals disabled")
                }
                "Midterm" -> {
                    // Prelim and Midterm enabled, Finals disabled
                    btnFinals.isEnabled = false
                    btnFinals.alpha = 0.5f
                    Log.d("ButtonStates", "🔒 Midterm mode - Finals disabled")
                }
                "Finals" -> {
                    // All periods enabled during Finals
                    Log.d("ButtonStates", "🔓 Finals mode - All periods enabled")
                }
                else -> {
                    Log.d("ButtonStates", "❓ Unknown term: $currentTerm - disabling all")
                    disableAllButtons()
                }
            }

            highlightCurrentTermButton()
        }
    }

    private fun highlightCurrentTermButton() {
        try {
            val normalColor = getColor(R.color.button_normal_color)
            val highlightColor = getColor(R.color.button_highlight_color)

            btnPrelim.setBackgroundColor(normalColor)
            btnMidterm.setBackgroundColor(normalColor)
            btnFinals.setBackgroundColor(normalColor)

            when (currentTerm) {
                "Prelim" -> btnPrelim.setBackgroundColor(highlightColor)
                "Midterm" -> btnMidterm.setBackgroundColor(highlightColor)
                "Finals" -> btnFinals.setBackgroundColor(highlightColor)
            }
        } catch (e: Exception) {
            Log.e("HighlightButton", "Color not found, using fallback colors")
            // Fallback colors
            val normalColor = getColor(android.R.color.darker_gray)
            val highlightColor = getColor(android.R.color.holo_blue_light)

            btnPrelim.setBackgroundColor(normalColor)
            btnMidterm.setBackgroundColor(normalColor)
            btnFinals.setBackgroundColor(normalColor)

            when (currentTerm) {
                "Prelim" -> btnPrelim.setBackgroundColor(highlightColor)
                "Midterm" -> btnMidterm.setBackgroundColor(highlightColor)
                "Finals" -> btnFinals.setBackgroundColor(highlightColor)
            }
        }
    }

    private fun navigateToGradingPeriod(period: String) {
        if (assignmentId.isNullOrEmpty() || subjectCode.isNullOrEmpty() || className.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing class information.", Toast.LENGTH_LONG).show()
            return
        }

        // 🟢 CRITICAL: Validate that we have section and year level
        if (sectionName.isNullOrEmpty() || yearLevel.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Cannot determine class section. Please try again.", Toast.LENGTH_LONG).show()
            Log.e("ManageGrades", "Missing section/year: Section=$sectionName, Year=$yearLevel")
            return
        }

        // Double-check network before navigating
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "📵 No internet connection. Please check your network.", Toast.LENGTH_LONG).show()
            return
        }

        // 🛡️ Validate if the selected period is allowed
        if (!isPeriodAllowed(period)) {
            Toast.makeText(this, "$period period is not yet available. Current system term: $currentTerm", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, GradeInputActivity::class.java)
        intent.putExtra("ASSIGNMENT_ID", assignmentId)
        intent.putExtra("SUBJECT_CODE", subjectCode)
        intent.putExtra("CLASS_NAME", className)
        intent.putExtra("GRADING_PERIOD", period)
        intent.putExtra("CURRENT_TERM", currentTerm)

        // 🟢 CRITICAL: Pass section and year level to GradeInputActivity
        intent.putExtra("SECTION_NAME", sectionName)
        intent.putExtra("YEAR_LEVEL", yearLevel)

        Log.d("ManageGrades", "Navigating to GradeInput with - Section: $sectionName, Year: $yearLevel")
        startActivity(intent)
    }

    /**
     * ✅ Check if the selected period is allowed based on current system term
     */
    private fun isPeriodAllowed(period: String): Boolean {
        val allowed = when (currentTerm) {
            "Prelim" -> period == "Prelim"
            "Midterm" -> period == "Prelim" || period == "Midterm"
            "Finals" -> true // All periods allowed during finals
            else -> false
        }
        Log.d("PeriodCheck", "📋 Period: $period, Current: $currentTerm, Allowed: $allowed")
        return allowed
    }

    /**
     * 🔧 TEMPORARY: Admin function to update term (for testing)
     */
    private fun updateCurrentTerm(newTerm: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "📵 No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val updateData = hashMapOf(
            "academicTerm" to newTerm, // 🔥 FIX: Use correct field name
            "academicYear" to "2025-2026",
            "semester" to "1st Semester",
            "lastUpdated" to com.google.firebase.Timestamp.now()
        )

        firestore.collection("systemSettings").document("currentTerm")
            .set(updateData)
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Updated to $newTerm!", Toast.LENGTH_SHORT).show()
                currentTerm = newTerm
                updateButtonStates()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "❌ Update failed", Toast.LENGTH_SHORT).show()
            }
    }
}
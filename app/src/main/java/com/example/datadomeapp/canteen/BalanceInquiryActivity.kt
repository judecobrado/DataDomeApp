package com.example.datadomeapp.canteen

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Tasks
import java.util.*

class BalanceInquiryActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BalanceAdapter
    private lateinit var tvTotalBalance: TextView
    private lateinit var etSearch: EditText
    private lateinit var spinnerFilter: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoResults: TextView
    // ⚠️ Tiyaking tama ang data model na ginagamit mo (tingnan ang huling sagot)
    private var allUsersList: List<BalanceItem> = emptyList()
    private var currentFilter: String = "High to Low"
    private var currentQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_balance_inquiry)

        initializeViews()
        setupListeners()
        loadAllUserBalances()
    }

    private fun initializeViews() {
        tvTotalBalance = findViewById(R.id.tvTotalBalance)
        etSearch = findViewById(R.id.etSearch)
        spinnerFilter = findViewById(R.id.spinnerFilter)
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerViewBalances)
        tvNoResults = findViewById(R.id.tvNoResults)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize adapter with an empty list and the click listener
        adapter = BalanceAdapter(emptyList()) { item ->
            // Action when an item is clicked: Go to History Activity
            val intent = Intent(this, UserHistoryActivity::class.java)
            intent.putExtra("userUID", item.uid)
            intent.putExtra("userName", item.name)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        // 1. Search Logic
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s.toString()
                filterAndSortList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 2. Filter/Sort Logic
        val filterOptions = resources.getStringArray(R.array.balance_filter_options) // Assuming you added the string array
        val filterAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filterOptions)
        spinnerFilter.adapter = filterAdapter

        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentFilter = filterOptions[position]
                filterAndSortList()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    // ---------------------------------------------------------------------------------------------
    // --- DATA FETCHING ---
    // ---------------------------------------------------------------------------------------------

    private fun loadAllUserBalances() {
        progressBar.visibility = View.VISIBLE

        val studentsTask = firestore.collection("students").get()
        val teachersTask = firestore.collection("teachers").get()

        Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(studentsTask, teachersTask)
            .addOnSuccessListener { results ->
                val studentsSnapshot = results[0] as com.google.firebase.firestore.QuerySnapshot
                val teachersSnapshot = results[1] as com.google.firebase.firestore.QuerySnapshot

                val combinedList = mutableListOf<BalanceItem>()
                var totalBalance = 0.0

                // Process Students
                studentsSnapshot.documents.forEach { doc ->
                    val balance = doc.getDouble("balance") ?: 0.0
                    totalBalance += balance
                    combinedList.add(BalanceItem(
                        // ❌ TINANGGAL ANG accountId
                        // accountId = doc.id,
                        uid = doc.getString("userUid") ?: "",
                        name = (doc.getString("firstName") ?: "") + " " + (doc.getString("lastName") ?: ""),
                        role = "Student",
                        balance = balance
                    ))
                }

                // Process Teachers
                teachersSnapshot.documents.forEach { doc ->
                    val balance = doc.getDouble("balance") ?: 0.0
                    totalBalance += balance
                    combinedList.add(BalanceItem(
                        // ❌ TINANGGAL ANG accountId
                        // accountId = doc.id,
                        uid = doc.getString("uid") ?: "",
                        name = doc.getString("name") ?: "N/A",
                        role = "Teacher",
                        balance = balance
                    ))
                }

                allUsersList = combinedList.sortedByDescending { it.balance }
                tvTotalBalance.text = "₱${String.format(Locale.US, "%.2f", totalBalance)}"
                filterAndSortList()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading balances: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun filterAndSortList() {
        var filteredList = allUsersList.filter {
            // ❌ SEARCH LOGIC: Check name only (case-insensitive)
            it.name.contains(currentQuery, ignoreCase = true)
            // ❌ Tinanggal ang || it.accountId.contains(currentQuery, ignoreCase = true)
        }

        // Apply Role Filter
        filteredList = when(currentFilter) {
            "Students Only" -> filteredList.filter { it.role == "Student" }
            "Teachers Only" -> filteredList.filter { it.role == "Teacher" }
            else -> filteredList
        }

        // Apply Sort
        filteredList = when(currentFilter) {
            "Low to High" -> filteredList.sortedBy { it.balance }
            "High to Low" -> filteredList.sortedByDescending { it.balance }
            else -> filteredList // Maintain the initial sort if no balance filter is selected
        }

        if (filteredList.isEmpty()) {
            tvNoResults.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            tvNoResults.text = "No user found."
        } else {
            tvNoResults.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        adapter.updateList(filteredList)
    }
}
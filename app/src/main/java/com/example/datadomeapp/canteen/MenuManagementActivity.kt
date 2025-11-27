package com.example.datadomeapp.canteen

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.*

// Data class (assuming this exists elsewhere)
// class MenuItem(var id: String = "", val name: String = "", val price: Double = 0.0, ...)

class MenuManagementActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddMenu: Button
    private lateinit var etSearch: EditText
    private lateinit var spFilter: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoItems: TextView

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var spCategoryFilter: Spinner
    private val allMenuList = mutableListOf<MenuItem>()      // Stores all fetched data from Firestore
    private val filteredMenuList = mutableListOf<MenuItem>() // List currently displayed in RecyclerView
    private lateinit var adapter: MenuAdapter
    private var menuListener: ListenerRegistration? = null

    private var staffUid: String? = null
    private var staffCanteenName: String? = null

    // Tracking state for search and filter
    private var currentQueryText = ""
    private var currentFilter = "All"
    private var currentCategoryFilter = "All Categories"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.canteen_menu_management)

        // --- Initialization ---
        recyclerView = findViewById(R.id.rvMenu)
        btnAddMenu = findViewById(R.id.btnAddMenu)
        etSearch = findViewById(R.id.etSearch)
        spFilter = findViewById(R.id.spFilter)
        spCategoryFilter = findViewById(R.id.spCategoryFilter)
        progressBar = findViewById(R.id.progressBar)
        tvNoItems = findViewById(R.id.tvNoItems)

        staffUid = auth.currentUser?.uid

        setupRecyclerView()
        setupSearchFilter()
        loadStaffCanteen()

        // Show loading indicator initially
        progressBar.visibility = View.VISIBLE
    }

    private fun loadStaffCanteen() {
        val uid = staffUid ?: return
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                staffCanteenName = doc.getString("canteenName")

                // ✅ Set the click listener and enable the button ONLY after loading
                btnAddMenu.isEnabled = true
                btnAddMenu.setOnClickListener {
                    if (staffCanteenName.isNullOrEmpty()) {
                        Toast.makeText(this, "Canteen info not found. Cannot add menu.", Toast.LENGTH_LONG).show()
                    } else {
                        val intent = Intent(this, AddEditMenuActivity::class.java)
                        intent.putExtra("canteenName", staffCanteenName)
                        startActivity(intent)
                    }
                }

                if (!staffCanteenName.isNullOrEmpty()) {
                    startMenuListener() // Start listener if canteen name is valid
                } else {
                    // Handle critical case: no canteen name assigned
                    progressBar.visibility = View.GONE
                    tvNoItems.text = "Error: Your canteen name is not set in your user profile. Cannot load or add menu."
                    tvNoItems.visibility = View.VISIBLE
                    btnAddMenu.isEnabled = false
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnAddMenu.isEnabled = false
                Toast.makeText(this, "Failed to get staff info: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupRecyclerView() {
        // Adapter uses the filtered list
        adapter = MenuAdapter(filteredMenuList,
            onEditClick = { menu ->
                val intent = Intent(this, AddEditMenuActivity::class.java)
                intent.putExtra("menuId", menu.id)
                intent.putExtra("canteenName", menu.canteenName)
                startActivity(intent)
            },
            onDeleteClick = { menu ->
                // ⚠️ Dito na idinagdag ang Confirmation Dialog
                showDeleteConfirmationDialog(menu)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    /**
     * Ipinapakita ang confirmation dialog bago burahin ang item.
     * @param menu Ang MenuItem na buburahin.
     */
    private fun showDeleteConfirmationDialog(menu: MenuItem) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Deletion")
            .setMessage("Are you sure you want to delete '${menu.name}'? This action cannot be undone.")
            .setPositiveButton("Delete") { dialog, which ->
                // Kung nag-confirm, tawagin ang delete logic
                deleteMenuItem(menu)
            }
            .setNegativeButton("Cancel", null) // Wala lang, isasara lang ang dialog
            .show()
    }

    /**
     * Ang aktwal na logic para mag-delete sa Firestore.
     * @param menu Ang MenuItem na buburahin.
     */
    private fun deleteMenuItem(menu: MenuItem) {
        firestore.collection("canteenMenu").document(menu.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "${menu.name} deleted successfully.", Toast.LENGTH_SHORT).show()
                // Ang startMenuListener ay awtomatikong magre-refresh dahil ito ay snapshot listener
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun setupSearchFilter() {
        // Search input listener
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentQueryText = s.toString().trim()
                applyFiltersAndSearch() // Trigger filter/search on text change
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // NOTE: btnAddMenu.isEnabled = false at loadStaffCanteen() is already handling this

        // Filter spinner listener
        spFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentFilter = parent.getItemAtPosition(position).toString()
                applyFiltersAndSearch() // Trigger filter/search on spinner change
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Category filter spinner listener
        spCategoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                currentCategoryFilter = parent.getItemAtPosition(position).toString()
                applyFiltersAndSearch() // Trigger filter/search on category change
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun startMenuListener() {
        val uid = staffUid ?: return
        menuListener?.remove() // Remove previous listener to avoid duplicates

        menuListener = firestore.collection("canteenMenu")
            .whereEqualTo("staffUid", uid)
            // Sorting: Available (TRUE) first, then by updated timestamp
            .orderBy("available", Query.Direction.DESCENDING)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->

                progressBar.visibility = View.GONE // Hide loading after data is received

                if (error != null) {
                    Toast.makeText(this, "Error loading menu: ${error.message}", Toast.LENGTH_SHORT).show()
                    tvNoItems.text = "Failed to load menu items."
                    tvNoItems.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                allMenuList.clear()
                snapshot?.documents?.forEach { doc ->
                    val menu = doc.toObject(MenuItem::class.java)
                    menu?.id = doc.id
                    if (menu != null) allMenuList.add(menu)
                }

                // Apply filtering/search to the newly fetched and sorted data
                applyFiltersAndSearch()
            }
    }

    /**
     * Filters the full list (allMenuList) based on the Spinner and EditText input.
     * This is the client-side logic for search and filter.
     */
    private fun applyFiltersAndSearch() {
        // 1. Filter the entire list based on Spinner and Search text
        val tempFilterList = allMenuList.filter { item ->
            // Check if item matches the spinner filter (All, Available, Out of Stock)
            val matchesFilter = when (currentFilter) {
                "Available" -> item.available == true
                "Not Available" -> item.available == false
                else -> true // "All"
            }

            val matchesCategoryFilter = when (currentCategoryFilter) {
                "All Categories" -> true
                else -> item.category == currentCategoryFilter
            }

            // Check if item matches the search query (case-insensitive contains)
            val matchesSearch = if (currentQueryText.isEmpty()) {
                true
            } else {
                val lowerCaseQuery = currentQueryText.toLowerCase(Locale.getDefault())
                item.name.toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)
            }

            matchesFilter && matchesCategoryFilter && matchesSearch
        }

        // 2. Update the RecyclerView's data source
        filteredMenuList.clear()
        filteredMenuList.addAll(tempFilterList)
        adapter.notifyDataSetChanged()

        // 3. Update No Items Message visibility
        if (filteredMenuList.isEmpty()) {
            tvNoItems.text = if (allMenuList.isEmpty()) {
                "No menu items found. Tap 'Add Item' to start."
            } else {
                "No menu items match your current search and filter."
            }
            tvNoItems.visibility = View.VISIBLE
        } else {
            tvNoItems.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure listener is started/restarted when the activity is foreground
        if (staffUid != null && staffCanteenName != null) {
            startMenuListener()
        }
    }

    override fun onPause() {
        super.onPause()
        // Remove listener when activity is backgrounded to save resources
        menuListener?.remove()
    }
}
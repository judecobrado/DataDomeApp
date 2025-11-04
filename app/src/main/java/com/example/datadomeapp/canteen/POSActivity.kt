package com.example.datadomeapp.canteen

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*

// Data class para sa Cart Item (Nasa loob na ng POSActivity package)
data class CartItem(
    val menuId: String,
    val name: String,
    val price: Double,
    var quantity: Int = 1
) {
    val subtotal: Double
        get() = price * quantity
}

// NOTE: Ang MenuItem data class ay INILIPAT sa sarili nitong file (MenuItem.kt)
// para maiwasan ang "duplicate class" error.
// Assuming mayroon kang: data class MenuItem(var id: String = "", val name: String? = null, ...)

class POSActivity : AppCompatActivity() {

    // ------------------- PROPERTIES -------------------
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private lateinit var pendingIntent: PendingIntent

    private lateinit var searchMenu: SearchView // ⬅️ IDINAGDAG
    private lateinit var pbMenuLoading: ProgressBar // ⬅️ IDINAGDAG
    private lateinit var tvNoResults: TextView // ⬅️ IDINAGDAG

    private val menuList = mutableListOf<MenuItem>() // Original full list
    private val cartList = mutableListOf<CartItem>()
    private lateinit var menuAdapter: MenuAdapterPOS
    private lateinit var cartAdapter: CartAdapter
    private var menuListener: ListenerRegistration? = null
    private var staffCanteenName: String? = null
    private var staffUid: String? = null

    private var scannedUserId: String? = null
    private var scannedUserAccountId: String? = null
    private var scannedUserRole: String? = null

    // UI Elements
    private lateinit var rvMenu: RecyclerView
    private lateinit var rvCart: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var btnCheckout: Button
    private lateinit var llNfcPrompt: LinearLayout
    private lateinit var tvNfcPrompt: TextView
    private lateinit var btnCancelOrder: Button
    private lateinit var pbLoading: ProgressBar // Transaction loading bar

    // ------------------- LIFECYCLE -------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.canteen_pos)
        supportActionBar?.title = "Canteen Point of Sale"

        initializeViews()
        setupNFC()
        setupListeners()
        setupAdapters()
        setupSearch() // ⬅️ IDINAGDAG

        staffUid = auth.currentUser?.uid
        if (staffUid == null) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadStaffCanteen()
        updateTotal()
        resetCheckoutState()
    }

    private fun initializeViews() {
        rvMenu = findViewById(R.id.rvMenuPOS)
        rvCart = findViewById(R.id.rvCart)
        tvTotal = findViewById(R.id.tvTotalAmount)
        btnCheckout = findViewById(R.id.btnCheckout)
        llNfcPrompt = findViewById(R.id.llNfcPrompt)
        tvNfcPrompt = findViewById(R.id.tvNfcPrompt)
        btnCancelOrder = findViewById(R.id.btnCancelOrder)
        pbLoading = findViewById(R.id.pbLoading) // Transaction loading bar

        // ⬅️ IDINAGDAG ANG SEARCH UI ELEMENTS
        searchMenu = findViewById(R.id.searchMenu)
        pbMenuLoading = findViewById(R.id.pbMenuLoading) // Menu loading bar
        tvNoResults = findViewById(R.id.tvNoResults)
    }

    // ------------------- SEARCH & FILTERING -------------------

    private fun setupSearch() {
        searchMenu.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterMenu(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterMenu(newText)
                return true
            }
        })
    }

    private fun filterMenu(query: String?) {
        val filteredList = if (query.isNullOrBlank()) {
            menuList.toList()
        } else {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            menuList.filter {
                it.name?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true
            }
        }

        menuAdapter.updateList(filteredList) // Requires 'updateList' in MenuAdapterPOS

        // NO RESULTS CHECK
        if (filteredList.isEmpty()) {
            // Check if the empty list is due to no results from a query, or if the whole list is empty
            if (menuList.isEmpty() && query.isNullOrBlank()) {
                tvNoResults.text = "No items available in the menu yet."
            } else {
                tvNoResults.text = "No menu items found matching your search."
            }
            tvNoResults.visibility = View.VISIBLE
            rvMenu.visibility = View.GONE
        } else {
            // May laman ang filtered list
            tvNoResults.visibility = View.GONE
            rvMenu.visibility = View.VISIBLE
        }
    }


    private fun setupListeners() {
        btnCheckout.setOnClickListener {
            if (cartList.isNotEmpty()) {
                showCheckoutPrompt()
            } else {
                Toast.makeText(this, "Please add items to the cart first.", Toast.LENGTH_SHORT).show()
            }
        }
        btnCancelOrder.setOnClickListener {
            resetCheckoutState()
        }
    }

    private fun setupAdapters() {
        // 1. Menu Adapter (Grid Layout)
        menuAdapter = MenuAdapterPOS(menuList) { menuItem ->
            addItemToCart(menuItem)
        }
        rvMenu.layoutManager = GridLayoutManager(this, 2) // 2 columns for menu
        rvMenu.adapter = menuAdapter

        // 2. Cart Adapter (Linear Layout)
        cartAdapter = CartAdapter(cartList,
            onQuantityChange = { cartItem, newQuantity ->
                updateCartItemQuantity(cartItem, newQuantity)
            },
            onRemove = { cartItem ->
                removeItemFromCart(cartItem)
            }
        )
        rvCart.layoutManager = LinearLayoutManager(this)
        rvCart.adapter = cartAdapter
    }


    // ------------------- DATA LOADING & REAL-TIME MENU -------------------

    private fun loadStaffCanteen() {
        // Fetch Canteen Name to filter the menu (Assuming canteenName is stored in 'users' doc)
        firestore.collection("users").document(staffUid!!).get()
            .addOnSuccessListener { doc ->
                staffCanteenName = doc.getString("canteenName")
                startMenuListener()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load staff data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startMenuListener() {
        if (staffCanteenName == null) return

        menuListener?.remove()

        pbMenuLoading.visibility = View.VISIBLE // ⬅️ SHOW Menu Loading
        tvNoResults.visibility = View.GONE

        // Filter menu by canteenName AND available=true
        menuListener = firestore.collection("canteenMenu")
            .whereEqualTo("canteenName", staffCanteenName)
            .whereEqualTo("available", true) // Only show available items
            .addSnapshotListener { snapshot, error ->
                pbMenuLoading.visibility = View.GONE // ⬅️ HIDE Menu Loading

                if (error != null) {
                    Toast.makeText(this, "Error loading menu: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                menuList.clear()
                snapshot?.documents?.forEach { doc ->
                    val item = doc.toObject(MenuItem::class.java)
                    item?.id = doc.id
                    if (item != null) menuList.add(item)
                }

                // I-update ang listahan at i-apply ang search filter
                filterMenu(searchMenu.query.toString())
            }
    }

    override fun onResume() {
        super.onResume()
        // Gumamit ng FLAG_UPDATE_CURRENT o FLAG_IMMUTABLE kasama ng FLAG_MUTABLE
        pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        startMenuListener() // Re-attach listener on resume
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
        menuListener?.remove() // Remove listener on pause
    }


    // ------------------- CART MANAGEMENT -------------------

    private fun addItemToCart(menuItem: MenuItem) {
        val existingItem = cartList.find { it.menuId == menuItem.id }

        if (existingItem != null) {
            existingItem.quantity++
        } else {
            // Check if price is valid before adding to cart
            val price = menuItem.price ?: 0.0
            if (price <= 0.0) {
                Toast.makeText(this, "${menuItem.name} has an invalid price.", Toast.LENGTH_SHORT).show()
                return
            }

            cartList.add(CartItem(menuItem.id, menuItem.name ?: "Unknown Item", price, 1))
        }

        cartAdapter.notifyDataSetChanged()
        updateTotal()
        rvCart.smoothScrollToPosition(cartList.size - 1)
    }

    private fun removeItemFromCart(cartItem: CartItem) {
        cartList.remove(cartItem)
        cartAdapter.notifyDataSetChanged()
        updateTotal()
    }

    private fun updateCartItemQuantity(cartItem: CartItem, newQuantity: Int) {
        cartItem.quantity = newQuantity
        cartAdapter.notifyDataSetChanged()
        updateTotal()
    }

    private fun updateTotal() {
        val total = cartList.sumOf { it.subtotal }
        tvTotal.text = String.format(Locale.US, "₱%.2f", total)

        // Only allow checkout if there are items and total > 0
        btnCheckout.isEnabled = total > 0.0

        if (total > 0.0) {
            btnCancelOrder.text = "Clear Cart" // Baguhin ang text
            btnCancelOrder.visibility = View.VISIBLE
        } else {
            btnCancelOrder.visibility = View.GONE
        }
    }

    // ------------------- CHECKOUT & NFC PROCESSING -------------------

    private fun showCheckoutPrompt() {
        val total = cartList.sumOf { it.subtotal }
        val totalFormatted = String.format(Locale.US, "₱%.2f", total)

        val builder = AlertDialog.Builder(this)
            .setTitle("Confirm Payment")
            .setMessage("Total Amount: $totalFormatted\n\nPlease select the payment method.")

        // ⬅️ BAGONG LOGIC DITO: I-check kung may NFC adapter at kung ito ay enabled.
        val hasWorkingNfc = nfcAdapter != null && nfcAdapter?.isEnabled == true

        if (hasWorkingNfc) {
            builder.setPositiveButton("SCAN RFID") { _, _ ->
                enterNfcScanningState()
            }
        } else {
            // Kung walang NFC, o naka-off, magbigay ng opsyon na i-enable ito (optional)
            // o di kaya'y gawing default ang cash payment kung walang ibang digital option.
            Log.w("POSActivity", "NFC is not available or disabled.")
            // Maaari mo ring maglagay ng Toast dito kung gusto mo i-notify ang staff.
        }

        builder.setNeutralButton("CASH PAYMENT") { _, _ ->
            logCashTransaction(total)
            AlertDialog.Builder(this)
                .setTitle("Cash Payment Successful! ✅")
                .setMessage("Please collect ${totalFormatted} from the customer.")
                .setPositiveButton("DONE") { _, _ -> resetCheckoutState() }
                .show()
        }
            .setNegativeButton("Cancel Transaction", null)
            .show()
    }

    private fun enterNfcScanningState() {
        // Disable UI interactions, show prompt
        llNfcPrompt.visibility = View.VISIBLE
        tvNfcPrompt.text = "Awaiting customer RFID scan..."
        btnCheckout.isEnabled = false

        // Update Cancel button to handle returning to the payment method selection
        btnCancelOrder.text = "Cancel/Change Payment"
        btnCancelOrder.visibility = View.VISIBLE
        btnCancelOrder.setOnClickListener {
            // I-reset ang NFC state at bumalik sa payment prompt
            llNfcPrompt.visibility = View.GONE
            btnCheckout.isEnabled = true
            btnCancelOrder.text = "Cancel Order"
            btnCancelOrder.setOnClickListener { resetCheckoutState() } // Ibalik ang default reset action
            showCheckoutPrompt() // Ipakita ulit ang payment prompt
        }
    }

    private fun resetCheckoutState() {
        // Clear cart and reset UI
        cartList.clear()
        cartAdapter.notifyDataSetChanged()
        updateTotal()

        scannedUserId = null
        scannedUserAccountId = null
        scannedUserRole = null

        llNfcPrompt.visibility = View.GONE
        btnCheckout.isEnabled = true
        btnCancelOrder.visibility = View.GONE
        // Ensure btnCancelOrder goes back to its default reset action
        btnCancelOrder.text = "Clear Cart"
        btnCancelOrder.setOnClickListener { resetCheckoutState() }
    }

    // --- NFC Setup & Handling (Reused from TopUpActivity) ---
    private fun setupNFC() {
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
            return
        }
        // Gagamitin na lang natin ang pendingIntent generation sa onResume, para ma-handle ang lifecycle.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Check if we are in the scanning state before processing
        if (llNfcPrompt.visibility != View.VISIBLE) {
            return
        }
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra<android.nfc.Tag>(NfcAdapter.EXTRA_TAG)
            // Fix: Check for null tag before trying to get its ID
            val rfidData = bytesToHex(tag?.id ?: return)

            tvNfcPrompt.text = "RFID Detected. Validating user..."
            loadUserByRfidForPayment(rfidData)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }.uppercase(Locale.US)
    }
    // -----------------------------------------------------

    private fun logCashTransaction(amount: Double) {
        // ... (Log cash transaction logic remains the same)
        val transaction = hashMapOf(
            "userId" to "CASH",
            "role" to "cash",
            "type" to "CASH_PAYMENT",
            "amount" to amount,
            "timestamp" to FieldValue.serverTimestamp(),
            "canteenName" to (staffCanteenName ?: "UnknownCanteen"),
            "staffUid" to (staffUid ?: "UnknownStaff"),
            "items" to cartList.map { item ->
                hashMapOf(
                    "menuId" to item.menuId,
                    "name" to item.name,
                    "price" to item.price,
                    "quantity" to item.quantity
                )
            }
        )
        firestore.collection("transactions").add(transaction)
    }

    // ------------------- DATA VALIDATION (PRE-PAYMENT) -------------------

    private fun loadUserByRfidForPayment(rfidData: String) {
        pbLoading.visibility = View.VISIBLE // SHOW Transaction Loading
        firestore.collection("users")
            .whereEqualTo("rfidTag", rfidData)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                pbLoading.visibility = View.GONE // HIDE Transaction Loading

                if (querySnapshot.isEmpty) {
                    Toast.makeText(this, "RFID tag not registered.", Toast.LENGTH_LONG).show()
                    tvNfcPrompt.text = "Error: Tag not registered. Please rescan."
                    return@addOnSuccessListener
                }

                val userDoc = querySnapshot.documents.first()
                val uid = userDoc.id
                val role = userDoc.getString("role")?.lowercase(Locale.getDefault()) // Lowercase for safety
                val status = userDoc.getString("rfidStatus")?.uppercase(Locale.getDefault())
                val totalAmount = cartList.sumOf { it.subtotal }

                // 1. Status Check
                if (status != "ACTIVE") {
                    Toast.makeText(this, "Payment blocked: Account is currently ${status ?: "INACTIVE"}.", Toast.LENGTH_LONG).show()
                    tvNfcPrompt.text = "Account Inactive/Blocked"
                    return@addOnSuccessListener
                }

                // 2. Role Check
                if (role == null || (role != "student" && role != "teacher")) {
                    Toast.makeText(this, "Unauthorized user type for payment: ${role ?: "Unknown"}.", Toast.LENGTH_LONG).show()
                    tvNfcPrompt.text = "Only Student/Teacher tags accepted."
                    return@addOnSuccessListener
                }

                // 3. Account ID link check
                val accountId = if (role == "student") userDoc.getString("studentId") else userDoc.getString("teacherId")

                if (accountId.isNullOrEmpty()) {
                    Toast.makeText(this, "Account ID link missing. Contact administrator.", Toast.LENGTH_LONG).show()
                    tvNfcPrompt.text = "Account ID link missing."
                    return@addOnSuccessListener
                }

                // Store user data
                scannedUserId = uid
                scannedUserAccountId = accountId
                scannedUserRole = role

                // Proceed to secure transaction (Use !! assertion since we checked for null)
                processSecurePayment(totalAmount, uid, accountId!!, role!!)

            }.addOnFailureListener { e ->
                pbLoading.visibility = View.GONE // HIDE Transaction Loading
                Toast.makeText(this, "Error scanning RFID: ${e.message}", Toast.LENGTH_LONG).show()
                tvNfcPrompt.text = "Scan Error. Please rescan."
            }
    }

    // ------------------- SECURE FIREBASE TRANSACTION -------------------

    private fun processSecurePayment(
        totalAmount: Double,
        userId: String,
        accountId: String,
        role: String
    ) {
        val collectionName = if (role == "student") "students" else "teachers"
        val accountRef = firestore.collection(collectionName).document(accountId)

        pbLoading.visibility = View.VISIBLE
        llNfcPrompt.visibility = View.VISIBLE
        tvNfcPrompt.text = "Processing secure transaction..."

        firestore.runTransaction { transaction ->
            // 1. READ
            val snapshot = transaction.get(accountRef)
            val currentBalance = snapshot.getDouble("balance") ?: 0.0

            // 2. CHECK
            if (currentBalance < totalAmount) {
                throw Exception("INSUFFICIENT_BALANCE")
            }

            // 3. WRITE
            val newBalance = currentBalance - totalAmount
            transaction.update(accountRef, "balance", newBalance)

            return@runTransaction newBalance

        }.addOnSuccessListener { finalBalance ->
            pbLoading.visibility = View.GONE
            logTransaction(userId, totalAmount, finalBalance, accountId, role)

            AlertDialog.Builder(this)
                .setTitle("Payment Successful! ✅")
                .setMessage("Amount Paid: ₱${String.format(Locale.US, "%.2f", totalAmount)}\nNew Balance: ₱${String.format(Locale.US, "%.2f", finalBalance)}")
                .setPositiveButton("DONE") { _, _ -> resetCheckoutState() }
                .show()

        }.addOnFailureListener { e ->
            pbLoading.visibility = View.GONE
            val message = when (e.message) {
                "INSUFFICIENT_BALANCE" -> "Payment Failed: Insufficient balance on account."
                else -> "Payment Failed: Transaction error. Please try again. Error: ${e.message}"
            }
            Log.e("POSActivity", "RFID Payment Failed: ${e.message}", e)

            AlertDialog.Builder(this)
                .setTitle("Transaction Error")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> tvNfcPrompt.text = "Awaiting customer RFID scan..." }
                .setNegativeButton("Cancel Order") { _, _ -> resetCheckoutState() }
                .show()
        }
    }

    /**
     * Logs the transaction after a successful payment.
     */
    private fun logTransaction(
        userId: String,
        amount: Double,
        finalBalance: Double,
        accountId: String,
        role: String
    ) {
        val transaction = hashMapOf(
            "userId" to userId,
            "accountId" to accountId,
            "role" to role,
            "type" to "RFID_PAYMENT",
            "amount" to amount,
            "timestamp" to FieldValue.serverTimestamp(),
            "finalBalance" to finalBalance,
            "canteenName" to (staffCanteenName ?: "UnknownCanteen"),
            "staffUid" to (staffUid ?: "UnknownStaff"),
            "items" to cartList.map { item ->
                hashMapOf(
                    "menuId" to item.menuId,
                    "name" to item.name,
                    "price" to item.price,
                    "quantity" to item.quantity
                )
            }
        )
        firestore.collection("transactions").add(transaction)
    }
}
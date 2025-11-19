package com.example.datadomeapp.canteen

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datadomeapp.R
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*

// Data class para sa Cart Item
data class CartItem(
    val menuId: String,
    val name: String,
    val price: Double,
    var quantity: Int = 1
) {
    val subtotal: Double
        get() = price * quantity
}

// NOTE: Ang MenuItem data class ay DAPAT nasa sarili nitong file (MenuItem.kt)
// para maiwasan ang "Redeclaration" error.

class POSActivity : AppCompatActivity() {

    // ------------------- PROPERTIES -------------------
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private lateinit var pendingIntent: PendingIntent

    private lateinit var etSearchMenu: TextInputEditText
    private lateinit var pbMenuLoading: ProgressBar
    private lateinit var tvNoResults: TextView

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

    // FOCUS FIX: Property para sa dummy focus container
    private lateinit var focusDummyContainer: LinearLayout

    // MAX QUANTITY CONSTANT
    private val MAX_QUANTITY = 20

    // ------------------- LIFECYCLE -------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.canteen_pos) // Make sure this layout includes focus_dummy_container
        supportActionBar?.title = "Canteen Point of Sale"

        initializeViews()
        setupNFC()
        setupAdapters()
        setupListeners()
        setupSearch()

        // **FOCUS FIX:** Pilitin na makuha ng dummy container ang focus sa simula
        focusDummyContainer.requestFocus()

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
        pbLoading = findViewById(R.id.pbLoading)

        etSearchMenu = findViewById(R.id.etSearchMenu)
        pbMenuLoading = findViewById(R.id.pbMenuLoading)
        tvNoResults = findViewById(R.id.tvNoResults)

        // FOCUS FIX: Initialize the dummy container
        focusDummyContainer = findViewById(R.id.focus_dummy_container)
    }

    // ------------------- SEARCH & FILTERING -------------------

    private fun setupSearch() {
        etSearchMenu.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMenu(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
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

        menuAdapter.updateList(filteredList)

        // NO RESULTS CHECK
        if (filteredList.isEmpty()) {
            if (menuList.isEmpty() && query.isNullOrBlank()) {
                tvNoResults.text = "No items available in the menu yet."
            } else {
                tvNoResults.text = "No menu items found matching your search."
            }
            tvNoResults.visibility = View.VISIBLE
            rvMenu.visibility = View.GONE
        } else {
            tvNoResults.visibility = View.GONE
            rvMenu.visibility = View.VISIBLE
        }
    }

    // ------------------- LISTENERS & ADAPTER SETUP -------------------

    private fun setupListeners() {
        btnCheckout.setOnClickListener {
            // CHECKOUT VALIDATION: Siguraduhin na may item sa cart.
            if (validateCartQuantities()) {
                showCheckoutPrompt()
            } else {
                Toast.makeText(this, "Please add items to the cart first.", Toast.LENGTH_SHORT).show()
            }
        }

        // **NA-UPDATE NA LOGIC PARA SA CLEAR CART CONFIRMATION**
        btnCancelOrder.setOnClickListener {
            if (cartList.isEmpty()) {
                // Kung walang laman, i-reset lang ang state
                resetCheckoutState()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Clear Cart Confirmation")
                .setMessage("Are you sure you want to clear all ${cartList.size} items from the cart? This action cannot be undone.")
                .setPositiveButton("Yes, Clear Cart") { dialog, which ->
                    // Kung kinumpirma, i-reset ang checkout state
                    resetCheckoutState()
                    Toast.makeText(this, "Cart cleared.", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupAdapters() {
        // 1. Menu Adapter (Grid Layout)
        menuAdapter = MenuAdapterPOS(menuList) { menuItem ->
            addItemToCart(menuItem)
        }
        rvMenu.layoutManager = GridLayoutManager(this, 3)
        rvMenu.adapter = menuAdapter

        // 2. Cart Adapter (Linear Layout)
        cartAdapter = CartAdapter(cartList,
            onQuantityChange = { cartItem, newQuantity ->
                // Force minimum quantity to 1 if it somehow became 0 or less
                updateCartItemQuantity(cartItem, newQuantity)
            },
            onRemove = { cartItem ->
                removeItemFromCart(cartItem)
            },
            maxQuantity = MAX_QUANTITY
        )
        rvCart.layoutManager = LinearLayoutManager(this)
        rvCart.adapter = cartAdapter
    }


    // ------------------- DATA LOADING & REAL-TIME MENU -------------------

    private fun loadStaffCanteen() {
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

        pbMenuLoading.visibility = View.VISIBLE
        tvNoResults.visibility = View.GONE

        // Filter menu by canteenName AND available=true
        menuListener = firestore.collection("canteenMenu")
            .whereEqualTo("canteenName", staffCanteenName)
            .whereEqualTo("available", true)
            .addSnapshotListener { snapshot, error ->
                pbMenuLoading.visibility = View.GONE

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

                // Ginagamit ang kasalukuyang text sa search bar para sa filtering
                filterMenu(etSearchMenu.text.toString())
            }
    }

    override fun onResume() {
        super.onResume()
        pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        startMenuListener()
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
        menuListener?.remove()
    }

    // ------------------- CART MANAGEMENT -------------------

    private fun addItemToCart(menuItem: MenuItem) {
        val existingItem = cartList.find { it.menuId == menuItem.id }

        if (existingItem != null) {
            if (existingItem.quantity >= MAX_QUANTITY) {
                Toast.makeText(this, "Maximum quantity of $MAX_QUANTITY reached for ${menuItem.name}.", Toast.LENGTH_SHORT).show()
                return
            }
            existingItem.quantity++
        } else {
            val price = menuItem.price ?: 0.0
            if (price <= 0.0) {
                Toast.makeText(this, "${menuItem.name} has an invalid price.", Toast.LENGTH_SHORT).show()
                return
            }

            // Always add with quantity 1
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
        var finalQuantity = newQuantity

        // Force minimum quantity to 1
        if (newQuantity < 1) {
            finalQuantity = 1
        }

        cartItem.quantity = finalQuantity

        // Gumagamit ng post para maiwasan ang focus issues
        rvCart.post {
            cartAdapter.notifyDataSetChanged()
            updateTotal()
        }
    }

    private fun updateTotal() {
        val total = cartList.sumOf { it.subtotal }
        tvTotal.text = String.format(Locale.US, "₱%.2f", total)

        // Nagdi-disable sa Checkout button kung ₱0.00 ang total
        btnCheckout.isEnabled = total > 0.0

        if (total > 0.0) {
            btnCancelOrder.text = "Clear Cart"
            btnCancelOrder.visibility = View.VISIBLE
        } else {
            btnCancelOrder.visibility = View.GONE
        }
    }

    // ------------------- VALIDATION -------------------

    private fun validateCartQuantities(): Boolean {
        // Tiyakin na may laman ang cart bago mag-validate
        if (cartList.isEmpty()) return false

        var needsUpdate = false

        // Final check for max quantity clamping
        for (item in cartList) {
            if (item.quantity > MAX_QUANTITY) {
                // Clamping sa MAX_QUANTITY
                item.quantity = MAX_QUANTITY
                needsUpdate = true
                Toast.makeText(this, "The quantity for ${item.name} was capped at $MAX_QUANTITY.", Toast.LENGTH_LONG).show()
            }
        }

        // I-refresh ang UI at Total kung may pagbabago
        if (needsUpdate) {
            cartAdapter.notifyDataSetChanged()
            updateTotal()
        }

        // HULING CHECK: Kung blangko ang cart, bawal mag-checkout.
        return cartList.isNotEmpty()
    }

    // ------------------- CHECKOUT & NFC PROCESSING -------------------

    private fun showCheckoutPrompt() {
        val total = cartList.sumOf { it.subtotal }
        val totalFormatted = String.format(Locale.US, "₱%.2f", total)

        val builder = AlertDialog.Builder(this)
            .setTitle("Confirm Payment")
            .setMessage("Total Amount: $totalFormatted\n\nPlease select the payment method.")

        val hasWorkingNfc = nfcAdapter != null && nfcAdapter?.isEnabled == true

        if (hasWorkingNfc) {
            builder.setPositiveButton("SCAN RFID") { _, _ ->
                enterNfcScanningState()
            }
        } else {
            Log.w("POSActivity", "NFC is not available or disabled.")
            Toast.makeText(this, "NFC/RFID scanning is unavailable.", Toast.LENGTH_LONG).show()
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
        llNfcPrompt.visibility = View.VISIBLE
        tvNfcPrompt.text = "Awaiting customer RFID scan..."
        btnCheckout.isEnabled = false
        pbLoading.visibility = View.GONE

        btnCancelOrder.text = "Cancel/Change Payment"
        btnCancelOrder.visibility = View.VISIBLE
        // Logic for canceling payment scan remains the same
        btnCancelOrder.setOnClickListener {
            llNfcPrompt.visibility = View.GONE
            btnCheckout.isEnabled = true
            btnCancelOrder.text = "Clear Cart"
            btnCancelOrder.setOnClickListener { resetCheckoutState() }
            showCheckoutPrompt()
        }
    }

    private fun resetCheckoutState() {
        cartList.clear()
        cartAdapter.notifyDataSetChanged()
        updateTotal()

        scannedUserId = null
        scannedUserAccountId = null
        scannedUserRole = null

        llNfcPrompt.visibility = View.GONE
        btnCheckout.isEnabled = true
        // Important: After reset, ibabalik ang listener sa confirmation dialog setup
        btnCancelOrder.text = "Clear Cart"
        btnCancelOrder.setOnClickListener {
            if (cartList.isEmpty()) {
                resetCheckoutState()
                return@setOnClickListener
            }
            // Dapat ito ang mag-trigger ng confirmation dialog kapag may laman
            setupListeners()
        }
    }

    private fun setupNFC() {
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
            return
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (llNfcPrompt.visibility != View.VISIBLE) {
            return
        }
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra<android.nfc.Tag>(NfcAdapter.EXTRA_TAG)
            val rfidData = bytesToHex(tag?.id ?: return)

            tvNfcPrompt.text = "RFID Detected. Validating user..."
            loadUserByRfidForPayment(rfidData)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }.uppercase(Locale.US)
    }

    private fun logCashTransaction(amount: Double) {
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

    private fun loadUserByRfidForPayment(rfidData: String) {
        pbLoading.visibility = View.VISIBLE
        firestore.collection("users")
            .whereEqualTo("rfidTag", rfidData)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                pbLoading.visibility = View.GONE

                if (querySnapshot.isEmpty) {
                    Toast.makeText(this, "RFID tag not registered.", Toast.LENGTH_LONG).show()
                    tvNfcPrompt.text = "Error: Tag not registered. Please rescan."
                    return@addOnSuccessListener
                }

                val userDoc = querySnapshot.documents.first()
                val uid = userDoc.id
                val role = userDoc.getString("role")?.lowercase(Locale.getDefault())
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

                scannedUserId = uid
                scannedUserAccountId = accountId
                scannedUserRole = role

                processSecurePayment(totalAmount, uid, accountId, role)

            }.addOnFailureListener { e ->
                pbLoading.visibility = View.GONE
                Toast.makeText(this, "Error scanning RFID: ${e.message}", Toast.LENGTH_LONG).show()
                tvNfcPrompt.text = "Scan Error. Please rescan."
            }
    }

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
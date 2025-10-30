package com.example.datadomeapp.canteen

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Base64
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

class POSActivity : AppCompatActivity() {

    // ------------------- PROPERTIES -------------------
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private lateinit var pendingIntent: PendingIntent

    private val menuList = mutableListOf<MenuItem>()
    private val cartList = mutableListOf<CartItem>()
    private lateinit var menuAdapter: MenuAdapterPOS
    private lateinit var cartAdapter: CartAdapter
    private var menuListener: ListenerRegistration? = null
    private var staffCanteenName: String? = null
    private var staffUid: String? = null

    private var scannedUserId: String? = null // UID ng nagbabayad
    private var scannedUserAccountId: String? = null // Account ID (student/teacher ID)
    private var scannedUserRole: String? = null

    // UI Elements
    private lateinit var rvMenu: RecyclerView
    private lateinit var rvCart: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var btnCheckout: Button
    private lateinit var llNfcPrompt: LinearLayout
    private lateinit var tvNfcPrompt: TextView
    private lateinit var btnCancelOrder: Button
    private lateinit var pbLoading: ProgressBar

    // ------------------- LIFECYCLE -------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.canteen_pos)
        supportActionBar?.title = "Canteen Point of Sale"

        initializeViews()
        setupNFC()
        setupListeners()
        setupAdapters()

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

        // Filter menu by canteenName AND available=true
        menuListener = firestore.collection("canteenMenu")
            .whereEqualTo("canteenName", staffCanteenName)
            .whereEqualTo("available", true) // Only show available items
            .addSnapshotListener { snapshot, error ->
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
                menuAdapter.notifyDataSetChanged()
                pbLoading.visibility = View.GONE
            }
    }

    override fun onResume() {
        super.onResume()
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

            cartList.add(CartItem(menuItem.id, menuItem.name, price, 1))
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
    }

    // ------------------- CHECKOUT & NFC PROCESSING -------------------

    private fun showCheckoutPrompt() {
        val total = cartList.sumOf { it.subtotal }

        AlertDialog.Builder(this)
            .setTitle("Confirm Payment")
            .setMessage("Total Amount: ₱${String.format(Locale.US, "%.2f", total)}\n\nProceed to payment? Please scan the customer's RFID.")
            .setPositiveButton("SCAN RFID") { _, _ ->
                enterNfcScanningState()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enterNfcScanningState() {
        // Disable UI interactions, show prompt
        llNfcPrompt.visibility = View.VISIBLE
        tvNfcPrompt.text = "Awaiting customer scan..."
        btnCheckout.isEnabled = false // Disable Checkout button while scanning
        btnCancelOrder.visibility = View.VISIBLE
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
    }

    // --- NFC Setup & Handling (Reused from TopUpActivity) ---
    private fun setupNFC() {
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
    // -----------------------------------------------------

    // ------------------- DATA VALIDATION (PRE-PAYMENT) -------------------

    private fun loadUserByRfidForPayment(rfidData: String) {
        firestore.collection("users")
            .whereEqualTo("rfidTag", rfidData)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(this, "RFID tag not registered.", Toast.LENGTH_LONG).show()
                    tvNfcPrompt.text = "Scan Student/Teacher RFID Tag"
                    return@addOnSuccessListener
                }

                val userDoc = querySnapshot.documents.first()
                val uid = userDoc.id
                val role = userDoc.getString("role")
                val status = userDoc.getString("rfidStatus")?.uppercase(Locale.getDefault())
                val totalAmount = cartList.sumOf { it.subtotal }

                if (status != "ACTIVE") {
                    Toast.makeText(this, "Payment blocked: Account is currently ${status ?: "INACTIVE"}.", Toast.LENGTH_LONG).show()
                    tvNfcPrompt.text = "Account Inactive/Blocked"
                    // Huwag i-reset ang cart, hayaan ang user na mag-cancel o mag-rescan
                    return@addOnSuccessListener
                }

                // Only Students/Teachers can pay
                if (role == null || (role != "student" && role != "teacher")) {
                    Toast.makeText(this, "Unauthorized user type for payment.", Toast.LENGTH_LONG).show()
                    tvNfcPrompt.text = "Scan Student/Teacher RFID Tag"
                    return@addOnSuccessListener
                }

                val accountId = if (role == "student") userDoc.getString("studentId") else userDoc.getString("teacherId")

                if (accountId.isNullOrEmpty()) {
                    Toast.makeText(this, "Account ID link missing.", Toast.LENGTH_LONG).show()
                    tvNfcPrompt.text = "Scan Student/Teacher RFID Tag"
                    return@addOnSuccessListener
                }

                // Store user data
                scannedUserId = uid
                scannedUserAccountId = accountId
                scannedUserRole = role

                // Proceed to secure transaction
                processSecurePayment(totalAmount, uid, accountId, role)

            }.addOnFailureListener { e ->
                Toast.makeText(this, "Error scanning RFID: ${e.message}", Toast.LENGTH_LONG).show()
                tvNfcPrompt.text = "Scan Student/Teacher RFID Tag"
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
            // 1. READ the latest balance inside the transaction
            val snapshot = transaction.get(accountRef)
            val currentBalance = snapshot.getDouble("balance") ?: 0.0

            // 2. CHECK if balance is sufficient
            if (currentBalance < totalAmount) {
                // Insufficient balance, throw to abort transaction
                throw Exception("INSUFFICIENT_BALANCE")
            }

            // 3. WRITE the new balance
            val newBalance = currentBalance - totalAmount
            transaction.update(accountRef, "balance", newBalance)

            // Return the new balance for the success handler
            return@runTransaction newBalance

        }.addOnSuccessListener { finalBalance ->
            pbLoading.visibility = View.GONE
            // Transaction succeeded. Log the purchase and show success dialog.
            logTransaction(userId, totalAmount, finalBalance, accountId, role)

            AlertDialog.Builder(this)
                .setTitle("Payment Successful! ✅")
                .setMessage("Amount Paid: ₱${String.format(Locale.US, "%.2f", totalAmount)}\nNew Balance: ₱${String.format(Locale.US, "%.2f", finalBalance)}")
                .setPositiveButton("DONE") { _, _ -> resetCheckoutState() }
                .show()

        }.addOnFailureListener { e ->
            pbLoading.visibility = View.GONE
            // Transaction failed.
            val message = when (e.message) {
                "INSUFFICIENT_BALANCE" -> "Payment Failed: Insufficient balance on account."
                else -> "Payment Failed: Transaction error or concurrency conflict. Please try again. Error: ${e.message}"
            }

            AlertDialog.Builder(this)
                .setTitle("Transaction Error")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> tvNfcPrompt.text = "Scan Student/Teacher RFID Tag" }
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
            "type" to "CASH_OUT", // Changed to CASH_OUT for POS payment
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
        // Log the transaction in a dedicated collection
        firestore.collection("transactions").add(transaction)
            .addOnFailureListener { e ->
                Toast.makeText(this, "Warning: Failed to log transaction history: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
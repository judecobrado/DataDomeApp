package com.example.datadomeapp.canteen

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.example.datadomeapp.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.util.*
import android.text.TextWatcher
import android.text.Editable

class TopUpActivity : AppCompatActivity() {

    // ---------------------------------------------------------------------------------------------
    // --- CLASS PROPERTIES ---
    // ---------------------------------------------------------------------------------------------
    private val firestore = FirebaseFirestore.getInstance()
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private lateinit var pendingIntent: PendingIntent

    // ⭐ MAXIMUM BALANCE LIMIT (Constant)
    private val MAX_BALANCE_LIMIT = 3000.0

    // Data Class for organized state management
    private data class ScannedUser(
        val uid: String,
        val role: String,
        val accountId: String,
        val currentBalance: Double
    )
    private var scannedUserData: ScannedUser? = null

    // UI Elements
    private lateinit var tvScanPrompt: TextView
    private lateinit var llTopUpForm: LinearLayout
    private lateinit var etAmount: EditText
    private lateinit var btnConfirmTopUp: Button
    private lateinit var btnCancel: Button
    private lateinit var tvStudentInfo: TextView
    private lateinit var tvCurrentBalance: TextView

    // Variable to prevent infinite loop during TextWatcher adjustments
    private var isUpdatingText = false

    // ---------------------------------------------------------------------------------------------
    // --- LIFECYCLE & INITIALIZATION ---
    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.canteen_top_up)

        initializeViews()
        setupNFC()
        setupListeners()
        addTextWatcherToAmount() // TextWatcher setup for real-time validation
        resetState()
    }

    private fun initializeViews() {
        tvScanPrompt = findViewById(R.id.tvScanPrompt)
        llTopUpForm = findViewById(R.id.llTopUpForm)
        etAmount = findViewById(R.id.etAmount)
        btnConfirmTopUp = findViewById(R.id.btnConfirmTopUp)
        btnCancel = findViewById(R.id.btnCancel)
        tvStudentInfo = findViewById(R.id.tvStudentInfo)
        tvCurrentBalance = findViewById(R.id.tvCurrentBalance)
    }

    private fun setupListeners() {
        btnCancel.setOnClickListener { resetState() }
        btnConfirmTopUp.setOnClickListener {
            // Final check just before processing
            if(scannedUserData == null) {
                Toast.makeText(this, "No user scanned. Please rescan.", Toast.LENGTH_LONG).show()
                resetState()
                return@setOnClickListener
            }
            processTopUp()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // --- INPUT VALIDATION & LIMIT CHECK ---
    // ---------------------------------------------------------------------------------------------

    private fun addTextWatcherToAmount() {
        etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(editable: Editable?) {
                if (isUpdatingText || editable == null) return

                val originalString = editable.toString()
                var newString = originalString

                // 1. DECIMAL LIMITER (Allows max 2 decimal places)
                // This regex allows digits, optionally followed by a dot and up to two digits.
                val decimalMatcher = Regex("^\\d+(\\.\\d{0,2})?\$")

                if (originalString.isNotEmpty()) {
                    if (!decimalMatcher.matches(originalString)) {
                        val dotIndex = originalString.indexOf('.')
                        if (dotIndex >= 0 && originalString.length > dotIndex + 3) {
                            // Truncate the string if it has more than 2 decimals
                            newString = originalString.substring(0, dotIndex + 3)
                        } else if (originalString.toDoubleOrNull() == null) {
                            // Clear input if it's completely invalid (e.g., multiple dots, non-numeric)
                            newString = ""
                        }
                    }
                }

                // 2. MAX BALANCE LIMIT CHECK (Auto-adjusts input amount)
                val amount = newString.toDoubleOrNull()
                val user = scannedUserData

                if (user != null && amount != null && amount > 0) {
                    val potentialNewBalance = user.currentBalance + amount

                    if (potentialNewBalance > MAX_BALANCE_LIMIT) {
                        // Calculate maximum allowed top-up amount
                        var maxAllowedTopUp = MAX_BALANCE_LIMIT - user.currentBalance

                        // If current balance is already at limit, set maxAllowedTopUp to 0
                        if (maxAllowedTopUp < 0) maxAllowedTopUp = 0.0

                        // Format the max allowed top-up amount to 2 decimal places
                        val adjustedAmountString = String.format(Locale.US, "%.2f", maxAllowedTopUp)

                        // Set the new string to the adjusted amount
                        newString = adjustedAmountString

                        // Show a temporary warning if the text was changed by the system
                        if (newString != originalString) {
                            Toast.makeText(this@TopUpActivity, "Amount adjusted: Reached maximum balance (₱${String.format(Locale.US, "%.2f", MAX_BALANCE_LIMIT)}).", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Apply the changes (either from decimal limiter or max balance limiter)
                if (newString != originalString) {
                    isUpdatingText = true
                    editable.clear()
                    editable.append(newString)
                    isUpdatingText = false
                }
            }
        })
    }


    // ---------------------------------------------------------------------------------------------
    // --- NFC SETUP & HANDLING ---
    // ---------------------------------------------------------------------------------------------

    private fun setupNFC() {
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == true) {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra<android.nfc.Tag>(NfcAdapter.EXTRA_TAG)
            val rfidData = bytesToHex(tag?.id ?: return)

            tvScanPrompt.text = "RFID Detected. Loading user..."
            loadUserByRfid(rfidData)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }.uppercase(Locale.US)
    }

    // ---------------------------------------------------------------------------------------------
    // --- DATA LOADING & VALIDATION ---
    // ---------------------------------------------------------------------------------------------

    private fun loadUserByRfid(rfidData: String) {
        firestore.collection("users")
            .whereEqualTo("rfidTag", rfidData)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(this, "RFID tag not registered.", Toast.LENGTH_LONG).show()
                    resetState()
                    return@addOnSuccessListener
                }

                val userDoc = querySnapshot.documents.first()
                val uid = userDoc.id
                val role = userDoc.getString("role")
                val status = userDoc.getString("rfidStatus")?.uppercase(Locale.getDefault())

                val accountId = if (role == "student") userDoc.getString("studentId") else userDoc.getString("teacherId")

                if (status != "ACTIVE") {
                    Toast.makeText(this, "Account is currently ${status ?: "INACTIVE"}. Top-up blocked.", Toast.LENGTH_LONG).show()
                    resetState()
                    return@addOnSuccessListener
                }

                if (role == null || (role != "student" && role != "teacher")) {
                    Toast.makeText(this, "Invalid or unauthorized user type.", Toast.LENGTH_LONG).show()
                    resetState()
                    return@addOnSuccessListener
                }

                if (accountId.isNullOrEmpty()) {
                    Toast.makeText(this, "Account ID link missing from user record. Please check Firestore.", Toast.LENGTH_LONG).show()
                    resetState()
                    return@addOnSuccessListener
                }

                fetchAccountDetails(uid, accountId, role)

            }.addOnFailureListener { e ->
                Toast.makeText(this, "Error scanning RFID: ${e.message}", Toast.LENGTH_LONG).show()
                resetState()
            }
    }

    private fun fetchAccountDetails(uid: String, accountId: String, role: String) {
        val collectionName = if (role == "student") "students" else "teachers"
        val capitalizedRole = role.replaceFirstChar { it.uppercase() }

        firestore.collection(collectionName).document(accountId).get()
            .addOnSuccessListener { accountDoc ->
                if (accountDoc.exists()) {
                    val firstName = accountDoc.getString("firstName") ?: ""
                    val lastName = accountDoc.getString("lastName") ?: ""

                    // Safely get balance, defaulting to 0.0
                    val rawBalance = accountDoc.getDouble("balance") ?: 0.0

                    // Format the balance to ensure exactly 2 decimal places are used for consistency
                    val balance = String.format(Locale.US, "%.2f", rawBalance).toDouble()

                    // Store all necessary data in the Data Class
                    scannedUserData = ScannedUser(uid, role, accountId, balance)

                    // UI Updates
                    tvStudentInfo.text = "$firstName $lastName ($capitalizedRole)"
                    tvCurrentBalance.text = "Current Balance: ₱${String.format(Locale.US, "%.2f", balance)}"
                    tvStudentInfo.visibility = View.VISIBLE
                    tvCurrentBalance.visibility = View.VISIBLE
                    tvScanPrompt.text = "Ready to Top Up"
                    llTopUpForm.visibility = View.VISIBLE
                    etAmount.requestFocus()
                } else {
                    // ⭐ FIXED: Changed Toast.LONG to Toast.LENGTH_LONG
                    Toast.makeText(this, "Account details not found in $collectionName.", Toast.LENGTH_LONG).show()
                    resetState()
                }
            }.addOnFailureListener { e ->
                // ⭐ FIXED: Changed Toast.LONG to Toast.LENGTH_LONG
                Toast.makeText(this, "Error fetching account details: ${e.message}", Toast.LENGTH_LONG).show()
                resetState()
            }
    }

    // ---------------------------------------------------------------------------------------------
    // --- TOP-UP PROCESSING ---
    // ---------------------------------------------------------------------------------------------

    private fun processTopUp() {
        val user = scannedUserData
        // Kukunin na lang ang value dahil nag-adjust na ang TextWatcher
        val topUpAmount = etAmount.text.toString().toDoubleOrNull()

        // Final Validation
        if (topUpAmount == null || topUpAmount <= 0) {
            Toast.makeText(this, "Enter a valid positive amount.", Toast.LENGTH_SHORT).show()
            return
        }
        if (user == null) {
            Toast.makeText(this, "No user scanned. Please rescan.", Toast.LENGTH_LONG).show()
            resetState()
            return
        }

        // Check if the amount is 0 because the user is already at the limit
        if (topUpAmount == 0.0 && user.currentBalance >= MAX_BALANCE_LIMIT) {
            AlertDialog.Builder(this)
                .setTitle("Balance Limit Reached (₱${String.format(Locale.US, "%.2f", MAX_BALANCE_LIMIT)})")
                .setMessage("The current balance is already at the maximum limit. Cannot top up.")
                .setPositiveButton("OK", null)
                .show()
            etAmount.setText("")
            return
        }


        val collectionName = if (user.role == "student") "students" else "teachers"
        val finalNewBalance = user.currentBalance + topUpAmount

        // Safety check: Since TextWatcher already adjusted and limited the input,
        // this check is for final assurance that the new balance does not exceed the limit.
        if (finalNewBalance > MAX_BALANCE_LIMIT + 0.01) {
            Toast.makeText(this, "System error: Balance exceeded limit after adjustment. Please try again.", Toast.LENGTH_LONG).show()
            resetState()
            return
        }

        // Perform the database update since all limits and decimal checks passed
        performDatabaseUpdate(user, collectionName, topUpAmount, finalNewBalance)
    }

    /**
     * Executes the actual Firestore balance update and transaction logging.
     */
    private fun performDatabaseUpdate(
        user: ScannedUser,
        collectionName: String,
        topUpAmount: Double,
        newBalance: Double
    ) {
        // Ensure the amount and balance are formatted to 2 decimal places before saving to Firestore
        val finalTopUpAmount = String.format(Locale.US, "%.2f", topUpAmount).toDouble()
        val finalNewBalance = String.format(Locale.US, "%.2f", newBalance).toDouble()

        // 1. Update the balance in the student/teacher collection
        firestore.collection(collectionName).document(user.accountId).update("balance", finalNewBalance)
            .addOnSuccessListener {
                // 2. Log the transaction and then show success dialog
                logTransaction(user.uid, finalTopUpAmount, finalNewBalance, user.accountId, user.role)

                AlertDialog.Builder(this)
                    .setTitle("Top-Up Successful! 💸")
                    .setMessage("Amount: ₱${String.format(Locale.US, "%.2f", finalTopUpAmount)}\nNew Balance: ₱${String.format(Locale.US, "%.2f", finalNewBalance)}")
                    .setPositiveButton("OK") { _, _ -> resetState() }
                    .show()
            }
            .addOnFailureListener { e ->
                // ⭐ FIXED: Changed Toast.LONG to Toast.LENGTH_LONG
                Toast.makeText(this, "Top-Up failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Logs the transaction using FieldValue.serverTimestamp() for accuracy.
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
            "type" to "CASH_IN",
            "amount" to amount,
            "timestamp" to FieldValue.serverTimestamp(), // Use Server Timestamp for accuracy
            "finalBalance" to finalBalance
        )
        // Log the transaction in a dedicated collection
        firestore.collection("transactions").add(transaction)
            .addOnFailureListener { e ->
                // This is a warning, as the balance update was successful.
                Toast.makeText(this, "Warning: Failed to log transaction history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------------------------------------------------------------------------------------------
    // --- UI State Management ---
    // ---------------------------------------------------------------------------------------------

    private fun resetState() {
        // Reset all global state holders
        scannedUserData = null

        // Reset UI
        llTopUpForm.visibility = View.GONE
        tvScanPrompt.text = "Scan Student/Teacher RFID Tag"
        tvStudentInfo.visibility = View.GONE
        tvCurrentBalance.visibility = View.GONE
        etAmount.setText("")
    }
}
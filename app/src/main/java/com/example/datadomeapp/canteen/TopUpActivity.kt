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

class TopUpActivity : AppCompatActivity() {

    // ---------------------------------------------------------------------------------------------
    // --- CLASS PROPERTIES ---
    // ---------------------------------------------------------------------------------------------
    private val firestore = FirebaseFirestore.getInstance()
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private lateinit var pendingIntent: PendingIntent

    // ⭐ MAXIMUM BALANCE LIMIT
    private val MAX_BALANCE_LIMIT = 1000.0

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

    // ---------------------------------------------------------------------------------------------
    // --- LIFECYCLE & INITIALIZATION ---
    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.canteen_top_up)

        initializeViews()
        setupNFC()
        setupListeners()
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
        btnConfirmTopUp.setOnClickListener { processTopUp() }
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
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
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
                    return@addOnSuccessListener // Ititigil ang execution dito
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
                    val firstName = accountDoc.getString("firstName") ?: "N/A"
                    val lastName = accountDoc.getString("lastName") ?: ""
                    val balance = accountDoc.getDouble("balance") ?: 0.0

                    // Store all necessary data in the Data Class
                    scannedUserData = ScannedUser(uid, role, accountId, balance)

                    // UI Updates
                    tvStudentInfo.text = "User: $firstName $lastName ($capitalizedRole)"
                    tvCurrentBalance.text = "Current Balance: ₱${String.format(Locale.US, "%.2f", balance)}"
                    tvStudentInfo.visibility = View.VISIBLE
                    tvCurrentBalance.visibility = View.VISIBLE
                    tvScanPrompt.text = "Ready to Top Up"
                    llTopUpForm.visibility = View.VISIBLE
                    etAmount.requestFocus()
                } else {
                    Toast.makeText(this, "Account details not found in $collectionName.", Toast.LENGTH_LONG).show()
                    resetState()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching account details: ${e.message}", Toast.LENGTH_LONG).show()
                resetState()
            }
    }

    // ---------------------------------------------------------------------------------------------
    // --- TOP-UP PROCESSING ---
    // ---------------------------------------------------------------------------------------------

    private fun processTopUp() {
        val user = scannedUserData
        val topUpAmount = etAmount.text.toString().toDoubleOrNull()

        // Final Validation (Amount check and User check)
        if (topUpAmount == null || topUpAmount <= 0) {
            Toast.makeText(this, "Enter a valid positive amount.", Toast.LENGTH_SHORT).show()
            return
        }
        if (user == null) {
            Toast.makeText(this, "No user scanned. Please rescan.", Toast.LENGTH_LONG).show()
            resetState()
            return
        }

        val collectionName = if (user.role == "student") "students" else "teachers"
        val newBalance = user.currentBalance + topUpAmount

        // ⭐ MAXIMUM BALANCE LIMIT CHECK
        if (newBalance > MAX_BALANCE_LIMIT) {
            val overAmount = newBalance - MAX_BALANCE_LIMIT

            // Magpakita ng alert sa user na lalagpas na sa limit.
            AlertDialog.Builder(this)
                .setTitle("Limit Reached (₱${String.format(Locale.US, "%.2f", MAX_BALANCE_LIMIT)})")
                .setMessage("The total balance (₱${String.format(Locale.US, "%.2f", newBalance)}) exceeds the limit by ₱${String.format(Locale.US, "%.2f", overAmount)}. Please enter a smaller amount.")
                .setPositiveButton("OK", null)
                .show()

            etAmount.setText("") // Clear the input field
            return
        }

        // 1. Update the balance in the student/teacher collection
        firestore.collection(collectionName).document(user.accountId).update("balance", newBalance)
            .addOnSuccessListener {
                // 2. Log the transaction and then show success dialog
                logTransaction(user.uid, topUpAmount, newBalance, user.accountId, user.role)

                AlertDialog.Builder(this)
                    .setTitle("Top-Up Successful! 💸")
                    .setMessage("Amount: ₱${String.format(Locale.US, "%.2f", topUpAmount)}\nNew Balance: ₱${String.format(Locale.US, "%.2f", newBalance)}")
                    .setPositiveButton("OK") { _, _ -> resetState() }
                    .show()
            }
            .addOnFailureListener { e ->
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
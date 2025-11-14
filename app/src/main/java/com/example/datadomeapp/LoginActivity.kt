package com.example.datadomeapp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.admin.AdminDashboardActivity
import com.example.datadomeapp.canteen.CanteenStaffDashboardActivity
import com.example.datadomeapp.student.StudentDashboardActivity
import com.example.datadomeapp.teacher.TeacherDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Hardcoded admin credentials for development bypass
    private val adminEmail = "q"
    private val adminPassword = "q"

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var forgotPasswordText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRfidLogin: Button // RFID Login Button

    // RFID/NFC Declarations
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var isNfcSupported = false
    private var rfidDetectionDialog: AlertDialog? = null

    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize UI elements
        emailEditText = findViewById(R.id.etEmail)
        passwordEditText = findViewById(R.id.etPassword)
        loginButton = findViewById(R.id.btnLogin)
        forgotPasswordText = findViewById(R.id.tvForgotPassword)
        progressBar = findViewById(R.id.progressBar)
        btnRfidLogin = findViewById(R.id.btnRfidLogin)

        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }

        // 🛑 CRITICAL FIX: Tinanggal ang maling session check logic dito.
        // Ang LoginActivity ay hindi dapat agad mag-exit kapag walang UID.

        // RFID Button Listener
        btnRfidLogin.setOnClickListener {
            showRfidDetectionDialog()
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showLoading()

            // Hardcoded admin login (Development Bypass)
            if (email == adminEmail && password == adminPassword) {
                getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
                    .putString("role", "admin")
                    .apply()

                hideLoading()
                Toast.makeText(this, "Welcome Admin (Hardcoded)!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, AdminDashboardActivity::class.java))
                finish()
                return@setOnClickListener
            }

            // Standard Firebase login
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val currentUser = auth.currentUser
                        if (currentUser == null) {
                            resetUI()
                            Toast.makeText(this, "Login successful but user data is temporarily unavailable.", Toast.LENGTH_LONG).show()
                            return@addOnCompleteListener
                        }
                        fetchUserRoleAndStartDashboard(currentUser.uid)

                    } else {
                        // Auth failed
                        resetUI()
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        // Setup NFC
        setupNfc()
    }

    // --- NFC Setup and Lifecycle Handlers ---

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not supported on this device. RFID login disabled.", Toast.LENGTH_LONG).show()
            isNfcSupported = false
            btnRfidLogin.isEnabled = false
            return
        }

        isNfcSupported = true
        // Gumamit ng FLAG_MUTABLE or FLAG_IMMUTABLE
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Gumamit ng FLAG_IMMUTABLE kung posible, pero sinunod ko ang iyong FLAG_MUTABLE or FLAG_UPDATE_CURRENT setup
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onResume() {
        super.onResume()
        // I-enable lang ang foreground dispatch kung may NFC support AT HINDI naka-login
        if (isNfcSupported && auth.currentUser == null) {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isNfcSupported) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
    }

    // 🛑 CRITICAL: Ito ang mag-de-detect ng RFID/NFC scan!
    override fun onNewIntent(intent: Intent) {
        if (!isNfcSupported) {
            super.onNewIntent(intent)
            return
        }

        super.onNewIntent(intent)

        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val rfidHex = bytesToHex(tag.id)
                Log.d(TAG, "RFID Detected for Login: $rfidHex")

                // Trigger RFID Login
                performRfidLogin(rfidHex)
            }
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v: Int = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    // --- RFID Login Core Logic ---

    private fun showRfidDetectionDialog() {
        if (!isNfcSupported) {
            Toast.makeText(this, "NFC is required for RFID login.", Toast.LENGTH_LONG).show()
            return
        }

        // I-close muna ang dating dialog kung meron
        rfidDetectionDialog?.dismiss()

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rfid_detection, null)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvRfidDetectionStatus)

        tvStatus.text = "Waiting for RFID/NFC tag scan...\n\nBring your ID near the phone's NFC area."

        // Mag-display ng temporary loading para maging malinaw na may ginagawa
        showLoading()

        rfidDetectionDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("RFID Login")
            .setPositiveButton("Cancel") { _, _ ->
                rfidDetectionDialog?.dismiss()
                resetUI() // Ibalik sa normal ang UI
            }
            .setCancelable(false)
            .create()

        rfidDetectionDialog!!.show()
    }

    private fun performRfidLogin(rfidTag: String) {
        // Hahanapin ang user sa 'users' collection gamit ang rfidTag.
        // Hahanapin din ang rfidStatus na 'ACTIVE' para hindi mag-login ang DISABLED tags.
        firestore.collection("users")
            .whereEqualTo("rfidTag", rfidTag)
            .whereEqualTo("rfidStatus", "ACTIVE")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->

                rfidDetectionDialog?.dismiss() // I-dismiss ang dialog agad

                if (snapshot.isEmpty) {
                    resetUI()
                    Toast.makeText(this, "Login failed: RFID tag is either not registered or is DISABLED.", Toast.LENGTH_LONG).show()
                } else {
                    val userDoc = snapshot.documents.first()
                    val uid = userDoc.id // Ito na ang User UID

                    val role = userDoc.getString("role")
                    val finalRole = role ?: "unknown"

                    Log.d(TAG, "RFID Login SUCCESS: UID: $uid, Role: $finalRole")

                    // Save role to shared preferences for later use in the app
                    getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
                        .putString("role", finalRole)
                        .apply()

                    hideLoading()
                    Toast.makeText(this, "Welcome ${finalRole.uppercase(Locale.getDefault())}!", Toast.LENGTH_LONG).show()
                    // 🛑 CRITICAL FIX: Tiyakin na ang role at uid ang ipinapasa
                    startDashboard(finalRole , uid)
                }
            }
            .addOnFailureListener { e ->
                rfidDetectionDialog?.dismiss() // I-dismiss ang dialog agad
                resetUI()
                Toast.makeText(this, "RFID Login Error: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "RFID Login failed", e)
            }
    }


    private fun fetchUserRoleAndStartDashboard(uid: String) {
        // Fetch the role from the 'users' collection in Firestore
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    resetUI()
                    Log.e(TAG, "User document does not exist for UID: $uid")
                    Toast.makeText(this, "User data setup incomplete. Please contact admin.", Toast.LENGTH_LONG).show()
                    auth.signOut()
                    return@addOnSuccessListener
                }

                val role = doc.getString("role")

                // CRITICAL DEBUG LOGGING
                Log.d(TAG, "Fetched role from Firestore: $role")

                val finalRole = role ?: "unknown"

                // Save role to shared preferences for later use in the app
                getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
                    .putString("role", finalRole)
                    .apply()

                hideLoading()
                startDashboard(finalRole, uid)
            }
            .addOnFailureListener { e ->
                // Firestore fetch failed
                resetUI()
                Log.e(TAG, "Error fetching user role for UID $uid: ${e.message}")
                Toast.makeText(this, "Error fetching user role: ${e.message}", Toast.LENGTH_SHORT).show()
                auth.signOut()
            }
    }

    private fun startDashboard(role: String, uid: String) {
        // Use lowercase to match roles consistently
        val roleLower = role.lowercase(Locale.getDefault())

        val intent = when (roleLower) {
            "student" -> Intent(this, StudentDashboardActivity::class.java)
            "teacher" -> Intent(this, TeacherDashboardActivity::class.java)
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "canteen_staff" -> Intent(this, CanteenStaffDashboardActivity::class.java)
            else -> {
                Toast.makeText(this, "Role '$role' not recognized. Defaulting to main screen.", Toast.LENGTH_LONG).show()
                Intent(this, MainActivity::class.java)
            }
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        // Ipinapasa ang UID sa Intent para gamitin sa dashboard (Ito ang solusyon)
        intent.putExtra("USER_UID", uid)

        startActivity(intent)
        finish() // Prevent returning to the LoginActivity
    }

    // --- Utility functions for UI handling ---
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        loginButton.isEnabled = false
        forgotPasswordText.isEnabled = false
        btnRfidLogin.isEnabled = false // Disable RFID button too
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        loginButton.isEnabled = true
        forgotPasswordText.isEnabled = true
        btnRfidLogin.isEnabled = isNfcSupported // Ibalik sa true kung supported ang NFC
    }

    private fun resetUI() {
        hideLoading()
    }
}
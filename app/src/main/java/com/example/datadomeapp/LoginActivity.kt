package com.example.datadomeapp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter // 🟢 NEW
import android.nfc.Tag // 🟢 NEW
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.admin.AdminDashboardActivity
import com.example.datadomeapp.canteen.CanteenStaffDashboardActivity
import com.example.datadomeapp.student.StudentDashboardActivity
import com.example.datadomeapp.teacher.TeacherDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope // 🟢 NEW
import kotlinx.coroutines.Dispatchers // 🟢 NEW
import kotlinx.coroutines.launch // 🟢 NEW
import kotlinx.coroutines.tasks.await // 🟢 NEW

class LoginActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Hardcoded credentials
    private val adminEmail = "q"
    private val adminPassword = "q"

    // 🟢 NEW: Hardcoded password for RFID/Auto-Login (Security note: Use Custom Tokens in production)
    private val RFID_AUTOLOGIN_PASSWORD = "datarome_temp_password"

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var forgotPasswordText: TextView
    private lateinit var progressBar: ProgressBar

    // 🟢 NFC Declarations
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var isNfcSupported = false

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

        setupNfc() // 🟢 Initialize NFC

        forgotPasswordText.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            // ... (rest of standard login logic)
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
                        // Continue to Firestore fetch
                        fetchUserRoleAndStartDashboard(currentUser.uid)

                    } else {
                        // Auth failed
                        resetUI()
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    // --- NFC Setup and Lifecycle ---

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Log.w(TAG, "NFC is not supported on this device.")
            isNfcSupported = false
            return
        }

        isNfcSupported = true
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Gumamit ng FLAG_MUTABLE
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
    }

    override fun onResume() {
        super.onResume()
        if (isNfcSupported) {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isNfcSupported) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
    }

    // 🛑 CRITICAL: RFID Scan Handler
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
                Log.d("NFC_SCAN", "RFID Detected for Auto-Login: $rfidHex")

                showLoading()

                // I-start ang RFID auto-login process
                CoroutineScope(Dispatchers.Main).launch {
                    performRfidAutoLogin(rfidHex)
                }
            }
        }
    }

    // --- RFID Auto-Login Core Logic ---

    private suspend fun performRfidAutoLogin(rfidTag: String) {
        // Mag-try muna sa Student Collection
        var userEmail = getEmailByRfid("students", rfidTag)

        // Kung walang nakita, mag-try sa Teacher Collection
        if (userEmail == null) {
            userEmail = getEmailByRfid("teachers", rfidTag)
        }

        // Kung wala pa rin, mag-try sa Canteen Staff (kung may sariling collection)
        if (userEmail == null) {
            // Assuming canteen_staff has its own collection with rfidTag
            userEmail = getEmailByRfid("canteen_staff", rfidTag)
        }


        if (userEmail == null) {
            resetUI()
            Toast.makeText(this, "RFID Tag is not registered to any active user.", Toast.LENGTH_LONG).show()
            return
        }

        // I-set ang email field (for visual confirmation)
        emailEditText.setText(userEmail)

        // Mag-sign in gamit ang nahanap na email at hardcoded password
        auth.signInWithEmailAndPassword(userEmail, RFID_AUTOLOGIN_PASSWORD)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "RFID Auto-Login Successful!", Toast.LENGTH_SHORT).show()
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        fetchUserRoleAndStartDashboard(currentUser.uid)
                    } else {
                        resetUI()
                    }
                } else {
                    resetUI()
                    Log.e(TAG, "RFID Auth failed: ${task.exception?.message}")
                    Toast.makeText(this, "Auto-Login Failed. Password mismatch or user account error.", Toast.LENGTH_LONG).show()
                }
            }
    }

    // 🟢 NEW: Utility function to search for email by RFID tag in a specific collection
    private suspend fun getEmailByRfid(collectionPath: String, rfidTag: String): String? {
        return try {
            val snapshot = firestore.collection(collectionPath)
                .whereEqualTo("rfidTag", rfidTag)
                .limit(1)
                .get()
                .await()

            val uid = snapshot.documents.firstOrNull()?.getString("uid")

            // Check if UID exists in the 'users' collection (para makuha ang email)
            if (uid != null) {
                val userDoc = firestore.collection("users").document(uid).get().await()
                return userDoc.getString("email")
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user email by RFID from $collectionPath: ${e.message}")
            null
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        // Utility function (Same as in ManageStudentsActivity)
        val hexArray = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v: Int = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
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
                startDashboard(finalRole)
            }
            .addOnFailureListener { e ->
                // Firestore fetch failed
                resetUI()
                Log.e(TAG, "Error fetching user role for UID $uid: ${e.message}")
                Toast.makeText(this, "Error fetching user role: ${e.message}", Toast.LENGTH_SHORT).show()
                auth.signOut()
            }
    }

    private fun startDashboard(role: String) {
        // Use lowercase to match roles consistently
        val roleLower = role.lowercase()

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

        startActivity(intent)
        finish() // Prevent returning to the LoginActivity
    }

    // --- Utility functions for UI handling ---
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        loginButton.isEnabled = false
        forgotPasswordText.isEnabled = false
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        loginButton.isEnabled = true
        forgotPasswordText.isEnabled = true
    }

    private fun resetUI() {
        hideLoading()
    }
}

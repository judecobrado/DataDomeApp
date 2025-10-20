package com.example.datadomeapp.enrollment

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class VerifyLinkActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val link = intent?.data.toString()
        val email = intent?.getStringExtra("email") ?: ""

        if (link.isNotEmpty() && auth.isSignInWithEmailLink(link)) {
            auth.signInWithEmailLink(email, link)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Email verified successfully!", Toast.LENGTH_SHORT)
                            .show()
                        // Mark verified in pending_enrollments (if exists)
                        db.collection("pending_enrollments")
                            .whereEqualTo("email", email)
                            .get()
                            .addOnSuccessListener { docs ->
                                if (!docs.isEmpty) {
                                    val docId = docs.documents[0].id
                                    db.collection("pending_enrollments").document(docId)
                                        .update("emailVerified", true)
                                }
                                // Proceed to enrollment
                                val intent = Intent(this, EnrollmentActivity::class.java)
                                intent.putExtra("email", email)
                                startActivity(intent)
                                finish()
                            }
                    } else {
                        Toast.makeText(
                            this,
                            "Failed to verify email: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }
}
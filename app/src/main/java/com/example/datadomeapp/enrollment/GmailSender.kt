package com.example.datadomeapp.enrollment

import android.util.Log
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class GmailSender {

    companion object {
        private const val TAG = "GmailSender"
        private const val SENDER_EMAIL = "dtdmspprt@gmail.com"
        private const val SENDER_PASSWORD = "ykdp awlm zsir vnyz"
        private const val SMTP_HOST = "smtp.gmail.com"
        private const val SMTP_PORT = "587"
    }

    // Original method (keep for compatibility)
    fun sendVerificationCode(toEmail: String, verificationCode: String): Boolean {
        return sendVerificationCode(toEmail, verificationCode, object : EmailSendCallback {
            override fun onSending() {}
            override fun onSuccess() {}
            override fun onComplete(success: Boolean) {}
        })
    }

    // New method with loading callback
    fun sendVerificationCode(toEmail: String, verificationCode: String, callback: EmailSendCallback): Boolean {
        return try {
            Log.d(TAG, "Attempting to send email to: $toEmail")
            callback.onSending()

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", SMTP_HOST)
                put("mail.smtp.port", SMTP_PORT)
                put("mail.smtp.ssl.trust", SMTP_HOST)
                put("mail.smtp.ssl.protocols", "TLSv1.2")
                put("mail.smtp.timeout", "20000")
                put("mail.smtp.connectiontimeout", "20000")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD)
                }
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(SENDER_EMAIL, "Data Dome App"))
                addRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
                subject = "Your Verification Code - Data Dome App"
                setText(
                    """
                    Data Dome App - Email Verification

                    Your verification code is: 
                    
                    🔒 $verificationCode
                    
                    Please enter this code in the app to verify your email address.
                    
                    This code will expire in 5 minutes.
                    
                    If you didn't request this code, please ignore this email.
                    
                    Best regards,
                    Data Dome App Team
                    """.trimIndent()
                )
            }

            Thread {
                try {
                    Transport.send(message)
                    Log.d(TAG, "✅ Verification code sent successfully to $toEmail")
                    callback.onSuccess()
                    callback.onComplete(true)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send email: ${e.message}")
                    e.printStackTrace()
                    callback.onComplete(false)
                }
            }.start()

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Email preparation failed: ${e.message}")
            e.printStackTrace()
            callback.onComplete(false)
            false
        }
    }
}

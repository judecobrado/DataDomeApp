package com.example.datadomeapp.enrollment

import android.util.Log
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class GmailSender {

    companion object {
        private const val TAG = "GmailSender"
        private const val SENDER_EMAIL = "dtdmspprt@gmail.com"
        private const val SENDER_PASSWORD = "ykdp awlm zsir vnyz"
        private const val SMTP_HOST = "smtp.gmail.com"
        private const val SMTP_PORT = "587"
        private const val LOGO_URL = "https://i.ibb.co/mCqtW1TJ/logo.png"
    }

    // Original method (keep for compatibility)
    fun sendVerificationCode(toEmail: String, verificationCode: String): Boolean {
        return sendVerificationCode(toEmail, verificationCode, object : EmailSendCallback {
            override fun onSending() {}
            override fun onSuccess() {}
            override fun onComplete(success: Boolean) {}
        })
    }

    // Enhanced method with your logo
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
                subject = "Verify Your Email - Data Dome App"

                // Create multipart message with both text and HTML versions
                setContent(createEmailContent(verificationCode))
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

    // NEW: Send enrollment email
    fun sendEnrollmentEmail(toEmail: String, studentId: String, password: String, callback: EmailSendCallback): Boolean {
        return try {
            Log.d(TAG, "Attempting to send enrollment email to: $toEmail")
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
                setFrom(InternetAddress(SENDER_EMAIL, "Data Dome University"))
                addRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
                subject = "🎉 Congratulations! You Have Been Accepted to Data Dome University"

                // Create multipart message with both text and HTML versions
                setContent(createEnrollmentEmailContent(studentId, password))
            }

            Thread {
                try {
                    Transport.send(message)
                    Log.d(TAG, "✅ Enrollment email sent successfully to $toEmail")
                    callback.onSuccess()
                    callback.onComplete(true)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send enrollment email: ${e.message}")
                    e.printStackTrace()
                    callback.onComplete(false)
                }
            }.start()

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Enrollment email preparation failed: ${e.message}")
            e.printStackTrace()
            callback.onComplete(false)
            false
        }
    }

    // NEW: Send rejection email
    fun sendRejectionEmail(toEmail: String, callback: EmailSendCallback): Boolean {
        return try {
            Log.d(TAG, "Attempting to send rejection email to: $toEmail")
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
                setFrom(InternetAddress(SENDER_EMAIL, "Data Dome University"))
                addRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
                subject = "Application Status Update - Data Dome University"

                setContent(createRejectionEmailContent())
            }

            Thread {
                try {
                    Transport.send(message)
                    Log.d(TAG, "✅ Rejection email sent successfully to $toEmail")
                    callback.onSuccess()
                    callback.onComplete(true)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to send rejection email: ${e.message}")
                    e.printStackTrace()
                    callback.onComplete(false)
                }
            }.start()

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Rejection email preparation failed: ${e.message}")
            e.printStackTrace()
            callback.onComplete(false)
            false
        }
    }

    private fun createEmailContent(verificationCode: String): Multipart {
        val multipart = MimeMultipart("alternative")

        // Add plain text version (for email clients that don't support HTML)
        val textPart = MimeBodyPart()
        textPart.setText(createPlainTextContent(verificationCode))
        multipart.addBodyPart(textPart)

        // Add HTML version (for modern email clients)
        val htmlPart = MimeBodyPart()
        htmlPart.setContent(createHtmlContent(verificationCode), "text/html; charset=utf-8")
        multipart.addBodyPart(htmlPart)

        return multipart
    }

    private fun createEnrollmentEmailContent(studentId: String, password: String): Multipart {
        val multipart = MimeMultipart("alternative")

        // Add plain text version
        val textPart = MimeBodyPart()
        textPart.setText(createEnrollmentPlainTextContent(studentId, password))
        multipart.addBodyPart(textPart)

        // Add HTML version
        val htmlPart = MimeBodyPart()
        htmlPart.setContent(createEnrollmentHtmlContent(studentId, password), "text/html; charset=utf-8")
        multipart.addBodyPart(htmlPart)

        return multipart
    }

    private fun createRejectionEmailContent(): Multipart {
        val multipart = MimeMultipart("alternative")

        // Add plain text version
        val textPart = MimeBodyPart()
        textPart.setText(createRejectionPlainTextContent())
        multipart.addBodyPart(textPart)

        // Add HTML version
        val htmlPart = MimeBodyPart()
        htmlPart.setContent(createRejectionHtmlContent(), "text/html; charset=utf-8")
        multipart.addBodyPart(htmlPart)

        return multipart
    }

    private fun createPlainTextContent(verificationCode: String): String {
        return """
            DATA DOME APP - EMAIL VERIFICATION
            
            Your verification code is: 
            
            ${verificationCode}
            
            Please enter this code in the app to verify your email address.
            
            This code will expire in 5 minutes.
            
            If you didn't request this code, please ignore this email.
            
            Best regards,
            Data Dome App Team
            
            Need help? Contact us at dtdmspprt@gmail.com
        """.trimIndent()
    }

    private fun createEnrollmentPlainTextContent(studentId: String, password: String): String {
        return """
            CONGRATULATIONS ON YOUR ACCEPTANCE TO DATA DOME UNIVERSITY!
            
            Dear Student,
            
            We are thrilled to inform you that you have been accepted to Data Dome University!
            
            Your Student Credentials:
            Student ID: $studentId
            Temporary Password: $password
            
            Please log in to the student portal using these credentials to begin your academic journey.
            
            How to Get Started:
            1. Visit the Student Portal
            2. Log in using your Student ID and Temporary Password
            3. Change your password for security
            4. Complete your student profile
            5. Access your class schedule and materials
            
            We are excited to welcome you to our campus community and look forward to supporting you 
            throughout your academic journey.
            
            Best regards,
            The Admissions Team
            Data Dome University
            
            Need assistance? Contact us at dtdmspprt@gmail.com
        """.trimIndent()
    }

    private fun createRejectionPlainTextContent(): String {
        return """
            APPLICATION STATUS UPDATE - DATA DOME UNIVERSITY
            
            Dear Applicant,
            
            Thank you for your interest in Data Dome University and for taking the time to submit your application.
            
            After careful consideration of all applications received, we regret to inform you that we are unable to offer you admission at this time.
            
            This decision was made after a thorough review process and does not reflect on your abilities or potential.
            
            We appreciate your interest in our institution and wish you every success in your future academic pursuits.
            
            Best regards,
            The Admissions Team
            Data Dome University
        """.trimIndent()
    }

    private fun createHtmlContent(verificationCode: String): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Email Verification - Data Dome App</title>
                <style>
                    /* Reset styles */
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background-color: #FDFBF8;
                        margin: 0;
                        padding: 20px;
                        min-height: 100vh;
                    }
                    
                    .email-container {
                        max-width: 600px;
                        margin: 0 auto;
                        background: #ffffff;
                        border-radius: 12px;
                        overflow: hidden;
                        box-shadow: 0 4px 20px rgba(123, 17, 19, 0.1);
                        border: 1px solid #e8e0d8;
                    }
                    
                    .header {
                        background: linear-gradient(135deg, #7B1113, #9B1C1F);
                        color: white;
                        padding: 40px 30px 30px;
                        text-align: center;
                        position: relative;
                    }
                    
                    .logo-container {
                        margin-bottom: 20px;
                    }
                    
                    .logo-background {
                        width: 120px;
                        height: 120px;
                        margin: 0 auto 15px;
                        border-radius: 50%;
                        background: white;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        border: 3px solid rgba(255, 255, 255, 0.3);
                        box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
                        padding: 10px;
                    }
                    
                    .logo {
                        width: 85px;
                        height: 85px;
                        object-fit: contain;
                        border-radius: 50%;
                    }
                    
                    .logo-text {
                        font-size: 24px;
                        font-weight: bold;
                        letter-spacing: 1px;
                        text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
                    }
                    
                    .header h1 {
                        font-size: 28px;
                        font-weight: 600;
                        margin: 0;
                        letter-spacing: 0.5px;
                    }
                    
                    .header p {
                        opacity: 0.95;
                        margin-top: 10px;
                        font-size: 16px;
                        font-weight: 300;
                    }
                    
                    .content {
                        padding: 40px 30px;
                    }
                    
                    .welcome-text {
                        font-size: 16px;
                        color: #5a4c3d;
                        margin-bottom: 20px;
                        line-height: 1.6;
                        text-align: center;
                    }
                    
                    .verification-section {
                        background: #f8f5f2;
                        border: 2px solid #e8e0d8;
                        border-radius: 12px;
                        padding: 30px 25px;
                        text-align: center;
                        margin: 30px 0;
                    }
                    
                    .verification-label {
                        font-size: 15px;
                        color: #7B1113;
                        margin-bottom: 15px;
                        font-weight: 600;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                    }
                    
                    .verification-code {
                        font-size: 38px;
                        font-weight: bold;
                        color: #7B1113;
                        letter-spacing: 8px;
                        background: white;
                        padding: 20px;
                        border-radius: 8px;
                        border: 3px dashed #9B1C1F;
                        margin: 15px 0;
                        font-family: 'Courier New', monospace;
                        text-shadow: 0 2px 4px rgba(123, 17, 19, 0.1);
                    }
                    
                    .security-note {
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 8px;
                        color: #7B1113;
                        font-weight: 600;
                        margin-top: 15px;
                        font-size: 14px;
                    }
                    
                    .instructions {
                        background: #f9f2f2;
                        border-left: 4px solid #9B1C1F;
                        padding: 20px;
                        margin: 25px 0;
                        border-radius: 0 8px 8px 0;
                    }
                    
                    .instructions h3 {
                        color: #7B1113;
                        margin-bottom: 12px;
                        font-size: 16px;
                        font-weight: 600;
                    }
                    
                    .instructions ol {
                        margin-left: 20px;
                        color: #5a4c3d;
                        line-height: 1.6;
                        font-size: 14px;
                    }
                    
                    .instructions li {
                        margin-bottom: 8px;
                    }
                    
                    .expiry-warning {
                        background: #fdf0f0;
                        border: 1px solid #f5d0d0;
                        border-radius: 8px;
                        padding: 18px;
                        text-align: center;
                        margin: 20px 0;
                        color: #7B1113;
                        font-weight: 500;
                    }
                    
                    .warning-icon {
                        font-weight: bold;
                        margin-right: 6px;
                    }
                    
                    .security-tip {
                        background: #f8f5f2;
                        border-radius: 8px;
                        padding: 18px;
                        text-align: center;
                        margin: 20px 0;
                        border: 1px solid #e8e0d8;
                        font-size: 14px;
                    }
                    
                    .security-tip strong {
                        color: #7B1113;
                    }
                    
                    .footer {
                        background: #f8f5f2;
                        padding: 30px 25px;
                        text-align: center;
                        border-top: 1px solid #e8e0d8;
                    }
                    
                    .contact-info {
                        color: #7B1113;
                        margin-bottom: 15px;
                        line-height: 1.5;
                        font-weight: 500;
                        font-size: 14px;
                    }
                    
                    .support-email {
                        color: #9B1C1F;
                        text-decoration: none;
                        font-weight: 600;
                    }
                    
                    .support-email:hover {
                        text-decoration: underline;
                    }
                    
                    .copyright {
                        color: #8a7b6a;
                        font-size: 12px;
                        margin-top: 20px;
                        line-height: 1.4;
                    }
                    
                    .divider {
                        height: 1px;
                        background: linear-gradient(90deg, transparent, #e8e0d8, transparent);
                        margin: 25px 0;
                    }
                    
                    /* Responsive design */
                    @media (max-width: 480px) {
                        .content {
                            padding: 30px 20px;
                        }
                        
                        .header {
                            padding: 30px 20px 25px;
                        }
                        
                        .verification-code {
                            font-size: 32px;
                            letter-spacing: 6px;
                            padding: 15px;
                        }
                        
                        .header h1 {
                            font-size: 24px;
                        }
                        
                        .logo-background {
                            width: 100px;
                            height: 100px;
                        }
                        
                        .logo {
                            width: 70px;
                            height: 70px;
                        }
                        
                        .logo-text {
                            font-size: 22px;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="email-container">
                    <!-- Header Section -->
                    <div class="header">
                        <div class="logo-container">
                            <!-- Logo with white circular background -->
                            <div class="logo-background">
                                <img src="$LOGO_URL" alt="Data Dome App" class="logo">
                            </div>
                            <div class="logo-text">DATA DOME APP</div>
                        </div>
                        <h1>Email Verification Required</h1>
                        <p>Secure your account with Data Dome App</p>
                    </div>
                    
                    <!-- Main Content -->
                    <div class="content">
                        <p class="welcome-text">
                            Hello,<br><br>
                            Thank you for choosing <strong style="color: #7B1113;">Data Dome App</strong>. 
                            To complete your registration and secure your account, please verify your 
                            email address using the code below:
                        </p>
                        
                        <!-- Verification Code Section -->
                        <div class="verification-section">
                            <div class="verification-label">Your Verification Code</div>
                            <div class="verification-code">$verificationCode</div>
                            <div class="security-note">
                                Secure verification code
                            </div>
                        </div>
                        
                        <!-- Instructions -->
                        <div class="instructions">
                            <h3>How to use this code:</h3>
                            <ol>
                                <li>Return to the Data Dome App</li>
                                <li>Enter the 6-digit verification code shown above</li>
                                <li>Click 'Verify Email' to complete the process</li>
                            </ol>
                        </div>
                        
                        <!-- Expiry Warning -->
                        <div class="expiry-warning">
                            <span class="warning-icon">Important:</span> 
                            This code will expire in <strong>5 minutes</strong> for security reasons.
                        </div>
                        
                        <!-- Security Tip -->
                        <div class="security-tip">
                            <strong>Security Tip:</strong> Never share this code with anyone. 
                            Our team will never ask for your verification code.
                        </div>
                    </div>
                    
                    <div class="divider"></div>
                    
                    <!-- Footer -->
                    <div class="footer">
                        <div class="contact-info">
                            Need assistance? Contact our support team:<br>
                            <a href="mailto:dtdmspprt@gmail.com" class="support-email">dtdmspprt@gmail.com</a>
                        </div>
                        
                        <p style="margin: 20px 0; color: #7B1113; font-weight: 500; font-size: 14px;">
                            If you didn't request this verification, please disregard this email.
                        </p>
                        
                        <div class="copyright">
                            © ${java.time.Year.now().value} Data Dome App. All rights reserved.<br>
                            Protecting your digital privacy and security.
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun createEnrollmentHtmlContent(studentId: String, password: String): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Congratulations! You're Accepted</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background-color: #FDFBF8;
                        margin: 0;
                        padding: 20px;
                        line-height: 1.6;
                    }
                    
                    .email-container {
                        max-width: 600px;
                        margin: 0 auto;
                        background: #ffffff;
                        border-radius: 12px;
                        overflow: hidden;
                        box-shadow: 0 4px 20px rgba(123, 17, 19, 0.1);
                        border: 1px solid #e8e0d8;
                    }
                    
                    .header {
                        background: linear-gradient(135deg, #7B1113, #9B1C1F);
                        color: white;
                        padding: 40px 30px;
                        text-align: center;
                    }
                    
                    .header h1 {
                        font-size: 32px;
                        font-weight: 600;
                        margin-bottom: 10px;
                    }
                    
                    .header p {
                        font-size: 18px;
                        opacity: 0.95;
                    }
                    
                    .content {
                        padding: 40px 30px;
                        color: #5a4c3d;
                    }
                    
                    .welcome-section {
                        margin-bottom: 30px;
                    }
                    
                    .welcome-section p {
                        margin-bottom: 15px;
                        font-size: 16px;
                    }
                    
                    .credentials-box {
                        background: #f8f5f2;
                        border: 2px solid #e8e0d8;
                        border-radius: 8px;
                        padding: 25px;
                        margin: 25px 0;
                    }
                    
                    .credentials-box h3 {
                        color: #7B1113;
                        margin-bottom: 15px;
                        font-size: 20px;
                    }
                    
                    .credential-item {
                        margin: 12px 0;
                        font-size: 16px;
                    }
                    
                    .credential-item strong {
                        color: #7B1113;
                        display: inline-block;
                        width: 150px;
                    }
                    
                    .instructions {
                        background: #f9f2f2;
                        border-left: 4px solid #9B1C1F;
                        padding: 20px;
                        margin: 25px 0;
                        border-radius: 0 8px 8px 0;
                    }
                    
                    .instructions h3 {
                        color: #7B1113;
                        margin-bottom: 12px;
                        font-size: 18px;
                    }
                    
                    .instructions ol {
                        margin-left: 20px;
                    }
                    
                    .instructions li {
                        margin-bottom: 8px;
                    }
                    
                    .next-steps {
                        margin: 25px 0;
                    }
                    
                    .next-steps h3 {
                        color: #7B1113;
                        margin-bottom: 15px;
                        font-size: 18px;
                    }
                    
                    .footer {
                        background: #f8f5f2;
                        padding: 30px;
                        text-align: center;
                        border-top: 1px solid #e8e0d8;
                    }
                    
                    .contact-info {
                        color: #7B1113;
                        margin-bottom: 15px;
                        font-weight: 500;
                    }
                    
                    .copyright {
                        color: #8a7b6a;
                        font-size: 14px;
                        margin-top: 20px;
                    }
                    
                    @media (max-width: 480px) {
                        .content {
                            padding: 30px 20px;
                        }
                        
                        .header {
                            padding: 30px 20px;
                        }
                        
                        .header h1 {
                            font-size: 26px;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="email-container">
                    <!-- Header -->
                    <div class="header">
                        <h1>🎉 Congratulations! 🎉</h1>
                        <p>You Have Been Accepted to Data Dome University</p>
                    </div>
                    
                    <!-- Content -->
                    <div class="content">
                        <div class="welcome-section">
                            <p>Dear Student,</p>
                            
                            <p>We are thrilled to inform you that you have been accepted to <strong style="color: #7B1113;">Data Dome University</strong>!</p>
                            
                            <p>Welcome to our academic community where we are committed to helping you achieve your educational goals and unlock your full potential.</p>
                        </div>
                        
                        <!-- Credentials -->
                        <div class="credentials-box">
                            <h3>Your Student Portal Credentials</h3>
                            <div class="credential-item">
                                <strong>Student ID:</strong> $studentId
                            </div>
                            <div class="credential-item">
                                <strong>Temporary Password:</strong> $password
                            </div>
                        </div>
                        
                        <!-- Instructions -->
                        <div class="instructions">
                            <h3>How to Get Started:</h3>
                            <ol>
                                <li>Visit the Student Portal</li>
                                <li>Log in using your Student ID and Temporary Password</li>
                                <li>Change your password for security</li>
                                <li>Complete your student profile</li>
                                <li>Access your class schedule and materials</li>
                            </ol>
                        </div>
                        
                        <!-- Next Steps -->
                        <div class="next-steps">
                            <h3>Important Next Steps:</h3>
                            <p>• Review your class schedule in the portal</p>
                            <p>• Check important academic dates and deadlines</p>
                            <p>• Familiarize yourself with university policies</p>
                            <p>• Connect with your academic advisor</p>
                        </div>
                        
                        <p>We are excited to welcome you to our campus community and look forward to supporting you throughout your academic journey.</p>
                        
                        <p style="margin-top: 20px;">
                            Best regards,<br>
                            <strong>The Admissions Team</strong><br>
                            Data Dome University
                        </p>
                    </div>
                    
                    <!-- Footer -->
                    <div class="footer">
                        <div class="contact-info">
                            Need assistance? Contact our support team at dtdmspprt@gmail.com
                        </div>
                        <div class="copyright">
                            © ${java.time.Year.now().value} Data Dome University. All rights reserved.<br>
                            Empowering students for a brighter future.
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun createRejectionHtmlContent(): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Application Status Update</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        background-color: #FDFBF8;
                        margin: 0;
                        padding: 20px;
                        line-height: 1.6;
                    }
                    
                    .email-container {
                        max-width: 600px;
                        margin: 0 auto;
                        background: #ffffff;
                        border-radius: 12px;
                        overflow: hidden;
                        box-shadow: 0 4px 20px rgba(123, 17, 19, 0.1);
                        border: 1px solid #e8e0d8;
                    }
                    
                    .header {
                        background: #5a4c3d;
                        color: white;
                        padding: 30px;
                        text-align: center;
                    }
                    
                    .content {
                        padding: 40px 30px;
                        color: #5a4c3d;
                    }
                    
                    .footer {
                        background: #f8f5f2;
                        padding: 20px;
                        text-align: center;
                        border-top: 1px solid #e8e0d8;
                        color: #8a7b6a;
                        font-size: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="email-container">
                    <div class="header">
                        <h1>Application Status Update</h1>
                    </div>
                    
                    <div class="content">
                        <p>Dear Applicant,</p>
                        
                        <p>Thank you for your interest in Data Dome University and for taking the time to submit your application.</p>
                        
                        <p>After careful consideration of all applications received, we regret to inform you that we are unable to offer you admission at this time.</p>
                        
                        <p>This decision was made after a thorough review process and does not reflect on your abilities or potential. The selection process is highly competitive, and we receive many qualified applications each year.</p>
                        
                        <p>We appreciate your interest in our institution and wish you every success in your future academic pursuits and career endeavors.</p>
                        
                        <p style="margin-top: 20px;">
                            Best regards,<br>
                            <strong>The Admissions Team</strong><br>
                            Data Dome University
                        </p>
                    </div>
                    
                    <div class="footer">
                        <p>© ${java.time.Year.now().value} Data Dome University. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}



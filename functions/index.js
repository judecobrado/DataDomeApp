const functions = require("firebase-functions");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

admin.initializeApp();

// Configure your Gmail account (or app password)
const gmailEmail = "dtdmspprt@gmail.com";
const gmailPassword = "fgxx kfqh zspk jicb"; // If 2FA enabled, generate App Password

const transporter = nodemailer.createTransport({
    service: "gmail",
    auth: {
        user: gmailEmail,
        pass: gmailPassword
    }
});

// Cloud Function to send enrollment email
exports.sendEnrollmentEmail = functions.https.onCall(async (data, context) => {
    const { email, studentId, password } = data;

    const mailOptions = {
        from: gmailEmail,
        to: email,
        subject: "Enrollment Approved",
        html: `
            <h3>Congratulations!</h3>
            <p>You have been enrolled successfully.</p>
            <p><b>Student ID:</b> ${studentId}</p>
            <p><b>Login Email:</b> ${email}</p>
            <p><b>Password:</b> ${password}</p>
            <p>Please login and change your password after first login.</p>
        `
    };

    try {
        await transporter.sendMail(mailOptions);
        return { success: true };
    } catch (error) {
        console.error("Error sending email:", error);
        return { success: false, error: error.message };
    }
});

// Cloud Function to send rejection email
exports.sendRejectionEmail = functions.https.onCall(async (data, context) => {
    const { email } = data;

    const mailOptions = {
        from: gmailEmail,
        to: email,
        subject: "Enrollment Result",
        html: `
            <h3>Enrollment Update</h3>
            <p>We regret to inform you that you did not pass the enrollment process.</p>
        `
    };

    try {
        await transporter.sendMail(mailOptions);
        return { success: true };
    } catch (error) {
        console.error("Error sending email:", error);
        return { success: false, error: error.message };
    }
});

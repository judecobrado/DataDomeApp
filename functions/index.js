const express = require("express");
const bodyParser = require("body-parser");
const cors = require("cors");
const { Resend } = require("resend");

const app = express();
const PORT = process.env.PORT || 5000;

// Replace with your Resend API key
const resend = new Resend("re_XXXXXXXXXXXXXXXXXXXX");

app.use(cors());
app.use(bodyParser.json());

// -----------------------------
// Send Enrollment Email
// -----------------------------
app.post("/sendEnrollmentEmail", async (req, res) => {
    try {
        const { email, studentId, password } = req.body;

        await resend.emails.send({
            from: "Your School <no-reply@yourschool.com>",
            to: email,
            subject: "Enrollment Approved",
            html: `
                <h3>Congratulations!</h3>
                <p>You have been enrolled successfully.</p>
                <p><b>Student ID:</b> ${studentId}</p>
                <p><b>Login Email:</b> ${email}</p>
                <p><b>Password:</b> ${password}</p>
                <p>Please login and change your password after first login.</p>
            `,
        });

        res.json({ success: true });
    } catch (error) {
        console.error("Error sending enrollment email:", error);
        res.status(500).json({ success: false, error: error.message });
    }
});

// -----------------------------
// Send Rejection Email
// -----------------------------
app.post("/sendRejectionEmail", async (req, res) => {
    try {
        const { email } = req.body;

        await resend.emails.send({
            from: "Your School <no-reply@yourschool.com>",
            to: email,
            subject: "Enrollment Result",
            html: `
                <h3>Enrollment Update</h3>
                <p>We regret to inform you that you did not pass the enrollment process.</p>
            `,
        });

        res.json({ success: true });
    } catch (error) {
        console.error("Error sending rejection email:", error);
        res.status(500).json({ success: false, error: error.message });
    }
});

app.listen(PORT, () => console.log(`Server running on port ${PORT}`));

const express = require("express");
const bodyParser = require("body-parser");
const cors = require("cors");
const { Resend } = require("resend");

const app = express();
const port = process.env.PORT || 3000;

// Your Resend API key
const resend = new Resend("re_your_api_key_here");

app.use(cors());
app.use(bodyParser.json());

// Enrollment email
app.post("/sendEnrollmentEmail", async (req, res) => {
    const { email, studentId, password } = req.body;

    try {
        await resend.emails.send({
            from: "YourSchool <no-reply@yourschool.com>",
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
        });
        res.json({ success: true });
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false, error: err.message });
    }
});

// Rejection email
app.post("/sendRejectionEmail", async (req, res) => {
    const { email } = req.body;

    try {
        await resend.emails.send({
            from: "YourSchool <no-reply@yourschool.com>",
            to: email,
            subject: "Enrollment Result",
            html: `
                <h3>Enrollment Update</h3>
                <p>We regret to inform you that you did not pass the enrollment process.</p>
            `
        });
        res.json({ success: true });
    } catch (err) {
        console.error(err);
        res.status(500).json({ success: false, error: err.message });
    }
});

app.listen(port, () => {
    console.log(`Email server running on port ${port}`);
});

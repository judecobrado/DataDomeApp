package com.example.datadomeapp.admin

import com.google.firebase.Timestamp

data class Enrollment(
    val id: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val course: String = "",
    val yearLevel: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val guardianRelationship: String = "",
    // Father information
    val fatherFirstName: String = "",
    val fatherMiddleName: String = "",
    val fatherLastName: String = "",
    val fatherDOB: String = "",
    val fatherPhone: String = "",
    val fatherOccupation: String = "",
    // Mother information
    val motherFirstName: String = "",
    val motherMiddleName: String = "",
    val motherLastName: String = "",
    val motherDOB: String = "",
    val motherPhone: String = "",
    val motherOccupation: String = "",
    // Address information
    val country: String = "",
    val region: String = "",
    val province: String = "",
    val municipality: String = "",
    val barangay: String = "",
    val street: String = "",
    val postalCode: String,
    val fullAddress: String = "",
    // Course information
    val courseName: String = "",
    val courseCode: String = "",
    val enrollmentType: String = "",
    val applicationType: String = "",
    val status: String = "pending",
    val timestamp: Timestamp? = null,
    val isVerified: Boolean = false,
    // Additional field for raw data
    val data: Map<String, Any> = emptyMap()
) {
    // Helper function to get data as map for Firestore
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "firstName" to firstName,
            "middleName" to middleName,
            "lastName" to lastName,
            "email" to email,
            "phone" to phone,
            "address" to address,
            "dateOfBirth" to dateOfBirth,
            "gender" to gender,
            "course" to course,
            "yearLevel" to yearLevel,
            "guardianName" to guardianName,
            "guardianPhone" to guardianPhone,
            "guardianRelationship" to guardianRelationship,
            // Father information
            "fatherFirstName" to fatherFirstName,
            "fatherMiddleName" to fatherMiddleName,
            "fatherLastName" to fatherLastName,
            "fatherDOB" to fatherDOB,
            "fatherPhone" to fatherPhone,
            "fatherOccupation" to fatherOccupation,
            // Mother information
            "motherFirstName" to motherFirstName,
            "motherMiddleName" to motherMiddleName,
            "motherLastName" to motherLastName,
            "motherDOB" to motherDOB,
            "motherPhone" to motherPhone,
            "motherOccupation" to motherOccupation,
            // Address information
            "country" to country,
            "region" to region,
            "province" to province,
            "municipality" to municipality,
            "barangay" to barangay,
            "street" to street,
            "postalCode" to postalCode,
            "fullAddress" to fullAddress,
            // Course information
            "courseName" to courseName,
            "courseCode" to courseCode,
            "enrollmentType" to enrollmentType,
            "applicationType" to applicationType,
            "status" to status,
            "timestamp" to (timestamp ?: Timestamp.now()),
            "isVerified" to isVerified
        )
    }

    // Helper function to create Enrollment from Firestore document
    companion object {
        fun fromFirestore(docId: String, data: Map<String, Any>): Enrollment {
            return Enrollment(
                id = docId,
                firstName = data["firstName"] as? String ?: "",
                middleName = data["middleName"] as? String ?: "",
                lastName = data["lastName"] as? String ?: "",
                email = data["email"] as? String ?: "",
                phone = data["phone"] as? String ?: "",
                address = data["address"] as? String ?: "",
                dateOfBirth = data["dateOfBirth"] as? String ?: "",
                gender = data["gender"] as? String ?: "",
                course = data["course"] as? String ?: "",
                yearLevel = data["yearLevel"] as? String ?: "",
                guardianName = data["guardianName"] as? String ?: "",
                guardianPhone = data["guardianPhone"] as? String ?: "",
                guardianRelationship = data["guardianRelationship"] as? String ?: "",
                // Father information
                fatherFirstName = data["fatherFirstName"] as? String ?: "",
                fatherMiddleName = data["fatherMiddleName"] as? String ?: "",
                fatherLastName = data["fatherLastName"] as? String ?: "",
                fatherDOB = data["fatherDOB"] as? String ?: "",
                fatherPhone = data["fatherPhone"] as? String ?: "",
                fatherOccupation = data["fatherOccupation"] as? String ?: "",
                // Mother information
                motherFirstName = data["motherFirstName"] as? String ?: "",
                motherMiddleName = data["motherMiddleName"] as? String ?: "",
                motherLastName = data["motherLastName"] as? String ?: "",
                motherDOB = data["motherDOB"] as? String ?: "",
                motherPhone = data["motherPhone"] as? String ?: "",
                motherOccupation = data["motherOccupation"] as? String ?: "",
                // Address information
                country = data["country"] as? String ?: "",
                region = data["region"] as? String ?: "",
                province = data["province"] as? String ?: "",
                municipality = data["municipality"] as? String ?: "",
                barangay = data["barangay"] as? String ?: "",
                street = data["street"] as? String ?: "",
                postalCode = data["postalCode"] as? String ?: "",
                fullAddress = data["fullAddress"] as? String ?: "",
                // Course information
                courseName = data["courseName"] as? String ?: "",
                courseCode = data["courseCode"] as? String ?: "",
                enrollmentType = data["enrollmentType"] as? String ?: "",
                applicationType = data["applicationType"] as? String ?: "",
                status = data["status"] as? String ?: "pending",
                timestamp = data["timestamp"] as? Timestamp,
                isVerified = data["isVerified"] as? Boolean ?: false,
                data = data
            )
        }
    }
}

data class StudentSubjectss(
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val sectionName: String = "", // e.g., 'ITEP111-A'
    val teacherId: String = "",   // ID ng guro
    val teacherName: String = "", // Pangalan ng guro
    val schedule: String = ""     // e.g., 'MW 8:00AM - 10:00AM'
)
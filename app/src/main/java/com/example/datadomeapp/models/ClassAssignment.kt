package com.example.datadomeapp.models

data class ClassAssignment(
    // Ang '726' na ginagamit bilang Document ID sa loob ng /schedules/
    val assignmentNo: String = "",

    // Academic Context
    val academicYear: String = "",
    val semester: String = "",

    // Subject Details
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val courseCode: String = "",
    val yearLevel: String = "",
    val section: String = "",

    // Teacher and Capacity
    val teacherUid: String = "",
    val teacherName: String = "",
    val maxCapacity: Int = 0,
    val enrolledCount: Int = 0,
    val onlineClassLink: String = "",

    // 🟢 CRITICAL FIX: Schedule Slots as a Map<Slot Number, TimeSlot>
    val scheduleSlots: Map<String, TimeSlot> = emptyMap()
)

data class TimeSlot(
    val day: String = "",       // e.g., "Mon", "Tue"
    val startTime: String = "", // e.g., "1:00 PM" (Display Format)
    val endTime: String = "",   // e.g., "2:00 PM" (Display Format)
    val roomLocation: String = "", // e.g., "Room 201"
    val sectionBlock: String = ""
)

data class Room(
    val id: String = "",       // Hal: "RM-305"
)



data class OLDVerClassAssignment(
    val assignmentId: String = "", // Document ID
    val courseCode: String = "",
    val yearLevel: String = "", // e.g., "1st Year"
    val sectionBlock: String = "", // e.g., "A"
    val sectionName: String = "", // e.g., "BSIT-1-A"
    val subjectCode: String = "",
    val subjectTitle: String = "",
    val teacherUid: String = "",
    val teacherName: String = "",
    val day: String = "", // e.g., "Mon"
    val startTime: String = "", // e.g., "08:00" (HH:mm 24-hour)
    val endTime: String = "", // e.g., "10:00" (HH:mm 24-hour)
    val roomNumber: String = "",
    val maxCapacity: Int = 0,
    val enrolledCount: Int = 0,
    val onlineClassLink: String = ""
)
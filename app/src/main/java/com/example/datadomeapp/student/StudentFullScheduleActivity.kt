package com.example.datadomeapp.student

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.datadomeapp.R
import com.example.datadomeapp.models.ClassAssignment
import com.example.datadomeapp.models.ScheduleItem
import com.example.datadomeapp.models.StudentSubject
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StudentFullScheduleActivity : AppCompatActivity()
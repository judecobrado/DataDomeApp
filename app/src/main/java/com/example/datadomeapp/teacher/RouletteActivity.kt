package com.example.datadomeapp.teacher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R
import kotlin.random.Random

class RouletteActivity : AppCompatActivity() {

    private lateinit var tvWinnerName: TextView
    private lateinit var btnSpinRoulette: Button
    private lateinit var tvRemainingCount: TextView
    private lateinit var tvStudentListDisplay: TextView
    private lateinit var tvRemovedListDisplay: TextView // ✅ NEW: Para sa removed names list
    private lateinit var layoutDecisionButtons: LinearLayout // ✅ NEW: Container ng decision buttons
    private lateinit var btnRemoveWinner: Button // ✅ NEW: Remove button
    private lateinit var btnKeepWinner: Button // ✅ NEW: Keep button

    // Gagamitin natin ito para i-hold ang current winner habang naghihintay ng desisyon
    private var currentWinner: String? = null

    // ✅ CRITICAL: Active list (Mutable)
    private var activeStudentNames: MutableList<String> = mutableListOf()
    // ✅ CRITICAL: Removed list (Mutable)
    private var removedStudentNames: MutableList<String> = mutableListOf()

    private val handler = Handler(Looper.getMainLooper())
    private val spinNames: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_roulette)

        tvWinnerName = findViewById(R.id.tvWinnerName)
        btnSpinRoulette = findViewById(R.id.btnSpinRoulette)
        tvRemainingCount = findViewById(R.id.tvRemainingCount)
        tvStudentListDisplay = findViewById(R.id.tvStudentListDisplay)
        tvRemovedListDisplay = findViewById(R.id.tvRemovedListDisplay) // I-bind
        layoutDecisionButtons = findViewById(R.id.layoutDecisionButtons) // I-bind
        btnRemoveWinner = findViewById(R.id.btnRemoveWinner) // I-bind
        btnKeepWinner = findViewById(R.id.btnKeepWinner) // I-bind


        // 1. Kunin ang data at i-set sa active list
        val initialNames = intent.getStringArrayListExtra("STUDENT_NAMES_LIST") ?: emptyList()
        activeStudentNames.addAll(initialNames)
        val className = intent.getStringExtra("CLASS_NAME")

        title = "Roleta: $className"

        if (activeStudentNames.size < 2) {
            Toast.makeText(this, "Error: Kailangan ng hindi bababa sa 2 estudyante para sa Roleta.", Toast.LENGTH_LONG).show()
            tvWinnerName.text = "Masyadong Kakaunti"
            btnSpinRoulette.isEnabled = false
            updateStudentDisplay()
            return
        }

        prepareSpinNames()
        updateStudentDisplay()

        btnSpinRoulette.setOnClickListener {
            spinRoulette()
        }

        // 2. Set up Decision Button Listeners
        btnRemoveWinner.setOnClickListener {
            handleDecision(remove = true)
        }

        btnKeepWinner.setOnClickListener {
            handleDecision(remove = false)
        }
    }

    private fun prepareSpinNames() {
        spinNames.clear()
        // Punan ang spinNames mula sa active list
        if (activeStudentNames.isNotEmpty()) {
            for (i in 0 until 50) {
                spinNames.add(activeStudentNames.random())
            }
        }
    }

    private fun updateStudentDisplay() {
        // Ipakita ang ACTIVE na pangalan
        val activeListText = if (activeStudentNames.isEmpty()) "Wala na sa listahan." else activeStudentNames.joinToString(separator = "\n") { name ->
            "• $name"
        }
        tvStudentListDisplay.text = activeListText

        // Ipakita ang REMOVED na pangalan
        val removedListText = if (removedStudentNames.isEmpty()) "Wala pang tinanggal." else removedStudentNames.joinToString(separator = "\n") { name ->
            "• $name"
        }
        tvRemovedListDisplay.text = removedListText

        val count = activeStudentNames.size
        tvRemainingCount.text = "Aktibong Estudyante: $count"

        if (count == 0) {
            tvWinnerName.text = "Tapos na ang Roleta!"
            btnSpinRoulette.isEnabled = false
        } else if (count == 1) {
            tvWinnerName.text = "Auto-Winner: ${activeStudentNames.first()}"
            btnSpinRoulette.isEnabled = false
        } else {
            // I-reset ang display para sa susunod na ikot, kung hindi pa umiikot
            if (layoutDecisionButtons.visibility != View.VISIBLE) {
                tvWinnerName.text = "Handa na ba kayo? ($count)"
                btnSpinRoulette.isEnabled = true
            }
        }
    }

    private fun handleDecision(remove: Boolean) {
        val winner = currentWinner ?: return

        if (remove) {
            // Tanggalin sa active list at ilagay sa removed list
            activeStudentNames.remove(winner)
            removedStudentNames.add(winner)
            Toast.makeText(this, "$winner ay tinanggal sa listahan.", Toast.LENGTH_SHORT).show()
        } else {
            // Panatilihin sa active list
            Toast.makeText(this, "$winner ay nanatili sa listahan.", Toast.LENGTH_SHORT).show()
        }

        // Itago ang decision buttons at ibalik ang spin button
        layoutDecisionButtons.visibility = View.GONE
        updateStudentDisplay() // I-update ang listahan at count
    }


    private fun spinRoulette() {
        if (activeStudentNames.isEmpty()) return

        // 1. I-disable ang button at ihanda ang display
        btnSpinRoulette.isEnabled = false
        layoutDecisionButtons.visibility = View.GONE // Siguraduhin na nakatago
        tvWinnerName.text = "UMI-IKOT..."

        // I-regenerate ang spin names
        prepareSpinNames()

        // Piliin na ang mananalo mula sa active list
        val finalWinner = activeStudentNames.random()

        val totalSpinDuration = 3000L
        var currentSpinTime = 0L
        val updateInterval = 50L
        currentWinner = null // I-clear ang previous winner

        // 2. Runnable para sa Spinning Visual Effect
        val spinRunnable = object : Runnable {
            override fun run() {
                if (currentSpinTime < totalSpinDuration) {
                    val randomDisplayIndex = Random.nextInt(spinNames.size)
                    tvWinnerName.text = spinNames[randomDisplayIndex]

                    currentSpinTime += updateInterval
                    handler.postDelayed(this, updateInterval)
                } else {
                    // 3. Itigil ang pag-ikot at ipakita ang nanalo
                    tvWinnerName.text = finalWinner
                    showFinalAnimation(finalWinner)

                    // 4. I-store ang nanalo at ipakita ang decision buttons
                    currentWinner = finalWinner
                    layoutDecisionButtons.visibility = View.VISIBLE

                    // HUWAG I-ENABLE ANG SPIN BUTTON DITO. AABUTIN SA DECISION HANDLER.
                }
            }
        }

        // Simulan ang simulation
        handler.post(spinRunnable)
    }

    private fun showFinalAnimation(winnerName: String) {
        val flashAnimation = AlphaAnimation(0.2f, 1.0f)
        flashAnimation.duration = 600
        flashAnimation.repeatCount = 2
        flashAnimation.repeatMode = Animation.REVERSE

        tvWinnerName.startAnimation(flashAnimation)
        Toast.makeText(this, "Ang Nanalo sa Roleta ay si: $winnerName! Ano ang desisyon?", Toast.LENGTH_LONG).show()
    }
}
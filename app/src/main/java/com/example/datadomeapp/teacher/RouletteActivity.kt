package com.example.datadomeapp.teacher

import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.datadomeapp.R

class RouletteActivity : AppCompatActivity() {

    private lateinit var tvWinnerName: TextView
    private lateinit var btnSpinRoulette: Button
    private lateinit var tvRemainingCount: TextView
    private lateinit var tvStudentListDisplay: TextView
    private lateinit var tvRemovedListDisplay: TextView
    private lateinit var layoutDecisionButtons: LinearLayout
    private lateinit var btnRemoveWinner: Button
    private lateinit var btnKeepWinner: Button
    private lateinit var wheelView: WheelView
    private lateinit var layoutReminder: LinearLayout
    private lateinit var tvWinnerAnnouncement: TextView

    private var currentWinner: String? = null
    private var activeStudentNames: MutableList<String> = mutableListOf()
    private var removedStudentNames: MutableList<String> = mutableListOf()

    private val handler = Handler(Looper.getMainLooper())
    private var wheelAnimator: ValueAnimator? = null

    // === SPECIFIC CHANGES START ===
    private var mediaPlayer: MediaPlayer? = null
    private var isSoundPlaying = false
    // === SPECIFIC CHANGES END ===

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.teacher_roulette)

        initializeViews()
        setupData()
        setupClickListeners()
    }

    private fun initializeViews() {
        tvWinnerName = findViewById(R.id.tvWinnerName)
        btnSpinRoulette = findViewById(R.id.btnSpinRoulette)
        tvRemainingCount = findViewById(R.id.tvRemainingCount)
        tvStudentListDisplay = findViewById(R.id.tvStudentListDisplay)
        tvRemovedListDisplay = findViewById(R.id.tvRemovedListDisplay)
        layoutDecisionButtons = findViewById(R.id.layoutDecisionButtons)
        btnRemoveWinner = findViewById(R.id.btnRemoveWinner)
        btnKeepWinner = findViewById(R.id.btnKeepWinner)
        wheelView = findViewById(R.id.wheelView)
        layoutReminder = findViewById(R.id.layoutReminder)
        tvWinnerAnnouncement = findViewById(R.id.tvWinnerAnnouncement)
    }

    private fun setupData() {
        val initialNames = intent.getStringArrayListExtra("STUDENT_NAMES_LIST") ?: emptyList()
        activeStudentNames.addAll(initialNames)
        val className = intent.getStringExtra("CLASS_NAME")

        title = "Roleta: $className"

        if (activeStudentNames.size < 2) {
            showStatusMessage("Kailangan ng hindi bababa sa 2 estudyante para magsimula")
            btnSpinRoulette.isEnabled = false
        }

        updateWheel()
        updateStudentDisplay()
    }

    private fun setupClickListeners() {
        btnSpinRoulette.setOnClickListener {
            spinRoulette()
        }

        btnRemoveWinner.setOnClickListener {
            handleDecision(remove = true)
        }

        btnKeepWinner.setOnClickListener {
            handleDecision(remove = false)
        }
    }

    private fun spinRoulette() {
        if (activeStudentNames.size < 2) return

        btnSpinRoulette.isEnabled = false
        layoutDecisionButtons.visibility = View.GONE
        tvWinnerAnnouncement.visibility = View.GONE
        tvWinnerName.text = "Spinning..."
        wheelView.setIsSpinning(true)

        val finalWinner = activeStudentNames.random()
        currentWinner = finalWinner

        // Calculate the target slice
        val winnerIndex = activeStudentNames.indexOf(finalWinner)
        val sliceAngle = 360f / activeStudentNames.size
        val targetAngle = -(winnerIndex * sliceAngle + sliceAngle / 2) + 270f

        // === SPECIFIC CHANGES START ===
        // Play spin_the_wheel sound once and let it finish naturally
        if (!isSoundPlaying) {
            mediaPlayer = MediaPlayer.create(this, R.raw.spin_the_wheel)
            mediaPlayer?.setOnCompletionListener {
                isSoundPlaying = false
                mediaPlayer?.release()
                mediaPlayer = null
            }
            mediaPlayer?.start()
            isSoundPlaying = true
        }

        val totalRotations = 15 // VERY FAST rotations
        val totalRotation = totalRotations * 360f + targetAngle

        wheelAnimator = ValueAnimator.ofFloat(0f, totalRotation).apply {
            duration = 9000

            // Use decelerate interpolator - FAST sa simula, SLOW papunta sa dulo
            interpolator = android.view.animation.DecelerateInterpolator(1.2f) // 2f = mas mabilis na slowdown

            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                wheelView.setWheelRotation(value)
            }

            start()
        }
        // === SPECIFIC CHANGES END ===

        // Show winner after animation (KEEP 9000)
        handler.postDelayed({
            showWinner(finalWinner)
            wheelView.setIsSpinning(false)
        }, 9000)
    }

    private fun showWinner(winner: String) {
        // === SPECIFIC CHANGES START ===
        // DON'T stop the sound - let it finish naturally
        // The MediaPlayer completion listener will handle cleanup
        // === SPECIFIC CHANGES END ===

        tvWinnerName.text = "WINNER!"

        // Show animated announcement
        tvWinnerAnnouncement.text = "🎉 $winner 🎉"
        tvWinnerAnnouncement.visibility = View.VISIBLE

        val flashAnimation = AlphaAnimation(0.2f, 1.0f)
        flashAnimation.duration = 800
        flashAnimation.repeatCount = 3
        flashAnimation.repeatMode = Animation.REVERSE

        tvWinnerAnnouncement.startAnimation(flashAnimation)
        showStatusMessage("The Winner: $winner!")

        layoutDecisionButtons.visibility = View.VISIBLE
        btnSpinRoulette.isEnabled = activeStudentNames.size >= 2
    }

    private fun handleDecision(remove: Boolean) {
        val winner = currentWinner ?: return

        if (remove) {
            activeStudentNames.remove(winner)
            removedStudentNames.add(winner)
            showStatusMessage("Tinanggal si: $winner")
        } else {
            showStatusMessage("$winner ay nanatili sa listahan")
        }

        layoutDecisionButtons.visibility = View.GONE
        updateWheel()
        updateStudentDisplay()

        // Auto-enable spin button if enough students
        if (activeStudentNames.size >= 2) {
            btnSpinRoulette.isEnabled = true
        }
    }

    private fun updateWheel() {
        wheelView.setNames(activeStudentNames)
        wheelView.setWheelRotation(0f)
    }

    private fun updateStudentDisplay() {
        val activeListText = if (activeStudentNames.isEmpty()) {
            "The list is empty."
        } else {
            activeStudentNames.joinToString(separator = "\n") { name ->
                "• $name"
            }
        }
        tvStudentListDisplay.text = activeListText

        val removedListText = if (removedStudentNames.isEmpty()) {
            "No one has been removed yet."
        } else {
            removedStudentNames.joinToString(separator = "\n") { name ->
                "• $name"
            }
        }
        tvRemovedListDisplay.text = removedListText

        val count = activeStudentNames.size
        tvRemainingCount.text = "Remaining student: $count"

        // Update reminder visibility
        layoutReminder.visibility = if (count == 1) View.VISIBLE else View.GONE

        // Update header message
        when {
            count == 0 -> {
                tvWinnerName.text = "Tapos na ang Roleta!"
                btnSpinRoulette.isEnabled = false
                showStatusMessage("Lahat ng estudyante ay natanggal na")
            }
            count == 1 -> {
                val autoWinner = activeStudentNames.first()
                tvWinnerName.text = "Auto-Winner: $autoWinner"
                btnSpinRoulette.isEnabled = false
                showStatusMessage("Auto-winner: $autoWinner")
            }
            else -> {
                if (layoutDecisionButtons.visibility != View.VISIBLE) {
                    tvWinnerName.text = "Spin the Wheel!!!"
                    btnSpinRoulette.isEnabled = true
                }
            }
        }
    }

    private fun showStatusMessage(message: String) {
        tvWinnerName.text = message
    }

    override fun onDestroy() {
        super.onDestroy()
        wheelAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
        // === SPECIFIC CHANGES START ===
        mediaPlayer?.release()
        // === SPECIFIC CHANGES END ===
    }
}
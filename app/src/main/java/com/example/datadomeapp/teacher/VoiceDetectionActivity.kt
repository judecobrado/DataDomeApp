package com.example.datadomeapp.teacher

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioFormat
import com.google.firebase.firestore.FirebaseFirestore
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

// --- Default Settings ---
private const val DEFAULT_NOISE_THRESHOLD = 75
private const val PREFS_NAME = "NoiseSettings"
private const val KEY_THRESHOLD = "noise_threshold_db"
private const val CALIBRATION_OFFSET = 50.0

class VoiceDetectionActivity : AppCompatActivity() {

    private lateinit var toggleDetection: com.google.android.material.button.MaterialButton
    private lateinit var tvNoiseStatus: TextView
    private lateinit var tvMusicStatus: TextView
    private lateinit var noiseProgressBar: ProgressBar
    private lateinit var tvThreshold: TextView
    private var btnCalibrate: Button? = null
    private val firestore = FirebaseFirestore.getInstance()
    private var noiseMediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    // Audio Components
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false

    private val RECORDER_SAMPLERATE = 44100
    private val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
    private val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private val PERMISSION_REQUEST_CODE = 101

    private var bufferSize: Int = 0
    private var audioBuffer: ShortArray? = null
    @Volatile private var currentDecibelLevel: Int = 0

    // Noise Monitoring Variables
    private var isDetectionActive = false
    private var currentNoiseThreshold: Int = DEFAULT_NOISE_THRESHOLD

    private val MONITORING_INTERVAL: Long = 500
    private var isMonitoringRunning = false

    // Range for Progress Bar
    private val MIN_DB = 50
    private val MAX_DB = 100

    private var isMusicPlayingOrCooldown = false
    private val MUSIC_COOLDOWN_DELAY: Long = 2000

    private var loudFrames = 0
    private val REQUIRED_CONSECUTIVE_FRAMES = 3

    // --- UI Update Runnable ---
    private val monitorDisplayRunnable: Runnable = object : Runnable {
        override fun run() {
            val dbLevel = currentDecibelLevel
            val progress = calculateBarProgress(dbLevel)

            // Update display text
            tvNoiseStatus.text = "Current Noise: ${dbLevel} dB"

            // Progress bar color logic
            val isThresholdReached = dbLevel >= currentNoiseThreshold
            val colorAlert = if (isThresholdReached)
                ContextCompat.getColor(this@VoiceDetectionActivity, android.R.color.holo_red_dark)
            else
                ContextCompat.getColor(this@VoiceDetectionActivity, android.R.color.black)

            val barColor = if (isThresholdReached)
                ContextCompat.getColor(this@VoiceDetectionActivity, android.R.color.holo_red_dark)
            else
                ContextCompat.getColor(this@VoiceDetectionActivity, android.R.color.holo_green_dark)

            tvNoiseStatus.setTextColor(colorAlert)
            noiseProgressBar.progress = progress
            noiseProgressBar.progressTintList = ColorStateList.valueOf(barColor)

            // Voice-based detection trigger
            if (isDetectionActive) {
                if (isThresholdReached) {
                    loudFrames++
                    if (loudFrames >= REQUIRED_CONSECUTIVE_FRAMES && !isMusicPlayingOrCooldown) {
                        startNoiseMusic()
                        loudFrames = 0
                    }
                } else {
                    loudFrames = 0
                }
            }

            handler.postDelayed(this, MONITORING_INTERVAL)
        }
    }

    // ------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.datadomeapp.R.layout.teacher_voice_detection)

        toggleDetection = findViewById(com.example.datadomeapp.R.id.toggleDetection)
        tvNoiseStatus = findViewById(com.example.datadomeapp.R.id.tvNoiseStatus)
        tvMusicStatus = findViewById(com.example.datadomeapp.R.id.tvMusicStatus)
        noiseProgressBar = findViewById(com.example.datadomeapp.R.id.noiseProgressBar)
        tvThreshold = findViewById(com.example.datadomeapp.R.id.tvThreshold)
        btnCalibrate = findViewById(com.example.datadomeapp.R.id.btnCalibrate)

        btnCalibrate?.setOnClickListener {
            calibrateVoiceThreshold()
        }

        currentNoiseThreshold = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(KEY_THRESHOLD, DEFAULT_NOISE_THRESHOLD)
        tvThreshold.text = "Target: ${currentNoiseThreshold} dB (Voice Level)"

        bufferSize = AudioRecord.getMinBufferSize(
            RECORDER_SAMPLERATE,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING
        ).coerceAtLeast(RECORDER_SAMPLERATE / 2)
        audioBuffer = ShortArray(bufferSize)

        noiseProgressBar.progressTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.holo_green_dark))

        checkAndRequestPermissions()

        toggleDetection.setOnClickListener {
            isDetectionActive = !isDetectionActive
            toggleDetection.isChecked = isDetectionActive
            if (isDetectionActive) {
                tvMusicStatus.text = "Voice Detection: ACTIVE"
                isMusicPlayingOrCooldown = false
            } else {
                tvMusicStatus.text = "Voice Detection: DISABLED"
                stopNoiseMusicImmediate()
            }
        }


        btnCalibrate!!.setOnClickListener {
            calibrateVoiceThreshold()
        }
    }

    // ------------------------------------------------------
    private fun calculateDecibel(buffer: ShortArray, readSize: Int): Int {
        if (readSize <= 0) return 0
        var sumOfSquares = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sumOfSquares += sample * sample
        }
        val rms = sqrt(sumOfSquares / readSize)
        val relativeDb = 20.0 * log10(max(1.0, rms))
        val calibratedDb = relativeDb + CALIBRATION_OFFSET
        return calibratedDb.toInt().coerceIn(50, 120)
    }

    private fun calculateBarProgress(db: Int): Int {
        val clampedDb = db.coerceIn(MIN_DB, MAX_DB)
        val range = (MAX_DB - MIN_DB).toFloat()
        val normalizedValue = (clampedDb - MIN_DB).toFloat()
        return ((normalizedValue / range) * 100).toInt().coerceIn(0, 100)
    }

    private fun isLikelyVoice(buffer: ShortArray, readSize: Int): Boolean {
        var zeroCrossings = 0
        for (i in 1 until readSize) {
            if ((buffer[i - 1] > 0 && buffer[i] < 0) || (buffer[i - 1] < 0 && buffer[i] > 0)) {
                zeroCrossings++
            }
        }
        val zcr = zeroCrossings.toDouble() / readSize
        return zcr in 0.01..0.15
    }

    // ------------------------------------------------------
    private fun startRecording() {
        if (isRecording) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return

        audioRecord?.release()
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLERATE,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING,
            bufferSize
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("VoiceDetection", "AudioRecord init failed.")
            return
        }

        audioRecord?.startRecording()
        isRecording = true
        recordingThread = Thread {
            while (isRecording) {
                val numRead = audioRecord?.read(audioBuffer!!, 0, bufferSize) ?: 0
                if (numRead > 0 && isLikelyVoice(audioBuffer!!, numRead)) {
                    currentDecibelLevel = calculateDecibel(audioBuffer!!, numRead)
                }
            }
        }
        recordingThread?.start()
    }

    private fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread?.interrupt()
        recordingThread = null
        currentDecibelLevel = 0
    }

    // ------------------------------------------------------
    private fun calibrateVoiceThreshold() {
        Toast.makeText(this, "Calibrating... Please speak for 3 seconds.", Toast.LENGTH_SHORT).show()
        var collectedValues = mutableListOf<Int>()
        val endTime = System.currentTimeMillis() + 3000

        Thread {
            while (System.currentTimeMillis() < endTime) {
                collectedValues.add(currentDecibelLevel)
                Thread.sleep(200)
            }
            if (collectedValues.isNotEmpty()) {
                val avgDb = collectedValues.average().toInt()
                saveNewThreshold(avgDb + 5) // add safety buffer
                runOnUiThread {
                    Toast.makeText(this, "Calibrated to ${avgDb + 5} dB", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun saveNewThreshold(newThreshold: Int) {
        currentNoiseThreshold = newThreshold.coerceIn(50, 110)
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putInt(KEY_THRESHOLD, currentNoiseThreshold)
            .apply()
        runOnUiThread {
            tvThreshold.text = "Target: ${currentNoiseThreshold} dB (Voice Level)"
        }
    }

    // ------------------------------------------------------
    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startRecording()
            if (!isMonitoringRunning) {
                handler.post(monitorDisplayRunnable)
                isMonitoringRunning = true
            }
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(monitorDisplayRunnable)
        isMonitoringRunning = false
        stopRecording()
        stopNoiseMusicImmediate()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        stopNoiseMusicImmediate()
        handler.removeCallbacksAndMessages(null)
    }

    // ------------------------------------------------------
    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE)
        }
    }

    // ------------------------------------------------------
    private fun startNoiseMusic() {
        isMusicPlayingOrCooldown = true
        if (noiseMediaPlayer == null) {
            noiseMediaPlayer = MediaPlayer.create(this, com.example.datadomeapp.R.raw.noise_detection)
            noiseMediaPlayer?.setOnCompletionListener {
                it.release()
                noiseMediaPlayer = null
                handler.postDelayed({
                    isMusicPlayingOrCooldown = false
                    if (isDetectionActive) tvMusicStatus.text = "Voice Detection: ACTIVE (Ready)"
                }, MUSIC_COOLDOWN_DELAY)
            }
        }
        noiseMediaPlayer?.start()
        tvMusicStatus.text = "🚨 Alert Triggered!"
    }

    private fun stopNoiseMusicImmediate() {
        noiseMediaPlayer?.stop()
        noiseMediaPlayer?.release()
        noiseMediaPlayer = null
        isMusicPlayingOrCooldown = false
        handler.removeCallbacksAndMessages(null)
        tvMusicStatus.text = if (isDetectionActive)
            "Voice Detection: ACTIVE"
        else
            "Voice Detection: DISABLED"
    }
}

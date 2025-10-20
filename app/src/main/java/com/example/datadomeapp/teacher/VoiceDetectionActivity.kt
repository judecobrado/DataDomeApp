package com.example.datadomeapp.teacher

import android.Manifest
import android.R
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.math.max

// --- Global Settings para sa Teacher Threshold at Calibration ---
private const val DEFAULT_NOISE_THRESHOLD = 90
private const val PREFS_NAME = "NoiseSettings"
private const val KEY_THRESHOLD = "noise_threshold_db"

// Calibration Offset: I-adjust ito kung masyado pa ring mababa/mataas ang dB reading
private const val CALIBRATION_OFFSET = 60.0

class VoiceDetectionActivity : AppCompatActivity() {

    private lateinit var toggleDetection: ToggleButton
    private lateinit var tvNoiseStatus: TextView
    private lateinit var tvMusicStatus: TextView
    private lateinit var noiseProgressBar: ProgressBar
    private lateinit var tvThreshold: TextView

    private var noiseMediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    // --- AudioRecord Components ---
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

    // --- Noise Monitoring Variables ---
    private var isDetectionActive = false
    private var currentNoiseThreshold: Int = DEFAULT_NOISE_THRESHOLD

    private val MONITORING_INTERVAL: Long = 500
    private var isMonitoringRunning = false

    // dB Range for the Progress Bar
    private val MIN_DB = 50
    private val MAX_DB = 100

    private var isMusicPlayingOrCooldown = false
    private val MUSIC_COOLDOWN_DELAY: Long = 2000

    // Runnable para sa UI UPDATE (Main Thread)
    private val monitorDisplayRunnable: Runnable = object : Runnable {
        override fun run() {
            val dbLevel = currentDecibelLevel

            // 1. ALWAYS UPDATE THE DISPLAY (TEXT)
            val statusText = "Current Noise: ${dbLevel} dB"
            tvNoiseStatus.text = statusText

            // 2. UPDATE THE PROGRESS BAR
            val progress = calculateBarProgress(dbLevel)
            noiseProgressBar.progress = progress

            // 3. APPLY COLOR AND CHECK THRESHOLD
            val isThresholdReached = dbLevel >= currentNoiseThreshold

            val colorBlack = ContextCompat.getColor(this@VoiceDetectionActivity, R.color.black)
            val colorAlert = if (isThresholdReached) ContextCompat.getColor(this@VoiceDetectionActivity, R.color.holo_red_dark) else colorBlack
            val barColor = if (isThresholdReached) ContextCompat.getColor(this@VoiceDetectionActivity, R.color.holo_red_dark) else ContextCompat.getColor(this@VoiceDetectionActivity, R.color.holo_green_dark)

            tvNoiseStatus.setTextColor(colorAlert)
            noiseProgressBar.progressTintList = ColorStateList.valueOf(barColor)


            // 4. MUSIC/ALERT SYSTEM (Trigger lang kapag naka-ON ang Toggle Button)
            if (isDetectionActive) {
                if (isThresholdReached && !isMusicPlayingOrCooldown) {
                    startNoiseMusic()
                }
            }

            handler.postDelayed(this, MONITORING_INTERVAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.datadomeapp.R.layout.teacher_voice_detection)

        toggleDetection = findViewById(com.example.datadomeapp.R.id.toggleDetection)
        tvNoiseStatus = findViewById(com.example.datadomeapp.R.id.tvNoiseStatus)
        tvMusicStatus = findViewById(com.example.datadomeapp.R.id.tvMusicStatus)
        noiseProgressBar = findViewById(com.example.datadomeapp.R.id.noiseProgressBar)
        tvThreshold = findViewById(com.example.datadomeapp.R.id.tvThreshold)

        // Kunin ang naka-save na threshold
        currentNoiseThreshold = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(KEY_THRESHOLD, DEFAULT_NOISE_THRESHOLD)
        tvThreshold.text = "Target: ${currentNoiseThreshold} dB (Loud)"


        // Calculate buffer size
        bufferSize = AudioRecord.getMinBufferSize(
            RECORDER_SAMPLERATE,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING
        ).coerceAtLeast(RECORDER_SAMPLERATE / 2)

        audioBuffer = ShortArray(bufferSize)

        // Set initial color
        noiseProgressBar.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.holo_green_dark))

        checkAndRequestPermissions()

        toggleDetection.setOnCheckedChangeListener { _, isChecked ->
            // Ang toggle ay nagko-kontrol lang sa Alert System, hindi sa mikropono
            isDetectionActive = isChecked
            if (isChecked) {
                tvMusicStatus.text = "Music System: ACTIVE"
                isMusicPlayingOrCooldown = false
            } else {
                tvMusicStatus.text = "Music System: DISABLED"
                stopNoiseMusicImmediate()
            }
            // WALANG TAWAG SA startRecording() o stopRecording() DITO!
        }
    }

    // --- RMS Decibel Calculation ---
    private fun calculateDecibel(buffer: ShortArray, readSize: Int): Int {
        if (readSize <= 0) return 0

        var sumOfSquares = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sumOfSquares += sample * sample
        }

        val rms = sqrt(sumOfSquares / readSize)

        val relativeDb = 20.0 * log10(max(1.0, rms))

        // Idagdag ang Calibration Offset
        val calibratedDb = relativeDb + CALIBRATION_OFFSET

        return calibratedDb.toInt().coerceIn(50, 120)
    }

    private fun calculateBarProgress(db: Int): Int {
        val clampedDb = db.coerceIn(MIN_DB, MAX_DB)
        val range = (MAX_DB - MIN_DB).toFloat()
        val normalizedValue = (clampedDb - MIN_DB).toFloat()
        return ((normalizedValue / range) * 100).toInt().coerceIn(0, 100)
    }

    // --- KRITIKAL FIX: Audio Recording Lifecycle (Para sa on/off/on bug) ---
    private fun startRecording() {
        if (isRecording) return

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Safety check: Tiyakin na walang lumang instance
        if (audioRecord != null) {
            audioRecord?.release()
            audioRecord = null
        }
        if (recordingThread != null) {
            recordingThread = null
        }

        // I-initialize ang AudioRecord
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLERATE,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("VoiceDetection", "AudioRecord initialization FAILED! Releasing resource.")
            audioRecord?.release()
            audioRecord = null
            return
        }

        audioRecord?.startRecording()
        isRecording = true

        // Simulan ang Thread
        recordingThread = Thread {
            while (isRecording) {
                val numRead = audioRecord?.read(audioBuffer!!, 0, bufferSize) ?: 0
                if (numRead > 0) {
                    currentDecibelLevel = calculateDecibel(audioBuffer!!, numRead)
                }
            }
            Log.d("VoiceDetection", "Recording thread terminated.")
        }
        recordingThread?.start()
        Log.d("VoiceDetection", "Recording started successfully.")
    }

    private fun stopRecording() {
        isRecording = false

        // 1. I-stop at I-release ang AudioRecord MUNA
        if (audioRecord != null) {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
            audioRecord = null
            Log.d("VoiceDetection", "AudioRecord successfully released.")
        }

        // 2. I-handle ang Thread
        if (recordingThread != null) {
            recordingThread?.interrupt()
            try {
                // Mas mahabang hintay para masigurado
                recordingThread?.join(500)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            recordingThread = null
            Log.d("VoiceDetection", "Recording thread terminated.")
        }

        currentDecibelLevel = 0
        Log.d("VoiceDetection", "Recording fully stopped and resources are FREE.")
    }
    // -------------------------------------------------------------------------

    // --- Teacher Threshold Setter ---
    fun saveNewThreshold(newThreshold: Int) {
        currentNoiseThreshold = newThreshold.coerceIn(50, 110)

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putInt(KEY_THRESHOLD, currentNoiseThreshold)
            .apply()

        tvThreshold.text = "Target: ${currentNoiseThreshold} dB (Loud)"
        Toast.makeText(this, "Threshold set to $currentNoiseThreshold dB", Toast.LENGTH_SHORT).show()
    }
    // --------------------------------

    // --- Activity Lifecycle: Ang Tanging Nagko-kontrol sa Mikropono ---

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // KRITIKAL: Palaging i-start ang recording (Noise Meter)
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
        // KRITIKAL: Palaging i-stop ang recording at i-release ang resources
        stopRecording()
        stopNoiseMusicImmediate()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        stopNoiseMusicImmediate()
        handler.removeCallbacksAndMessages(null)
    }
    // -------------------------------------------------------------------

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission is required to monitor noise.", Toast.LENGTH_LONG).show()
                toggleDetection.isEnabled = false
                tvNoiseStatus.text = "Permission denied."
            }
        }
    }

    // --- Music Logic ---
    private fun startNoiseMusic() {
        isMusicPlayingOrCooldown = true
        // ... (Music creation and playback logic dito) ...
        // Note: Tiyakin na may R.raw.noise_detection file ka
        if (noiseMediaPlayer == null) {
            noiseMediaPlayer = MediaPlayer.create(this, com.example.datadomeapp.R.raw.noise_detection)
            if (noiseMediaPlayer == null) {
                Log.e("VoiceDetection", "Failed to create MediaPlayer. Check R.raw.noise_detection file.")
                isMusicPlayingOrCooldown = false
                return
            }
            noiseMediaPlayer?.setOnCompletionListener { mp ->
                mp.stop()
                mp.release()
                noiseMediaPlayer = null
                tvMusicStatus.text = "Music: Finished. Cooldown..."
                handler.postDelayed({
                    isMusicPlayingOrCooldown = false
                    if (isDetectionActive) {
                        tvMusicStatus.text = "Music System: ACTIVE (Ready)"
                    }
                }, MUSIC_COOLDOWN_DELAY)
            }
        }
        if (noiseMediaPlayer != null && noiseMediaPlayer?.isPlaying == false) {
            noiseMediaPlayer?.start()
            tvMusicStatus.text = "Music: PLAYING! 🚨"
        }
    }

    private fun stopNoiseMusicImmediate() {
        if (noiseMediaPlayer != null) {
            noiseMediaPlayer?.stop()
            noiseMediaPlayer?.release()
            noiseMediaPlayer = null
        }
        isMusicPlayingOrCooldown = false
        handler.removeCallbacksAndMessages(null)

        if (isDetectionActive) {
            tvMusicStatus.text = "Music System: ACTIVE"
        } else {
            tvMusicStatus.text = "Music System: DISABLED"
        }
    }
}
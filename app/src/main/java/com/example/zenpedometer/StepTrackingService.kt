package com.example.zenpedometer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StepTrackingService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var stepCount = 0f
    private var initialStepCount = -1f
    private var isWalking = false
    private var walkingStartTime = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        sharedPreferences = getSharedPreferences("ZenPedometerPrefs", Context.MODE_PRIVATE)
        initialStepCount = sharedPreferences.getFloat("initialStepCount", -1f)
        stepCount = sharedPreferences.getFloat("currentStepCount", 0f)

        createNotificationChannel()
        val notification = createNotification()
        try {
            startForeground(1, notification)
        } catch (e: SecurityException) {
            // Handle case where FOREGROUND_SERVICE_SPECIAL_USE permission is not granted
            stopSelf()
        }

        stepSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                if (initialStepCount == -1f) {
                    initialStepCount = it.values[0]
                    sharedPreferences.edit { putFloat("initialStepCount", initialStepCount) }
                }
                stepCount = it.values[0]
                sharedPreferences.edit { putFloat("currentStepCount", stepCount) }
                saveStepData(stepCount - initialStepCount)

                if (!isWalking) {
                    isWalking = true
                    walkingStartTime = System.currentTimeMillis()
                    sharedPreferences.edit { putLong("walkingStartTime", walkingStartTime) }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No implementation needed
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        if (isWalking) {
            val currentTime = System.currentTimeMillis()
            val totalWalkingTime = sharedPreferences.getLong("totalWalkingTime", 0L) + (currentTime - walkingStartTime)
            sharedPreferences.edit { putLong("totalWalkingTime", totalWalkingTime) }
        }
    }

    private fun saveStepData(steps: Float) {
        val calendar = Calendar.getInstance()
        val dateKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.time)
        val weekKey = SimpleDateFormat("yyyyww", Locale.US).format(calendar.time)
        val monthKey = SimpleDateFormat("yyyyMM", Locale.US).format(calendar.time)
        val yearKey = SimpleDateFormat("yyyy", Locale.US).format(calendar.time)

        sharedPreferences.edit {
            putFloat("steps_daily_$dateKey", steps)
            putFloat("steps_weekly_$weekKey", sharedPreferences.getFloat("steps_weekly_$weekKey", 0f) + steps)
            putFloat("steps_monthly_$monthKey", sharedPreferences.getFloat("steps_monthly_$monthKey", 0f) + steps)
            putFloat("steps_yearly_$yearKey", sharedPreferences.getFloat("steps_yearly_$yearKey", 0f) + steps)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "step_tracking_channel",
                "Step Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for step tracking notifications"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "step_tracking_channel")
            .setContentTitle("ZenPedometer Running")
            .setContentText("Tracking your steps in the background")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
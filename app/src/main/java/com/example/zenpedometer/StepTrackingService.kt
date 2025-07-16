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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StepTrackingService : Service(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var sharedPreferences: SharedPreferences? = null
    private var stepCount = 0f
    private var initialStepCount = -1f
    private var previousStepCount = 0f
    private var isWalking = false
    private var walkingStartTime = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d("StepTrackingService", "onCreate called")
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            sharedPreferences = getSharedPreferences("ZenPedometerPrefs", Context.MODE_PRIVATE)
            initialStepCount = sharedPreferences?.getFloat("initialStepCount", -1f) ?: -1f
            stepCount = sharedPreferences?.getFloat("currentStepCount", 0f) ?: 0f
            previousStepCount = sharedPreferences?.getFloat("previousStepCount", 0f) ?: 0f

            if (stepSensor == null) {
                Log.w("StepTrackingService", "Step counter sensor not available")
                sharedPreferences?.edit { putBoolean("isSensorAvailable", false) }
                stopSelf()
                return
            }

            createNotificationChannel()
            val notification = createNotification()
            try {
                startForeground(1, notification)
                Log.d("StepTrackingService", "Foreground service started successfully")
            } catch (e: Exception) {
                Log.e("StepTrackingService", "Failed to start foreground service: ${e.message}")
                stopSelf()
                return
            }

            stepSensor?.also { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
                Log.d("StepTrackingService", "Sensor listener registered")
            }
        } catch (e: Exception) {
            Log.e("StepTrackingService", "Error in onCreate: ${e.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("StepTrackingService", "onStartCommand called with intent: $intent")
        try {
            if (intent?.getBooleanExtra("RESET_STEPS", false) == true) {
                val calendar = Calendar.getInstance()
                val currentDay = SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.time)
                sharedPreferences?.edit {
                    putFloat("initialStepCount", stepCount)
                    putFloat("previousStepCount", stepCount)
                    putLong("lastResetTime", System.currentTimeMillis())
                    putLong("totalWalkingTime", 0L)
                    putFloat("steps_daily_$currentDay", 0f)
                }
                initialStepCount = stepCount
                isWalking = false
                Log.d("StepTrackingService", "Steps reset")
            }
        } catch (e: Exception) {
            Log.e("StepTrackingService", "Error in onStartCommand: ${e.message}")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        try {
            event?.let {
                if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                    if (initialStepCount == -1f) {
                        initialStepCount = it.values[0]
                        previousStepCount = it.values[0]
                        sharedPreferences?.edit {
                            putFloat("initialStepCount", initialStepCount)
                            putFloat("previousStepCount", previousStepCount)
                        }
                        Log.d("StepTrackingService", "Initial step count set: $initialStepCount")
                    }
                    stepCount = it.values[0]
                    sharedPreferences?.edit { putFloat("currentStepCount", stepCount) }
                    saveStepData(stepCount - initialStepCount)

                    if (!isWalking) {
                        isWalking = true
                        walkingStartTime = System.currentTimeMillis()
                        sharedPreferences?.edit { putLong("walkingStartTime", walkingStartTime) }
                        Log.d("StepTrackingService", "Walking started at $walkingStartTime")
                    } else {
                        val currentTime = System.currentTimeMillis()
                        val totalWalkingTime = sharedPreferences?.getLong("totalWalkingTime", 0L) ?: 0L + (currentTime - walkingStartTime)
                        sharedPreferences?.edit { putLong("totalWalkingTime", totalWalkingTime) }
                        walkingStartTime = currentTime
                        sharedPreferences?.edit { putLong("walkingStartTime", walkingStartTime) }
                        Log.d("StepTrackingService", "Walking time updated: $totalWalkingTime")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("StepTrackingService", "Error in onSensorChanged: ${e.message}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No implementation needed
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            sensorManager?.unregisterListener(this)
            if (isWalking) {
                val currentTime = System.currentTimeMillis()
                val totalWalkingTime = sharedPreferences?.getLong("totalWalkingTime", 0L) ?: 0L + (currentTime - walkingStartTime)
                sharedPreferences?.edit { putLong("totalWalkingTime", totalWalkingTime) }
                Log.d("StepTrackingService", "Service destroyed, total walking time: $totalWalkingTime")
            }
        } catch (e: Exception) {
            Log.e("StepTrackingService", "Error in onDestroy: ${e.message}")
        }
    }

    private fun saveStepData(steps: Float) {
        try {
            val calendar = Calendar.getInstance()
            val dateKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.time)
            val weekKey = SimpleDateFormat("yyyyww", Locale.US).format(calendar.time)
            val monthKey = SimpleDateFormat("yyyyMM", Locale.US).format(calendar.time)
            val yearKey = SimpleDateFormat("yyyy", Locale.US).format(calendar.time)

            val deltaSteps = steps - previousStepCount

            sharedPreferences?.edit {
                putFloat("steps_daily_$dateKey", steps)
                if (deltaSteps > 0) {
                    putFloat("steps_weekly_$weekKey", sharedPreferences?.getFloat("steps_weekly_$weekKey", 0f) ?: 0f + deltaSteps)
                    putFloat("steps_monthly_$monthKey", sharedPreferences?.getFloat("steps_monthly_$monthKey", 0f) ?: 0f + deltaSteps)
                    putFloat("steps_yearly_$yearKey", sharedPreferences?.getFloat("steps_yearly_$yearKey", 0f) ?: 0f + deltaSteps)
                }
                putFloat("previousStepCount", steps)
            }
            previousStepCount = steps
            Log.d("StepTrackingService", "Step data saved: daily=$steps, delta=$deltaSteps")
        } catch (e: Exception) {
            Log.e("StepTrackingService", "Error in saveStepData: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    "step_tracking_channel",
                    "Step Tracking Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Channel for step tracking notifications"
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                Log.d("StepTrackingService", "Notification channel created")
            } catch (e: Exception) {
                Log.e("StepTrackingService", "Error creating notification channel: ${e.message}")
            }
        }
    }

    private fun createNotification(): Notification {
        return try {
            NotificationCompat.Builder(this, "step_tracking_channel")
                .setContentTitle("ZenPedometer Running")
                .setContentText("Tracking your steps in the background")
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
        } catch (e: Exception) {
            Log.e("StepTrackingService", "Error creating notification: ${e.message}")
            NotificationCompat.Builder(this, "step_tracking_channel")
                .setContentTitle("ZenPedometer Running")
                .setContentText("Tracking your steps in the background")
                .setSmallIcon(android.R.drawable.ic_notification_clear_all)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
        }
    }
}
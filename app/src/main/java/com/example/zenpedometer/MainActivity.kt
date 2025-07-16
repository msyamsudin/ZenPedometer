package com.example.zenpedometer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.zenpedometer.ui.theme.ZenPedometerTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private var stepCount = mutableStateOf(0f)
    private var initialStepCount = mutableStateOf(-1f)
    private var stepGoal = mutableStateOf(10000) // Default goal: 10,000 steps
    private var weight = mutableStateOf(70f) // Default weight: 70 kg
    private var height = mutableStateOf(170f) // Default height: 170 cm
    private var lastResetTime = mutableStateOf(0L)
    private var totalWalkingTime = mutableStateOf(0L)
    private var isDarkTheme = mutableStateOf(false) // Theme toggle state
    private var isSensorAvailable = mutableStateOf(true)

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true) {
            checkSensorAvailability {
                startStepTrackingService()
            }
        } else {
            stepCount.value = -1f
            Toast.makeText(this, "Activity recognition permission denied", Toast.LENGTH_LONG).show()
        }
        if (permissions[Manifest.permission.POST_NOTIFICATIONS] != true) {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_LONG).show()
        }
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (isIgnoringBatteryOptimizations()) {
            Toast.makeText(this, "Battery optimization disabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Please disable battery optimization for continuous tracking", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("ZenPedometerPrefs", Context.MODE_PRIVATE)
        val numberFormat = NumberFormat.getNumberInstance(Locale("id", "ID")) // Indonesian locale for 10.000 format

        // Load saved data
        try {
            initialStepCount.value = sharedPreferences.getFloat("initialStepCount", -1f)
            stepCount.value = sharedPreferences.getFloat("currentStepCount", 0f)
            stepGoal.value = sharedPreferences.getInt("stepGoal", 10000)
            weight.value = sharedPreferences.getFloat("weight", 70f)
            height.value = sharedPreferences.getFloat("height", 170f)
            lastResetTime.value = sharedPreferences.getLong("lastResetTime", 0L)
            totalWalkingTime.value = sharedPreferences.getLong("totalWalkingTime", 0L)
            isDarkTheme.value = sharedPreferences.getBoolean("isDarkTheme", false)
            isSensorAvailable.value = sharedPreferences.getBoolean("isSensorAvailable", true)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading SharedPreferences: ${e.message}")
        }

        // Check for daily and periodic resets
        try {
            checkAndResetSteps()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in checkAndResetSteps: ${e.message}")
        }

        // Register SharedPreferences listener to update step count
        sharedPreferences.registerOnSharedPreferenceChangeListener { _, key ->
            try {
                when (key) {
                    "currentStepCount" -> stepCount.value = sharedPreferences.getFloat("currentStepCount", 0f)
                    "initialStepCount" -> initialStepCount.value = sharedPreferences.getFloat("initialStepCount", -1f)
                    "totalWalkingTime" -> totalWalkingTime.value = sharedPreferences.getLong("totalWalkingTime", 0L)
                    "isSensorAvailable" -> isSensorAvailable.value = sharedPreferences.getBoolean("isSensorAvailable", true)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in SharedPreferences listener: ${e.message}")
            }
        }

        // Request permissions
        requestPermissions()
        requestBatteryOptimization()

        setContent {
            ZenPedometerTheme(darkTheme = isDarkTheme.value) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PedometerScreen(
                        stepCount = if (stepCount.value == -1f) -1 else (stepCount.value - initialStepCount.value).toInt(),
                        stepGoal = stepGoal.value,
                        weight = weight.value,
                        height = height.value,
                        isSensorAvailable = isSensorAvailable.value,
                        onStepGoalChange = { newGoal ->
                            if (newGoal in 1000..50000) {
                                stepGoal.value = newGoal
                                sharedPreferences.edit { putInt("stepGoal", newGoal) }
                                Toast.makeText(this, "Step goal updated to ${numberFormat.format(newGoal)}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Step goal must be between 1.000 and 50.000", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onWeightChange = { newWeight ->
                            if (newWeight in 30f..150f) {
                                weight.value = newWeight
                                sharedPreferences.edit { putFloat("weight", newWeight) }
                                Toast.makeText(this, "Weight updated to ${newWeight.toInt()} kg", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Weight must be between 30 and 150 kg", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onHeightChange = { newHeight ->
                            if (newHeight in 100f..250f) {
                                height.value = newHeight
                                sharedPreferences.edit { putFloat("height", newHeight) }
                                Toast.makeText(this, "Height updated to ${newHeight.toInt()} cm", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Height must be between 100 and 250 cm", Toast.LENGTH_SHORT).show()
                            }
                        },
                        dailySteps = getStepsForPeriod("daily"),
                        weeklySteps = getStepsForPeriod("weekly"),
                        monthlySteps = getStepsForPeriod("monthly"),
                        yearlySteps = getStepsForPeriod("yearly"),
                        walkingDuration = totalWalkingTime.value,
                        isDarkTheme = isDarkTheme.value,
                        onThemeToggle = { isDark ->
                            isDarkTheme.value = isDark
                            sharedPreferences.edit { putBoolean("isDarkTheme", isDark) }
                            Toast.makeText(this, "Theme switched to ${if (isDark) "Dark" else "Light"}", Toast.LENGTH_SHORT).show()
                        },
                        onResetSteps = {
                            try {
                                resetSteps()
                                Toast.makeText(this, "Steps reset successfully", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error resetting steps: ${e.message}")
                                Toast.makeText(this, "Failed to reset steps", Toast.LENGTH_SHORT).show()
                            }
                        },
                        numberFormat = numberFormat,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkSensorAvailability(onSensorAvailable: () -> Unit) {
        try {
            val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            isSensorAvailable.value = stepSensor != null
            sharedPreferences.edit { putBoolean("isSensorAvailable", isSensorAvailable.value) }
            if (!isSensorAvailable.value) {
                Toast.makeText(this, "Step counter sensor not available on this device", Toast.LENGTH_LONG).show()
                Log.w("MainActivity", "Step counter sensor not available")
            } else {
                onSensorAvailable()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking sensor availability: ${e.message}")
            isSensorAvailable.value = false
            sharedPreferences.edit { putBoolean("isSensorAvailable", false) }
            Toast.makeText(this, "Error checking sensor availability", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        try {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        } catch (e: Exception) {
            Log.e("MainActivity", "Error requesting permissions: ${e.message}")
            Toast.makeText(this, "Failed to request permissions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                batteryOptimizationLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error requesting battery optimization: ${e.message}")
                Toast.makeText(this, "Failed to request battery optimization", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking battery optimization: ${e.message}")
            false
        }
    }

    private fun startStepTrackingService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED && isSensorAvailable.value) {
            try {
                val intent = Intent(this, StepTrackingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Log.d("MainActivity", "StepTrackingService started successfully")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting StepTrackingService: ${e.message}")
                Toast.makeText(this, "Failed to start step tracking service", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w("MainActivity", "Not starting StepTrackingService: Permission denied or sensor unavailable")
        }
    }

    private fun checkAndResetSteps() {
        val calendar = Calendar.getInstance()
        val currentDay = SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.time)
        val currentWeek = SimpleDateFormat("yyyyww", Locale.US).format(calendar.time)
        val currentMonth = SimpleDateFormat("yyyyMM", Locale.US).format(calendar.time)
        val currentYear = SimpleDateFormat("yyyy", Locale.US).format(calendar.time)
        val lastResetDay = SimpleDateFormat("yyyyMMdd", Locale.US).format(lastResetTime.value)
        val lastResetWeek = sharedPreferences.getString("lastResetWeek", "") ?: ""
        val lastResetMonth = sharedPreferences.getString("lastResetMonth", "") ?: ""
        val lastResetYear = sharedPreferences.getString("lastResetYear", "") ?: ""

        try {
            if (lastResetDay != currentDay) {
                initialStepCount.value = stepCount.value
                totalWalkingTime.value = 0L
                sharedPreferences.edit {
                    putFloat("initialStepCount", initialStepCount.value)
                    putLong("lastResetTime", calendar.timeInMillis)
                    putLong("totalWalkingTime", 0L)
                    putFloat("steps_daily_$currentDay", 0f)
                }
            }

            if (lastResetWeek != currentWeek) {
                sharedPreferences.edit {
                    putFloat("steps_weekly_$currentWeek", 0f)
                    putString("lastResetWeek", currentWeek)
                }
            }

            if (lastResetMonth != currentMonth) {
                sharedPreferences.edit {
                    putFloat("steps_monthly_$currentMonth", 0f)
                    putString("lastResetMonth", currentMonth)
                }
            }

            if (lastResetYear != currentYear) {
                sharedPreferences.edit {
                    putFloat("steps_yearly_$currentYear", 0f)
                    putString("lastResetYear", currentYear)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in checkAndResetSteps: ${e.message}")
        }
    }

    private fun resetSteps() {
        try {
            initialStepCount.value = stepCount.value
            totalWalkingTime.value = 0L
            sharedPreferences.edit {
                putFloat("initialStepCount", initialStepCount.value)
                putLong("lastResetTime", System.currentTimeMillis())
                putLong("totalWalkingTime", 0L)
            }
            if (isSensorAvailable.value) {
                val intent = Intent(this, StepTrackingService::class.java).apply {
                    putExtra("RESET_STEPS", true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error resetting steps: ${e.message}")
            Toast.makeText(this, "Failed to reset steps", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getStepsForPeriod(period: String): Int {
        val calendar = Calendar.getInstance()
        val key = when (period) {
            "daily" -> "steps_daily_${SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.time)}"
            "weekly" -> "steps_weekly_${SimpleDateFormat("yyyyww", Locale.US).format(calendar.time)}"
            "monthly" -> "steps_monthly_${SimpleDateFormat("yyyyMM", Locale.US).format(calendar.time)}"
            "yearly" -> "steps_yearly_${SimpleDateFormat("yyyy", Locale.US).format(calendar.time)}"
            else -> ""
        }
        return try {
            sharedPreferences.getFloat(key, 0f).toInt()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting steps for $period: ${e.message}")
            0
        }
    }
}

@Composable
fun PedometerScreen(
    stepCount: Int,
    stepGoal: Int,
    weight: Float,
    height: Float,
    isSensorAvailable: Boolean,
    onStepGoalChange: (Int) -> Unit,
    onWeightChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    dailySteps: Int,
    weeklySteps: Int,
    monthlySteps: Int,
    yearlySteps: Int,
    walkingDuration: Long,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onResetSteps: () -> Unit,
    numberFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Dashboard", "Settings")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Theme Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text("Dark Mode", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Switch(
                checked = isDarkTheme,
                onCheckedChange = onThemeToggle
            )
        }

        // Tab Navigation with Icons
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    text = { Text(title) },
                    icon = {
                        when (index) {
                            0 -> Icon(Icons.Filled.Dashboard, contentDescription = "Dashboard")
                            1 -> Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            else -> null
                        }
                    },
                    selected = selectedTab == index,
                    onClick = { selectedTab = index }
                )
            }
        }

        // Tab Content
        AnimatedVisibility(visible = selectedTab == 0) {
            DashboardTab(
                stepCount = stepCount,
                stepGoal = stepGoal,
                weight = weight,
                height = height,
                isSensorAvailable = isSensorAvailable,
                dailySteps = dailySteps,
                weeklySteps = weeklySteps,
                monthlySteps = monthlySteps,
                yearlySteps = yearlySteps,
                walkingDuration = walkingDuration,
                isDarkTheme = isDarkTheme,
                numberFormat = numberFormat
            )
        }
        AnimatedVisibility(visible = selectedTab == 1) {
            SettingsTab(
                stepGoal = stepGoal,
                weight = weight,
                height = height,
                onStepGoalChange = onStepGoalChange,
                onWeightChange = onWeightChange,
                onHeightChange = onHeightChange,
                onResetSteps = onResetSteps,
                numberFormat = numberFormat
            )
        }
    }
}

@Composable
fun DashboardTab(
    stepCount: Int,
    stepGoal: Int,
    weight: Float,
    height: Float,
    isSensorAvailable: Boolean,
    dailySteps: Int,
    weeklySteps: Int,
    monthlySteps: Int,
    yearlySteps: Int,
    walkingDuration: Long,
    isDarkTheme: Boolean,
    numberFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val stepLength = height * 0.415f / 100 // Convert cm to meters, then apply step length factor
    val distanceKm = if (isSensorAvailable) stepCount * stepLength / 1000 else 0f
    val calories = if (isSensorAvailable) distanceKm * weight * 0.57f else 0f // MET factor for walking
    val durationMinutes = if (isSensorAvailable) TimeUnit.MILLISECONDS.toMinutes(walkingDuration) else 0L
    val progressColor = if (isDarkTheme) Color(0xFF4CAF50) else Color(0xFF1976D2) // Green for dark, Blue for light

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isSensorAvailable) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "Step counter sensor not available on this device.",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSensorAvailable) {
                        "Steps: ${numberFormat.format(stepCount)} / ${numberFormat.format(stepGoal)}"
                    } else {
                        "Steps: N/A / ${numberFormat.format(stepGoal)}"
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                CircularProgressIndicator(
                    progress = if (isSensorAvailable) minOf(stepCount.toFloat() / stepGoal, 1f) else 0f,
                    modifier = Modifier.size(140.dp),
                    strokeWidth = 14.dp,
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surface,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = if (isSensorAvailable) {
                        "Progress: ${String.format("%.1f", (stepCount.toFloat() / stepGoal * 100))}%"
                    } else {
                        "Progress: N/A"
                    },
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Metrics",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isSensorAvailable) {
                        "Distance: ${String.format("%.2f", distanceKm)} km"
                    } else {
                        "Distance: N/A"
                    },
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isSensorAvailable) {
                        "Calories: ${String.format("%.2f", calories)} kcal"
                    } else {
                        "Calories: N/A"
                    },
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isSensorAvailable) {
                        "Walking Time: ${numberFormat.format(durationMinutes)} min"
                    } else {
                        "Walking Time: N/A"
                    },
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Step History",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isSensorAvailable) {
                        "Today: ${numberFormat.format(dailySteps)} steps"
                    } else {
                        "Today: N/A"
                    },
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isSensorAvailable) {
                        "This Week: ${numberFormat.format(weeklySteps)} steps"
                    } else {
                        "This Week: N/A"
                    },
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isSensorAvailable) {
                        "This Month: ${numberFormat.format(monthlySteps)} steps"
                    } else {
                        "This Month: N/A"
                    },
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isSensorAvailable) {
                        "This Year: ${numberFormat.format(yearlySteps)} steps"
                    } else {
                        "This Year: N/A"
                    },
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsTab(
    stepGoal: Int,
    weight: Float,
    height: Float,
    onStepGoalChange: (Int) -> Unit,
    onWeightChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    onResetSteps: () -> Unit,
    numberFormat: NumberFormat
) {
    var goalInput by remember { mutableStateOf(numberFormat.format(stepGoal)) }
    var weightValue by remember { mutableFloatStateOf(weight) }
    var heightValue by remember { mutableFloatStateOf(height) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineMedium
        )

        // Step Goal
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = goalInput,
                onValueChange = { input ->
                    goalInput = input.replace(".", "") // Remove dots for user input
                },
                label = { Text("Daily Step Goal (1.000-50.000)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val newGoal = goalInput.replace(".", "").toIntOrNull()
                    if (newGoal != null && newGoal in 1000..50000) {
                        onStepGoalChange(newGoal)
                        goalInput = numberFormat.format(newGoal) // Update with formatted value
                    } else {
                        errorMessage = "Step goal must be between 1.000 and 50.000"
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Set")
            }
        }

        // Weight Slider
        Text("Weight: ${weightValue.toInt()} kg", fontSize = 18.sp)
        Slider(
            value = weightValue,
            onValueChange = { weightValue = it },
            valueRange = 30f..150f,
            onValueChangeFinished = {
                onWeightChange(weightValue)
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Height Slider
        Text("Height: ${heightValue.toInt()} cm", fontSize = 18.sp)
        Slider(
            value = heightValue,
            onValueChange = { heightValue = it },
            valueRange = 100f..250f,
            onValueChangeFinished = {
                onHeightChange(heightValue)
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Reset Steps Button
        Button(
            onClick = onResetSteps,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Steps")
        }

        // Error Message
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PedometerScreenPreview() {
    val numberFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))
    ZenPedometerTheme(darkTheme = false) {
        PedometerScreen(
            stepCount = 1234,
            stepGoal = 10000,
            weight = 70f,
            height = 170f,
            isSensorAvailable = true,
            onStepGoalChange = {},
            onWeightChange = {},
            onHeightChange = {},
            dailySteps = 1234,
            weeklySteps = 5678,
            monthlySteps = 25000,
            yearlySteps = 300000,
            walkingDuration = 3600000L, // 1 hour
            isDarkTheme = false,
            onThemeToggle = {},
            onResetSteps = {},
            numberFormat = numberFormat
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PedometerScreenNoSensorPreview() {
    val numberFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))
    ZenPedometerTheme(darkTheme = false) {
        PedometerScreen(
            stepCount = -1,
            stepGoal = 10000,
            weight = 70f,
            height = 170f,
            isSensorAvailable = false,
            onStepGoalChange = {},
            onWeightChange = {},
            onHeightChange = {},
            dailySteps = 0,
            weeklySteps = 0,
            monthlySteps = 0,
            yearlySteps = 0,
            walkingDuration = 0L,
            isDarkTheme = false,
            onThemeToggle = {},
            onResetSteps = {},
            numberFormat = numberFormat
        )
    }
}
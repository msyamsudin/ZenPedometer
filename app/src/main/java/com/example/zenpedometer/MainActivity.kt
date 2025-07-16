package com.example.zenpedometer

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
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
import androidx.core.content.edit
import com.example.zenpedometer.ui.theme.ZenPedometerTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var stepCount = mutableStateOf(0f)
    private var initialStepCount = -1f
    private lateinit var sharedPreferences: SharedPreferences
    private var stepGoal = mutableStateOf(10000) // Default goal: 10,000 steps
    private var weight = mutableStateOf(70f) // Default weight: 70 kg
    private var height = mutableStateOf(170f) // Default height: 170 cm
    private var lastResetTime = mutableStateOf(0L)
    private var walkingStartTime = mutableStateOf(0L)
    private var totalWalkingTime = mutableStateOf(0L)
    private var isWalking = mutableStateOf(false)
    private var isDarkTheme = mutableStateOf(false) // Theme toggle state

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            stepSensor?.also { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        } else {
            stepCount.value = -1f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        sharedPreferences = getSharedPreferences("ZenPedometerPrefs", Context.MODE_PRIVATE)

        // Load saved data
        initialStepCount = sharedPreferences.getFloat("initialStepCount", -1f)
        stepGoal.value = sharedPreferences.getInt("stepGoal", 10000)
        weight.value = sharedPreferences.getFloat("weight", 70f)
        height.value = sharedPreferences.getFloat("height", 170f)
        lastResetTime.value = sharedPreferences.getLong("lastResetTime", 0L)
        totalWalkingTime.value = sharedPreferences.getLong("totalWalkingTime", 0L)
        isDarkTheme.value = sharedPreferences.getBoolean("isDarkTheme", false) // Load theme preference

        // Check for daily reset
        checkAndResetDailySteps()

        setContent {
            ZenPedometerTheme(darkTheme = isDarkTheme.value) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PedometerScreen(
                        stepCount = if (stepCount.value == -1f) -1 else (stepCount.value - initialStepCount).toInt(),
                        stepGoal = stepGoal.value,
                        weight = weight.value,
                        height = height.value,
                        onStepGoalChange = { newGoal ->
                            if (newGoal in 1000..50000) {
                                stepGoal.value = newGoal
                                sharedPreferences.edit { putInt("stepGoal", newGoal) }
                            }
                        },
                        onWeightChange = { newWeight ->
                            if (newWeight in 30f..150f) {
                                weight.value = newWeight
                                sharedPreferences.edit { putFloat("weight", newWeight) }
                            }
                        },
                        onHeightChange = { newHeight ->
                            if (newHeight in 100f..250f) {
                                height.value = newHeight
                                sharedPreferences.edit { putFloat("height", newHeight) }
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
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            stepSensor?.also { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || stepCount.value != -1f) {
            stepSensor?.also { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        // Save total walking time when pausing
        if (isWalking.value) {
            val currentTime = System.currentTimeMillis()
            totalWalkingTime.value += currentTime - walkingStartTime.value
            sharedPreferences.edit { putLong("totalWalkingTime", totalWalkingTime.value) }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                if (initialStepCount == -1f) {
                    initialStepCount = it.values[0]
                    sharedPreferences.edit { putFloat("initialStepCount", initialStepCount) }
                }
                stepCount.value = it.values[0]
                saveStepData(stepCount.value - initialStepCount)

                // Track walking duration
                if (!isWalking.value) {
                    isWalking.value = true
                    walkingStartTime.value = System.currentTimeMillis()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No implementation needed
    }

    private fun checkAndResetDailySteps() {
        val calendar = Calendar.getInstance()
        val currentDay = SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.time)
        val lastResetDay = SimpleDateFormat("yyyyMMdd", Locale.US).format(lastResetTime.value)

        if (lastResetDay != currentDay) {
            // Reset daily steps
            initialStepCount = stepCount.value
            totalWalkingTime.value = 0L
            sharedPreferences.edit {
                putFloat("initialStepCount", initialStepCount)
                putLong("lastResetTime", calendar.timeInMillis)
                putLong("totalWalkingTime", 0L)
            }
            saveStepData(0f) // Reset daily steps
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

    private fun getStepsForPeriod(period: String): Int {
        val calendar = Calendar.getInstance()
        val key = when (period) {
            "daily" -> "steps_daily_${SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.time)}"
            "weekly" -> "steps_weekly_${SimpleDateFormat("yyyyww", Locale.US).format(calendar.time)}"
            "monthly" -> "steps_monthly_${SimpleDateFormat("yyyyMM", Locale.US).format(calendar.time)}"
            "yearly" -> "steps_yearly_${SimpleDateFormat("yyyy", Locale.US).format(calendar.time)}"
            else -> ""
        }
        return sharedPreferences.getFloat(key, 0f).toInt()
    }
}

@Composable
fun PedometerScreen(
    stepCount: Int,
    stepGoal: Int,
    weight: Float,
    height: Float,
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
        if (stepCount == -1) {
            Text(
                text = "Permission denied. Please grant activity recognition permission.",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        } else {
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
                    dailySteps = dailySteps,
                    weeklySteps = weeklySteps,
                    monthlySteps = monthlySteps,
                    yearlySteps = yearlySteps,
                    walkingDuration = walkingDuration,
                    isDarkTheme = isDarkTheme
                )
            }
            AnimatedVisibility(visible = selectedTab == 1) {
                SettingsTab(
                    stepGoal = stepGoal,
                    weight = weight,
                    height = height,
                    onStepGoalChange = onStepGoalChange,
                    onWeightChange = onWeightChange,
                    onHeightChange = onHeightChange
                )
            }
        }
    }
}

@Composable
fun DashboardTab(
    stepCount: Int,
    stepGoal: Int,
    weight: Float,
    height: Float,
    dailySteps: Int,
    weeklySteps: Int,
    monthlySteps: Int,
    yearlySteps: Int,
    walkingDuration: Long,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val stepLength = height * 0.415f / 100 // Convert cm to meters, then apply step length factor
    val distanceKm = stepCount * stepLength / 1000
    val calories = distanceKm * weight * 0.57f // MET factor for walking
    val durationMinutes = TimeUnit.MILLISECONDS.toMinutes(walkingDuration)
    val progressColor = if (isDarkTheme) Color(0xFF4CAF50) else Color(0xFF1976D2) // Green for dark, Blue for light

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                    text = "Steps: $stepCount / $stepGoal",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Modern Progress Indicator with Theme Adaptation
                CircularProgressIndicator(
                    progress = { minOf(stepCount.toFloat() / stepGoal, 1f) },
                    modifier = Modifier.size(140.dp),
                    strokeWidth = 14.dp,
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surface,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = "Progress: ${String.format("%.1f", (stepCount.toFloat() / stepGoal * 100))}%",
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
                    text = "Distance: ${String.format("%.2f", distanceKm)} km",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Calories: ${String.format("%.2f", calories)} kcal",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Walking Time: ${String.format("%d", durationMinutes)} min",
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
                    text = "Today: $dailySteps steps",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "This Week: $weeklySteps steps",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "This Month: $monthlySteps steps",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "This Year: $yearlySteps steps",
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
    onHeightChange: (Float) -> Unit
) {
    var goalInput by remember { mutableStateOf(stepGoal.toString()) }
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
                onValueChange = { goalInput = it },
                label = { Text("Daily Step Goal (1000-50000)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val newGoal = goalInput.toIntOrNull()
                    if (newGoal != null && newGoal in 1000..50000) {
                        onStepGoalChange(newGoal)
                        errorMessage = ""
                    } else {
                        errorMessage = "Step goal must be between 1000 and 50000"
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
                if (weightValue in 30f..150f) {
                    onWeightChange(weightValue)
                    errorMessage = ""
                } else {
                    errorMessage = "Weight must be between 30 and 150 kg"
                }
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
                if (heightValue in 100f..250f) {
                    onHeightChange(heightValue)
                    errorMessage = ""
                } else {
                    errorMessage = "Height must be between 100 and 250 cm"
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

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
    ZenPedometerTheme(darkTheme = false) {
        PedometerScreen(
            stepCount = 1234,
            stepGoal = 10000,
            weight = 70f,
            height = 170f,
            onStepGoalChange = {},
            onWeightChange = {},
            onHeightChange = {},
            dailySteps = 1234,
            weeklySteps = 5678,
            monthlySteps = 25000,
            yearlySteps = 300000,
            walkingDuration = 3600000L, // 1 hour
            isDarkTheme = false,
            onThemeToggle = {}
        )
    }
}
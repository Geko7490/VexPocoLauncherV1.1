package com.vexpoco.launcher

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.using
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Background = Color(0xFF101114)
private val SurfaceDark = Color(0xFF1A1C20)
private val Accent = Color(0xFFB8F36B)
private val PrimaryText = Color(0xFFF4F4F0)
private val SecondaryText = Color(0xFF9A9DA4)

private data class LauncherApp(
    val label: String,
    val packageName: String,
    val className: String,
    val icon: Bitmap
)

class MainActivity : ComponentActivity() {
    private val appsState = mutableStateOf<List<LauncherApp>>(emptyList())
    private val screenState = mutableStateOf("home")
    private val refreshState = mutableStateOf(120)
    private val animationState = mutableStateOf("VEX Zoom")
    private val speedState = mutableStateOf(220)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 17, 20)
        window.navigationBarColor = android.graphics.Color.rgb(16, 17, 20)
        refreshState.value = getPreferences(MODE_PRIVATE).getInt("refresh_rate", 120)
        animationState.value = getPreferences(MODE_PRIVATE).getString("launch_animation", "VEX Zoom") ?: "VEX Zoom"
        speedState.value = getPreferences(MODE_PRIVATE).getInt("animation_speed", 220)
        applyRefreshPreference(refreshState.value)
        loadApps()

        setContent {
            MaterialTheme {
                VexLauncherApp(
                    apps = appsState.value,
                    screen = screenState.value,
                    refreshRate = refreshState.value,
                    animation = animationState.value,
                    animationSpeed = speedState.value,
                    onOpenDrawer = { screenState.value = "drawer" },
                    onHome = { screenState.value = "home" },
                    onSettings = { screenState.value = "settings" },
                    onRefreshChange = { rate ->
                        refreshState.value = rate
                        getPreferences(MODE_PRIVATE).edit().putInt("refresh_rate", rate).apply()
                        applyRefreshPreference(rate)
                    },
                    onAnimationChange = { value ->
                        animationState.value = value
                        getPreferences(MODE_PRIVATE).edit().putString("launch_animation", value).apply()
                    },
                    onSpeedChange = { value ->
                        speedState.value = value
                        getPreferences(MODE_PRIVATE).edit().putInt("animation_speed", value).apply()
                    },
                    onLaunch = ::launchApp
                )
            }
        }
    }

    private fun applyRefreshPreference(requested: Int) {
        if (android.os.Build.VERSION.SDK_INT < 23) return
        val modes = window.windowManager.defaultDisplay.supportedModes
        val chosen = modes.minByOrNull { kotlin.math.abs(it.refreshRate - requested) } ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = chosen.modeId
        }
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
                    .distinctBy { it.activityInfo.packageName }
                    .mapNotNull { info ->
                        runCatching {
                            LauncherApp(
                                label = info.loadLabel(packageManager).toString(),
                                packageName = info.activityInfo.packageName,
                                className = info.activityInfo.name,
                                icon = info.loadIcon(packageManager).toBitmap()
                            )
                        }.getOrNull()
                    }
                    .sortedBy { it.label.lowercase() }
            }
            appsState.value = loaded
        }
    }

    private fun launchApp(app: LauncherApp) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.className)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val durationAnimations = when (speedState.value) {
            140 -> R.anim.vex_app_enter_140 to R.anim.vex_launcher_exit_140
            180 -> R.anim.vex_app_enter_180 to R.anim.vex_launcher_exit_180
            280 -> R.anim.vex_app_enter_280 to R.anim.vex_launcher_exit_280
            else -> R.anim.vex_app_enter to R.anim.vex_launcher_exit
        }
        val options = when (animationState.value) {
            "System Default" -> null
            "Fade" -> ActivityOptions.makeCustomAnimation(this, R.anim.vex_fade_enter, R.anim.vex_fade_exit)
            "Quick Scale" -> ActivityOptions.makeCustomAnimation(this, R.anim.vex_quick_enter, R.anim.vex_quick_exit)
            "VEX Zoom" -> ActivityOptions.makeCustomAnimation(this, durationAnimations.first, durationAnimations.second)
            else -> null
        }
        runCatching {
            if (options == null) startActivity(intent) else startActivity(intent, options.toBundle())
        }
    }
}

private fun Drawable.toBitmap(): Bitmap {
    val width = intrinsicWidth.coerceAtLeast(1).coerceAtMost(192)
    val height = intrinsicHeight.coerceAtLeast(1).coerceAtMost(192)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap
}

@Composable
private fun VexLauncherApp(
    apps: List<LauncherApp>,
    screen: String,
    refreshRate: Int,
    animation: String,
    animationSpeed: Int,
    onOpenDrawer: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onRefreshChange: (Int) -> Unit,
    onAnimationChange: (String) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onLaunch: (LauncherApp) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(tween(130)) + scaleIn(initialScale = 0.985f, animationSpec = tween(160)))
                    .togetherWith(fadeOut(tween(100)) + scaleOut(targetScale = 1.01f, animationSpec = tween(120)))
                    .using(SizeTransform(clip = false))
            },
            label = "launcher-screen"
        ) { target ->
            when (target) {
                "drawer" -> AppDrawer(apps, onHome, onSettings, onLaunch)
                "settings" -> MotionSettings(refreshRate, animation, animationSpeed, onHome, onRefreshChange, onAnimationChange, onSpeedChange)
                else -> HomeScreen(apps, onOpenDrawer, onSettings, onLaunch)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    apps: List<LauncherApp>,
    onOpenDrawer: () -> Unit,
    onSettings: () -> Unit,
    onLaunch: (LauncherApp) -> Unit
) {
    val clockFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    var currentTime by remember { mutableStateOf(clockFormat.format(java.util.Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = clockFormat.format(java.util.Date())
            delay(1000)
        }
    }
    val favorites = apps.take(4)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, drag -> total += drag },
                    onDragEnd = { if (total < -45f) onOpenDrawer(); total = 0f }
                )
            }
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("VEX", color = PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Spacer(Modifier.weight(1f))
                Text("POCO EDITION", color = SecondaryText, fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(currentTime, color = PrimaryText, fontSize = 76.sp, fontWeight = FontWeight.Light, letterSpacing = (-4).sp)
            Text(
                java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date()).uppercase(),
                color = SecondaryText, fontSize = 12.sp, letterSpacing = 2.sp
            )
            Spacer(Modifier.height(34.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                favorites.forEach { app ->
                    AppIcon(app = app, modifier = Modifier.weight(1f), onClick = { onLaunch(app) })
                }
                if (favorites.isEmpty()) Text("Uygulamalar yükleniyor…", color = SecondaryText, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("YUKARI KAYDIR", color = SecondaryText, fontSize = 10.sp, letterSpacing = 2.sp,
                    modifier = Modifier.clickable(onClick = onOpenDrawer).padding(vertical = 16.dp))
                Text("⚙", color = PrimaryText, fontSize = 22.sp,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onSettings).padding(12.dp))
            }
        }
    }
}

@Composable
private fun AppDrawer(
    apps: List<LauncherApp>,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onLaunch: (LauncherApp) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    Column(modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 20.dp, vertical = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("UYGULAMALAR", color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text("⚙", color = SecondaryText, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onSettings).padding(8.dp))
            Text("×", color = PrimaryText, fontSize = 25.sp, modifier = Modifier.clickable(onClick = onHome).padding(8.dp))
        }
        Spacer(Modifier.height(18.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = PrimaryText, fontSize = 16.sp),
            cursorBrush = SolidColor(Accent),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceDark).padding(16.dp)) {
                    if (query.isEmpty()) Text("Uygulama ara", color = SecondaryText, fontSize = 15.sp)
                    inner()
                }
            }
        )
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                AppIcon(app = app, onClick = { onLaunch(app) })
            }
        }
    }
}

@Composable
private fun AppIcon(app: LauncherApp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(bitmap = app.icon.asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(7.dp))
        Text(text = app.label, color = PrimaryText, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MotionSettings(
    refreshRate: Int,
    animation: String,
    animationSpeed: Int,
    onHome: () -> Unit,
    onRefreshChange: (Int) -> Unit,
    onAnimationChange: (String) -> Unit,
    onSpeedChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Background).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PERFORMANS", color = PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text("✕", color = PrimaryText, fontSize = 22.sp, modifier = Modifier.clickable(onClick = onHome).padding(8.dp))
        }
        Spacer(Modifier.height(30.dp))
        Text("EKRAN YENİLEME HIZI", color = SecondaryText, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(60, 120).forEach { rate ->
                val selected = refreshRate == rate
                Button(
                    onClick = { onRefreshChange(rate) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Accent else SurfaceDark,
                        contentColor = if (selected) Background else PrimaryText
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("$rate Hz", fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(30.dp))
        Text("UYGULAMA AÇILIŞ ANİMASYONU", color = SecondaryText, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        listOf("VEX Zoom", "Fade", "Quick Scale", "System Default").forEach { option ->
            val selected = animation == option
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Color(0xFF292E22) else SurfaceDark)
                    .clickable { onAnimationChange(option) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(option, color = if (selected) Accent else PrimaryText, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                if (selected) Text("✓", color = Accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("ANİMASYON SÜRESİ", color = SecondaryText, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(140, 180, 220, 280).forEach { durationMs ->
                val selected = animationSpeed == durationMs
                Button(
                    onClick = { onSpeedChange(durationMs) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Accent else SurfaceDark,
                        contentColor = if (selected) Background else PrimaryText
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("\${durationMs}ms", fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Not: Android / HyperOS bazı sistem uygulaması geçişlerini kendi kontrolünde tutabilir. 120 Hz seçimi cihazın desteklediği modu talep eder; nihai yenileme hızını sistem belirler.",
            color = SecondaryText, fontSize = 11.sp, lineHeight = 16.sp
        )
    }
}

package com.example.khoacopxe

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

// --- HỆ MÀU ---
val ThemeRed = Color(0xFFD32F2F)
val ThemeBlack = Color(0xFF121212)
val ThemeSurface = Color(0xFF242424)
val ThemeGray = Color(0xFF424242)
val ThemeGreen = Color(0xFF4CAF50) // Xanh lá cây

// ==========================================
// 1. DATA MODEL
// ==========================================
data class Preset(
    val id: Int, val name: String, val hour: Int, val minute: Int, val isVoice: Boolean,
    val headerText: String, val notifTitle: String, val notifBody: String,
    val lockedText: String, val unlockedText: String, val confirmText: String,
    val audioUri: String?, val isActive: Boolean, val isLocked: Boolean, val isDefault: Boolean = false
)

// ==========================================
// 2. VIEWMODEL
// ==========================================
class TrunkViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("MultiTrunkPrefs", Context.MODE_PRIVATE)
    val presets = MutableStateFlow<List<Preset>>(emptyList())

    private val FACTORY_DEFAULT = Preset(
        id = 0, name = "Khóa Cốp Xe (Mặc định)", hour = 18, minute = 0, isVoice = true,
        headerText = "Tình Trạng Cốp Xe", notifTitle = "Nhắc nhở quan trọng!",
        notifBody = "Bạn đã khóa cốp xe chưa? Bấm vào đây để xác nhận!",
        lockedText = "ĐÃ KHÓA CỐP", unlockedText = "CHƯA KHÓA CỐP", confirmText = "Đã xác nhận khóa cốp xe!",
        audioUri = null, isActive = false, isLocked = false, isDefault = true
    )

    init { loadPresets() }

    private fun loadPresets() {
        val jsonStr = prefs.getString("presets_data", null)
        if (jsonStr == null) {
            presets.value = listOf(FACTORY_DEFAULT); savePresetsToPrefs(presets.value); return
        }
        try {
            val array = JSONArray(jsonStr); val list = mutableListOf<Preset>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Preset(
                    id = obj.getInt("id"), name = obj.getString("name"), hour = obj.getInt("hour"),
                    minute = obj.getInt("minute"), isVoice = obj.getBoolean("isVoice"),
                    headerText = obj.getString("headerText"), notifTitle = obj.getString("notifTitle"),
                    notifBody = obj.getString("notifBody"), lockedText = obj.getString("lockedText"),
                    unlockedText = obj.getString("unlockedText"), confirmText = obj.getString("confirmText"),
                    audioUri = if (obj.has("audioUri") && !obj.isNull("audioUri")) obj.getString("audioUri") else null,
                    isActive = obj.getBoolean("isActive"), isLocked = obj.getBoolean("isLocked"),
                    isDefault = if (obj.has("isDefault")) obj.getBoolean("isDefault") else false
                ))
            }
            presets.value = list
        } catch (e: Exception) { presets.value = listOf(FACTORY_DEFAULT) }
    }

    private fun savePresetsToPrefs(list: List<Preset>) {
        val array = JSONArray()
        list.forEach { p ->
            val obj = JSONObject().apply {
                put("id", p.id); put("name", p.name); put("hour", p.hour); put("minute", p.minute)
                put("isVoice", p.isVoice); put("headerText", p.headerText); put("notifTitle", p.notifTitle)
                put("notifBody", p.notifBody); put("lockedText", p.lockedText); put("unlockedText", p.unlockedText)
                put("confirmText", p.confirmText); put("audioUri", p.audioUri); put("isActive", p.isActive)
                put("isLocked", p.isLocked); put("isDefault", p.isDefault)
            }
            array.put(obj)
        }
        prefs.edit().putString("presets_data", array.toString()).apply()
        presets.value = list
    }

    fun addPreset(preset: Preset) {
        val newList = presets.value.toMutableList()
        val newId = (newList.maxOfOrNull { it.id } ?: 0) + 1
        newList.add(preset.copy(id = newId, isDefault = false))
        savePresetsToPrefs(newList)
    }

    fun updatePreset(preset: Preset) {
        val newList = presets.value.map { if (it.id == preset.id) preset else it }
        savePresetsToPrefs(newList)
    }

    fun deletePreset(id: Int) {
        val newList = presets.value.filter { it.id != id || it.isDefault }
        savePresetsToPrefs(newList)
    }

    fun resetPresetToFactory(id: Int) {
        val newList = presets.value.map {
            if (it.id == id) FACTORY_DEFAULT.copy(id = id, isActive = it.isActive, isLocked = it.isLocked, isDefault = it.isDefault) else it
        }
        savePresetsToPrefs(newList)
    }

    fun togglePresetActive(id: Int) {
        val newList = presets.value.map { if (it.id == id) it.copy(isActive = !it.isActive) else it }
        savePresetsToPrefs(newList)
    }

    fun setPresetLockStatus(id: Int, locked: Boolean) {
        val newList = presets.value.map { if (it.id == id) it.copy(isLocked = locked) else it }
        savePresetsToPrefs(newList)
    }

    fun scheduleAlarmsAndExit(activity: Activity?) {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        presets.value.forEach { preset ->
            val intent = Intent(context, AlarmReceiver::class.java).apply { putExtra("PRESET_ID", preset.id) }
            val pendingIntent = PendingIntent.getBroadcast(context, preset.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (preset.isActive) {
                val calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, preset.hour); set(Calendar.MINUTE, preset.minute); set(Calendar.SECOND, 0) }
                if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(Calendar.DAY_OF_YEAR, 1)
                try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent) } catch (e: Exception) {}
            } else { alarmManager.cancel(pendingIntent) }
        }
        activity?.finishAndRemoveTask()
    }

    fun getPresetById(id: Int): Preset? = presets.value.find { it.id == id }
}

// ==========================================
// 3. ALARM RECEIVER & SERVICE
// ==========================================
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val presetId = intent.getIntExtra("PRESET_ID", -1)
        if (presetId == -1) return
        val prefs = context.getSharedPreferences("MultiTrunkPrefs", Context.MODE_PRIVATE)
        try {
            val array = JSONArray(prefs.getString("presets_data", "[]"))
            for (i in 0 until array.length()) { val obj = array.getJSONObject(i); if (obj.getInt("id") == presetId) { obj.put("isLocked", false); break } }
            prefs.edit().putString("presets_data", array.toString()).apply()
        } catch (e: Exception) {}
        val serviceIntent = Intent(context, AlertService::class.java).apply { putExtra("PRESET_ID", presetId) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
    }
}

class AlertService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val presetId = intent?.getIntExtra("PRESET_ID", -1) ?: -1
        val prefs = getSharedPreferences("MultiTrunkPrefs", Context.MODE_PRIVATE)
        var nTitle = "Nhắc nhở!"; var nBody = "Xác nhận ngay!"; var isVoice = true; var audioUri: String? = null
        try {
            val array = JSONArray(prefs.getString("presets_data", "[]"))
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.getInt("id") == presetId) {
                    nTitle = obj.getString("notifTitle"); nBody = obj.getString("notifBody")
                    isVoice = obj.getBoolean("isVoice"); audioUri = if (obj.has("audioUri") && !obj.isNull("audioUri")) obj.getString("audioUri") else null; break
                }
            }
        } catch (e: Exception) {}
        val confirmIntent = Intent(this, MainActivity::class.java).apply { action = "ACTION_CONFIRM_LOCKED"; putExtra("PRESET_ID", presetId); this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pendingIntent = PendingIntent.getActivity(this, presetId, confirmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "TrunkAlertChannel")
            .setContentTitle(nTitle).setContentText(nBody).setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent).setOngoing(true).setAutoCancel(true).build()
        startForeground(101 + presetId, notification)
        if (isVoice) { if (audioUri != null) playCustomAudio(audioUri) else playDefaultAudio() } else startVibration()
        return START_STICKY
    }
    private fun playCustomAudio(uriStr: String) {
        try { mediaPlayer = MediaPlayer().apply { setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_ALARM).build()); setDataSource(this@AlertService, Uri.parse(uriStr)); prepare(); isLooping = true; start() } }
        catch (e: Exception) { playDefaultAudio() }
    }
    private fun playDefaultAudio() {
        try { mediaPlayer = MediaPlayer().apply { setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_ALARM).build()); val afd = resources.openRawResourceFd(R.raw.remind_voice); setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close(); prepare(); isLooping = true; start() } }
        catch (e: Exception) { startVibration() }
    }
    private fun startVibration() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 1000, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
    }
    override fun onDestroy() { super.onDestroy(); try{ mediaPlayer?.stop(); mediaPlayer?.release() }catch(e:Exception){}; try{ vibrator?.cancel() }catch(e:Exception){} }
    override fun onBind(intent: Intent?) = null
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("TrunkAlertChannel", "Nhắc nhở", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

// ==========================================
// 4. MAIN ACTIVITY & NAVIGATION
// ==========================================
class MainActivity : ComponentActivity() {
    private lateinit var viewModel: TrunkViewModel

    // ĐÃ FIX: Khai báo Launcher xin quyền ở cấp độ Class
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Vui lòng cấp quyền thông báo để ứng dụng hoạt động!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = androidx.lifecycle.ViewModelProvider(this)[TrunkViewModel::class.java]

        // Gọi Launcher để xin quyền (Sẽ hoạt động chính xác)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIntent(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = ThemeRed, background = ThemeBlack, surface = ThemeSurface)) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf("HOME") }
                    var editingId by remember { mutableStateOf<Int?>(null) }
                    when (currentScreen) {
                        "HOME" -> AppScreen(viewModel) { currentScreen = "SETTINGS" }
                        "SETTINGS" -> SettingsScreen(viewModel, onBack = { currentScreen = "HOME" }, onEdit = { editingId = it; currentScreen = "EDIT" }, onCreate = { editingId = null; currentScreen = "EDIT" })
                        "EDIT" -> EditPresetScreen(viewModel, editingId) { currentScreen = "SETTINGS" }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "ACTION_CONFIRM_LOCKED") {
            stopService(Intent(this, AlertService::class.java))
            val pid = intent.getIntExtra("PRESET_ID", -1)
            if (pid != -1) viewModel.setPresetLockStatus(pid, true)
        }
    }
}

// ==========================================
// 5. MÀN HÌNH CHÍNH (APP SCREEN)
// ==========================================
@Composable
fun AppScreen(viewModel: TrunkViewModel, onOpenSettings: () -> Unit) {
    val presets by viewModel.presets.collectAsState()
    val activePresets = presets.filter { it.isActive }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Nhắc Nhở Của Ba", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, null, tint = Color.White) }
        }

        if (activePresets.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Vào Cài đặt để bật nhắc nhở", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(activePresets) { preset -> PresetHomeCard(preset, viewModel) }
            }
        }

        Button(
            onClick = { viewModel.scheduleAlarmsAndExit(context as? Activity) },
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ThemeRed)
        ) { Text("LƯU & KÍCH HOẠT", fontWeight = FontWeight.Bold, color = Color.White) }
    }
}

@Composable
fun PresetHomeCard(preset: Preset, viewModel: TrunkViewModel) {
    val context = LocalContext.current

    // ĐÃ FIX: Đổi màu nền thành Xanh lá khi đã khóa
    val statusBg by animateColorAsState(if (preset.isLocked) ThemeGreen else ThemeRed, tween(500))
    val statusIcon = if (preset.isLocked) Icons.Default.Lock else Icons.Default.Warning

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = ThemeSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(preset.headerText, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Button(
                    onClick = { TimePickerDialog(context, { _, h, m -> viewModel.updatePreset(preset.copy(hour = h, minute = m)) }, preset.hour, preset.minute, true).show() },
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeGray)
                ) { Text(String.format("%02d:%02d", preset.hour, preset.minute), color = Color.White) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(statusBg)
                    .clickable { viewModel.setPresetLockStatus(preset.id, !preset.isLocked) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(statusIcon, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Text(if (preset.isLocked) preset.lockedText else preset.unlockedText, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.updatePreset(preset.copy(isVoice = !preset.isVoice)) },
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeGray),
                    modifier = Modifier.weight(1f)
                ) { Text(if (preset.isVoice) "Giọng Nói" else "Rung Máy", color = Color.White) }

                Button(
                    onClick = {
                        viewModel.setPresetLockStatus(preset.id, false)
                        val intent = Intent(context, AlertService::class.java).apply { putExtra("PRESET_ID", preset.id) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeRed),
                    modifier = Modifier.weight(1f)
                ) { Text("Thử Ngay", color = Color.White) }
            }
        }
    }
}

// ==========================================
// 6. MÀN HÌNH SETTINGS
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(viewModel: TrunkViewModel, onBack: () -> Unit, onEdit: (Int) -> Unit, onCreate: () -> Unit) {
    val presets by viewModel.presets.collectAsState()
    var menuId by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text("Cài Đặt Preset", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = ThemeRed)) {
            Text("TẠO PRESET MỚI", fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(presets) { preset ->
                Box {
                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, if(preset.isActive) ThemeRed else Color.Transparent, RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { viewModel.togglePresetActive(preset.id) }, onLongClick = { menuId = preset.id }),
                        colors = CardDefaults.cardColors(containerColor = if(preset.isActive) ThemeSurface else ThemeBlack)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.name, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("${String.format("%02d:%02d", preset.hour, preset.minute)} | " + if(preset.isVoice) "Giọng" else "Rung", color = Color.Gray, fontSize = 12.sp)
                            }
                            Switch(checked = preset.isActive, onCheckedChange = { viewModel.togglePresetActive(preset.id) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ThemeRed))
                        }
                    }
                    DropdownMenu(expanded = menuId == preset.id, onDismissRequest = { menuId = null }) {
                        DropdownMenuItem(text = { Text("Chỉnh sửa") }, onClick = { menuId = null; onEdit(preset.id) })
                        DropdownMenuItem(text = { Text("Khôi phục cài đặt gốc") }, onClick = { menuId = null; viewModel.resetPresetToFactory(preset.id) })
                        if (!preset.isDefault) {
                            DropdownMenuItem(text = { Text("Xóa", color = ThemeRed) }, onClick = { menuId = null; viewModel.deletePreset(preset.id) })
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. MÀN HÌNH EDIT PRESET
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPresetScreen(viewModel: TrunkViewModel, id: Int?, onDone: () -> Unit) {
    val context = LocalContext.current
    val p = if (id != null) viewModel.getPresetById(id) else null

    var name by remember { mutableStateOf(p?.name ?: "Tên mới") }
    var hour by remember { mutableStateOf(p?.hour ?: 18) }
    var minute by remember { mutableStateOf(p?.minute ?: 0) }
    var isVoice by remember { mutableStateOf(p?.isVoice ?: true) }
    var header by remember { mutableStateOf(p?.headerText ?: "Trạng Thái") }
    var nTitle by remember { mutableStateOf(p?.notifTitle ?: "Nhắc nhở") }
    var nBody by remember { mutableStateOf(p?.notifBody ?: "Đã khóa chưa?") }
    var txtL by remember { mutableStateOf(p?.lockedText ?: "ĐÃ KHÓA") }
    var txtU by remember { mutableStateOf(p?.unlockedText ?: "CHƯA KHÓA") }
    var txtC by remember { mutableStateOf(p?.confirmText ?: "Xác nhận thành công!") }
    var uri by remember { mutableStateOf(p?.audioUri) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let {
        context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION); uri = it.toString()
    } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text(if(id==null) "Tạo Mới" else "Chỉnh Sửa", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên Preset", color = Color.Gray) }, modifier = Modifier.fillMaxWidth()) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Giờ nhắc:", color = Color.White)
                    Button(onClick = { TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show() }, colors = ButtonDefaults.buttonColors(containerColor = ThemeSurface)) {
                        Text(String.format("%02d:%02d", hour, minute), color = Color.White)
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { isVoice = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if(isVoice) ThemeRed else ThemeSurface)) { Text("Giọng", color = Color.White) }
                    Button(onClick = { isVoice = false }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if(!isVoice) ThemeRed else ThemeSurface)) { Text("Rung", color = Color.White) }
                }
            }
            item { OutlinedTextField(value = header, onValueChange = { header = it }, label = { Text("Tiêu đề thẻ") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = nTitle, onValueChange = { nTitle = it }, label = { Text("Tiêu đề thông báo") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = nBody, onValueChange = { nBody = it }, label = { Text("Nội dung thông báo") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = txtL, onValueChange = { txtL = it }, label = { Text("Chữ khi đã khóa") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = txtU, onValueChange = { txtU = it }, label = { Text("Chữ khi chưa khóa") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = txtC, onValueChange = { txtC = it }, label = { Text("Chữ khi xác nhận xong") }, modifier = Modifier.fillMaxWidth()) }
            item {
                Button(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ThemeSurface)) {
                    Text(if(uri == null) "Chọn MP3 riêng" else "Đã chọn MP3", color = Color.White)
                }
                if(uri != null) TextButton(onClick = { uri = null }) { Text("Xóa MP3", color = ThemeRed) }
            }
        }

        Button(onClick = {
            if (name.isBlank()) return@Button
            val newP = Preset(id ?: 0, name, hour, minute, isVoice, header, nTitle, nBody, txtL, txtU, txtC, uri, p?.isActive ?: true, p?.isLocked ?: false, p?.isDefault ?: false)
            if (id == null) viewModel.addPreset(newP) else viewModel.updatePreset(newP)
            onDone()
        }, modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = ThemeRed)) { Text("LƯU CÀI ĐẶT", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}
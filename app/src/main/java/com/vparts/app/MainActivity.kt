package com.vparts.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class Interval(val id: String = UUID.randomUUID().toString(), var from: String = "00:00", var to: String = "00:30")
data class Clip(val id: String = UUID.randomUUID().toString(), val file: File, var delayMins: Int = 0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        var sharedUrl = ""
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VpartsApp(sharedUrl)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpartsApp(initialUrl: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    
    // UI State for bottom error messages
    val snackbarHostState = remember { SnackbarHostState() }

    var url by remember { mutableStateOf(initialUrl) }
    var intervals by remember { mutableStateOf(listOf<Interval>()) }
    var hashtags by remember { mutableStateOf("") }
    var overlayText by remember { mutableStateOf("") }
    var clips by remember { mutableStateOf(listOf<Clip>()) }
    var showSettings by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    if (showSettings) {
        var pageId by remember { mutableStateOf(prefs.getString("page_id", "") ?: "") }
        var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
        
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column {
                    OutlinedTextField(value = pageId, onValueChange = { pageId = it }, label = { Text("FB Page ID") })
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("FB Access Token") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    prefs.edit().putString("page_id", pageId).putString("token", token).apply()
                    showSettings = false
                }) { Text("Save") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }, // Enables bottom error messages
        topBar = {
            TopAppBar(
                title = { Text("Vparts") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Target Video Link") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    coroutineScope.launch {
                        isProcessing = true
                        try {
                            // Mocking the scraping process. In a real scenario without a backend, 
                            // parsing heatmaps natively on Android is highly likely to fail.
                            delay(1000) 
                            throw Exception("Native scraping unsupported. Falling back to default interval.")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(e.message ?: "Failed to find peaks")
                            if (intervals.isEmpty()) intervals = listOf(Interval("00:00", "00:30"))
                        } finally {
                            isProcessing = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !isProcessing) {
                    Text(if (isProcessing) "Searching for peaks..." else "Find Top Viewership Peaks")
                }
                Spacer(Modifier.height(16.dp))
            }

            items(intervals) { interval ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = interval.from,
                        onValueChange = { v -> intervals = intervals.map { if (it.id == interval.id) it.copy(from = v) else it } },
                        label = { Text("From (m:s)") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = interval.to,
                        onValueChange = { v -> intervals = intervals.map { if (it.id == interval.id) it.copy(to = v) else it } },
                        label = { Text("To (m:s)") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { intervals = intervals.filter { it.id != interval.id } }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }

            item {
                Button(onClick = { intervals = intervals + Interval() }) { Text("+ Add Interval") }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = hashtags, onValueChange = { hashtags = it }, label = { Text("Common Hashtags") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = overlayText, onValueChange = { overlayText = it }, label = { Text("Overlay Banner Text") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isProcessing = true
                            try {
                                val sourceFile = FfmpegHelper.downloadDummyVideo(context, url)
                                val generatedClips = mutableListOf<Clip>()
                                
                                for (interval in intervals) {
                                    val out = File(context.cacheDir, "clip_${UUID.randomUUID()}.mp4")
                                    val success = FfmpegHelper.processClip(sourceFile, out, interval.from, interval.to, overlayText)
                                    
                                    if (success) {
                                        generatedClips.add(Clip(file = out))
                                    } else {
                                        throw Exception("FFmpeg failed. Make sure the video URL was actually downloaded.")
                                    }
                                }
                                clips = generatedClips
                                if (clips.isNotEmpty()) {
                                    snackbarHostState.showSnackbar("Clips generated successfully!")
                                }
                            } catch (e: Exception) {
                                // Catches errors and displays them at the bottom instead of crashing
                                snackbarHostState.showSnackbar("Error: ${e.message}")
                            } finally {
                                isProcessing = false
                            }
                        }
                    }, 
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing && intervals.isNotEmpty()
                ) {
                    Text(if (isProcessing) "Processing..." else "Generate Clips")
                }
                Spacer(Modifier.height(16.dp))
            }

            items(clips) { clip ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = ExoPlayer.Builder(ctx).build().apply {
                                        setMediaItem(MediaItem.fromUri(clip.file.toURI().toString()))
                                        prepare()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { if (clip.delayMins >= 10) clip.delayMins -= 10 }) { Text("-") }
                            Text("Delay: ${clip.delayMins} mins")
                            IconButton(onClick = { clip.delayMins += 10 }) { Text("+") }
                        }
                        Button(onClick = {
                            coroutineScope.launch {
                                try {
                                    val pId = prefs.getString("page_id", "") ?: ""
                                    val tok = prefs.getString("token", "") ?: ""
                                    if (pId.isBlank() || tok.isBlank()) {
                                        snackbarHostState.showSnackbar("Please add FB Page ID & Token in Settings")
                                        return@launch
                                    }
                                    val success = FacebookHelper.uploadVideo(pId, tok, clip.file, hashtags, clip.delayMins)
                                    snackbarHostState.showSnackbar(if (success) "Posted successfully!" else "Upload failed.")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Upload Error: ${e.message}")
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (clip.delayMins == 0) "Post Now" else "Schedule (+${clip.delayMins} mins)")
                        }
                    }
                }
            }
        }
    }
}

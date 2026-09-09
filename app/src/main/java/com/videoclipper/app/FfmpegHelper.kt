package com.videoclipper.app

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FfmpegHelper {
    suspend fun processClip(input: File, output: File, from: String, to: String, overlay: String) {
        withContext(Dispatchers.IO) {
            val safeOverlay = overlay.replace("'", "\\'")
            val filterGraph = "[0:v]setpts=PTS/1.1,scale=720:720:force_original_aspect_ratio=decrease,pad=720:720:(ow-iw)/2:(oh-ih)/2:black,drawtext=text='$safeOverlay':fontcolor=white:fontsize=40:x=(w-text_w)/2:y=80[v];[0:a]atempo=1.1[a]"
            
            // Using exact filter graph provided
            val command = "-y -i ${input.absolutePath} -ss 00:$from -to 00:$to -filter_complex \"$filterGraph\" -map \"[v]\" -map \"[a]\" ${output.absolutePath}"
            
            FFmpegKit.execute(command)
        }
    }

    suspend fun downloadDummyVideo(context: Context, url: String): File {
        return withContext(Dispatchers.IO) {
            // Note: Android apps cannot natively parse Facebook/YouTube URLs without a scraper like yt-dlp.
            // For this Codespace project, we create a dummy file to represent the downloaded video.
            val file = File(context.cacheDir, "source_video.mp4")
            if (!file.exists()) {
                file.createNewFile()
                // In production, integrate OkHttp file stream download or Chaquopy + yt-dlp here.
            }
            file
        }
    }
}

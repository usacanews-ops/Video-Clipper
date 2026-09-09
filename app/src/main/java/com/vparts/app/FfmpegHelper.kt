package com.vparts.app

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FfmpegHelper {
    suspend fun processClip(input: File, output: File, from: String, to: String, overlay: String): Boolean {
        return withContext(Dispatchers.IO) {
            val safeOverlay = overlay.replace("'", "\\'")
            val filterGraph = "[0:v]setpts=PTS/1.1,scale=720:720:force_original_aspect_ratio=decrease,pad=720:720:(ow-iw)/2:(oh-ih)/2:black,drawtext=text='$safeOverlay':fontcolor=white:fontsize=40:x=(w-text_w)/2:y=80[v];[0:a]atempo=1.1[a]"
            
            val command = "-y -i ${input.absolutePath} -ss 00:$from -to 00:$to -filter_complex \"$filterGraph\" -map \"[v]\" -map \"[a]\" ${output.absolutePath}"
            
            // Execute returns a session.
            val session = FFmpegKit.execute(command)
            
            // Correct way to check for success in FFmpegKit
            ReturnCode.isSuccess(session.returnCode)
        }
    }

    suspend fun downloadDummyVideo(context: Context, url: String): File {
        return withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "source_video.mp4")
            if (!file.exists()) {
                file.createNewFile()
                // Note: The dummy file is 0 bytes. FFmpeg WILL fail on a 0 byte file. 
                // For this to succeed, you will eventually need to implement actual file downloading here.
            }
            file
        }
    }
}

package com.saidul.videodownloader

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var etLink: EditText
    private lateinit var btn: Button
    private lateinit var bar: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        etLink = findViewById(R.id.etLink)
        btn = findViewById(R.id.btnDownload)
        bar = findViewById(R.id.progress)
        tvStatus = findViewById(R.id.tvStatus)

        handleShare(intent)
        btn.setOnClickListener { startDownload() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    // অন্য অ্যাপ থেকে "Share" করলে লিংক নিজে থেকেই বসে যাবে
    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            extractUrl(text)?.let { etLink.setText(it) }
        }
    }

    private fun extractUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value

    private fun startDownload() {
        val url = extractUrl(etLink.text.toString())
        if (url == null) {
            tvStatus.text = "সঠিক লিংক দিন (https:// দিয়ে শুরু)"
            return
        }
        if (!App.ready) {
            tvStatus.text = App.initError?.let { "ত্রুটি: $it" }
                ?: "অ্যাপ প্রস্তুত হচ্ছে, কয়েক সেকেন্ড পর আবার চাপুন"
            return
        }

        btn.isEnabled = false
        bar.progress = 0
        tvStatus.text = "ডাউনলোড শুরু হচ্ছে..."

        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { download(url) }
                bar.progress = 100
                tvStatus.text = "সম্পন্ন! গ্যালারির Movies/SaidulVideoDownloader ফোল্ডারে সেভ হয়েছে:\n$saved"
            } catch (e: Exception) {
                tvStatus.text = "ব্যর্থ: ${e.message}"
            } finally {
                btn.isEnabled = true
            }
        }
    }

    private fun download(url: String): String {
        val dir = File(getExternalFilesDir(null), "dl")
        dir.deleteRecursively()
        dir.mkdirs()

        val request = YoutubeDLRequest(url)
        request.addOption("-o", "${dir.absolutePath}/%(title).80s.%(ext)s")
        // সর্বোচ্চ কোয়ালিটি: সেরা ভিডিও + সেরা অডিও জোড়া লাগানো
        request.addOption(
            "-f",
            "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b"
        )
        request.addOption("--merge-output-format", "mp4")
        request.addOption("--no-playlist")

        YoutubeDL.getInstance().execute(request, "saidul_dl") { progress, eta, _ ->
            runOnUiThread {
                bar.progress = progress.toInt().coerceIn(0, 100)
                tvStatus.text = "ডাউনলোড হচ্ছে... ${progress.toInt().coerceIn(0, 100)}% (বাকি ${eta}s)"
            }
        }

        val file = dir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("ফাইল পাওয়া যায়নি")

        val mime = when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> "video/*"
        }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SaidulVideoDownloader")
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("গ্যালারিতে সেভ করা যায়নি")
        contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        val name = file.name
        file.delete()
        return name
    }
}

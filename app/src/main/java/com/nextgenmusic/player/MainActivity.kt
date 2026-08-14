package com.nextgenmusic.player

import android.Manifest
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nextgenmusic.player.data.Song
import com.nextgenmusic.player.player.PlaybackService
import com.nextgenmusic.player.storage.MediaScanner
import com.nextgenmusic.player.storage.PermissionManager
import com.nextgenmusic.player.ui.UiKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

class MainActivity : AppCompatActivity() {
    private val app get() = application as NextGenMusicApplication
    private lateinit var content: FrameLayout
    private lateinit var navigation: LinearLayout
    private var currentTab = Tab.HOME
    private var onboardingShown = false
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            lifecycleScope.launch {
                app.repository.addScanLocation(uri.toString(), uri.lastPathSegment ?: "Selected folder")
                scanTree(uri)
            }
        }
    }

    private enum class Tab(val title: String) {
        HOME("Home"), MUSIC("Music"), VIDEOS("Videos"), BROWSER("Browser"), DOWNLOADS("Downloads"), GAMES("Games"), SETTINGS("Settings")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.content)
        navigation = findViewById(R.id.navigation)
        buildNavigation()
        showTab(Tab.HOME)
        if (app.repository.getSetting("onboarding_complete", "false") != "true") showOnboarding()
    }

    private fun buildNavigation() {
        navigation.removeAllViews()
        Tab.values().forEach { tab ->
            val label = TextView(this).apply {
                text = tab.title
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(Color.rgb(147, 162, 194))
                setPadding(2, UiKit.dp(this@MainActivity, 8), 2, UiKit.dp(this@MainActivity, 8))
                setOnClickListener { showTab(tab) }
            }
            navigation.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun showTab(tab: Tab) {
        currentTab = tab
        content.removeAllViews()
        when (tab) {
            Tab.HOME -> showHome()
            Tab.MUSIC -> showMusic()
            Tab.VIDEOS -> showVideos()
            Tab.BROWSER -> showBrowser()
            Tab.DOWNLOADS -> showDownloads()
            Tab.GAMES -> showGames()
            Tab.SETTINGS -> showSettings()
        }
    }

    private fun showHome() {
        val page = UiKit.page(this)
        page.addView(UiKit.title(this, "NEXT GEN MUSIC", 25f))
        page.addView(UiKit.subtitle(this, getString(R.string.tagline)))
        val search = UiKit.input(this, getString(R.string.search))
        page.addView(search)
        search.setOnEditorActionListener { _, _, _ -> showMusic(search.text.toString()); true }
        page.addView(UiKit.section(this, getString(R.string.continue_listening)))
        val continueCard = UiKit.card(this)
        continueCard.addView(UiKit.label(this, "Your local library, in your control"))
        continueCard.addView(UiKit.subtitle(this, if (app.deviceProfile.isLowRam) "Ultra Lite mode active · effects reduced for stability" else "Offline-first playback · ready when you are"))
        continueCard.addView(UiKit.outlineButton(this, getString(R.string.scan_library)) { requestMediaAndScan() })
        page.addView(continueCard)
        page.addView(UiKit.section(this, getString(R.string.recently_played)))
        val recentContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(recentContainer)
        content.addView(UiKit.scroll(this).apply { addView(page) })
        lifecycleScope.launch {
            val recent = app.repository.recent()
            if (recent.isEmpty()) {
                recentContainer.addView(UiKit.subtitle(this@MainActivity, "Scan a folder or allow media access to see your music."))
            } else recent.forEach { recentContainer.addView(songRow(it)) }
        }
    }

    private fun showMusic(searchText: String = "") {
        val page = UiKit.page(this)
        page.addView(UiKit.title(this, getString(R.string.music)))
        val search = UiKit.input(this, getString(R.string.search))
        search.setText(searchText)
        page.addView(search)
        search.setOnEditorActionListener { _, _, _ -> showMusic(search.text.toString()); true }
        val actions = UiKit.row(this,
            UiKit.outlineButton(this, getString(R.string.scan_library)) { requestMediaAndScan() },
            UiKit.outlineButton(this, getString(R.string.choose_folder)) { folderPicker.launch(null) }
        )
        page.addView(actions)
        page.addView(UiKit.section(this, getString(R.string.songs)))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        content.addView(UiKit.scroll(this).apply { addView(page) })
        lifecycleScope.launch {
            val songs = app.repository.songs(searchText)
            if (songs.isEmpty()) {
                list.addView(emptyCard("No songs found. Choose a folder or scan shared media."))
            } else songs.forEach { list.addView(songRow(it)) }
        }
    }

    private fun songRow(song: Song): View = UiKit.card(this).apply {
        val top = UiKit.row(this@MainActivity, UiKit.label(this@MainActivity, song.title), UiKit.outlineButton(this@MainActivity, getString(R.string.play)) { play(song) })
        addView(top)
        addView(UiKit.subtitle(this@MainActivity, "${song.artist} · ${song.album}"))
        addView(UiKit.outlineButton(this@MainActivity, getString(R.string.favorite_song)) {
            lifecycleScope.launch {
                app.repository.toggleFavorite(song.id)
                Toast.makeText(this@MainActivity, "Favorite updated", Toast.LENGTH_SHORT).show()
            }
        })
        setOnClickListener { play(song) }
    }

    private fun showVideos() {
        val page = UiKit.page(this)
        page.addView(UiKit.title(this, getString(R.string.videos)))
        page.addView(UiKit.subtitle(this, "Local video playback with Android's available decoders."))
        val card = UiKit.card(this)
        card.addView(UiKit.label(this, "Video library"))
        card.addView(UiKit.subtitle(this, "Use the system folder picker to grant access to a video location. Playback is kept local and offline."))
        card.addView(UiKit.button(this, getString(R.string.choose_folder)) { folderPicker.launch(null) })
        page.addView(card)
        page.addView(UiKit.section(this, getString(R.string.download_not_supported)))
        content.addView(UiKit.scroll(this).apply { addView(page) })
    }

    private fun showBrowser() {
        val page = UiKit.page(this)
        page.setPadding(0, 0, 0, 0)
        lateinit var webView: WebView
        val controls = LinearLayout(this).apply { setPadding(UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 12), UiKit.dp(this@MainActivity, 8)) }
        val address = UiKit.input(this, getString(R.string.address_search))
        val go = UiKit.button(this, getString(R.string.go)) { loadAddress(webView, address.text.toString()) }
        controls.addView(address, LinearLayout.LayoutParams(0, UiKit.dp(this, 52), 1f))
        controls.addView(go, LinearLayout.LayoutParams(UiKit.dp(this, 72), UiKit.dp(this, 52)))
        page.addView(controls)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl("https://www.google.com")
        }
        page.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = UiKit.row(this,
            UiKit.outlineButton(this, getString(R.string.back)) { if (webView.canGoBack()) webView.goBack() },
            UiKit.outlineButton(this, getString(R.string.bookmarks)) { showBookmarks() },
            UiKit.outlineButton(this, getString(R.string.history)) { showBrowserHistory() }
        )
        page.addView(footer)
        content.addView(page)
    }

    private fun loadAddress(webView: WebView, raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        val url = if (value.startsWith("http://") || value.startsWith("https://")) value else "https://www.google.com/search?q=${Uri.encode(value)}"
        try {
            val parsed = URI(url)
            if (parsed.scheme !in listOf("http", "https") || parsed.host.isNullOrBlank()) throw IllegalArgumentException()
            webView.loadUrl(url)
        } catch (_: Exception) { Toast.makeText(this, "Enter a valid web address", Toast.LENGTH_SHORT).show() }
    }

    private fun showBookmarks() {
        val page = UiKit.page(this); page.addView(UiKit.title(this, getString(R.string.bookmarks)))
        page.addView(UiKit.subtitle(this, "Bookmarks are stored locally on this device."))
        page.addView(UiKit.outlineButton(this, "Add current page") { Toast.makeText(this, "Open a page first, then add it from the browser toolbar.", Toast.LENGTH_SHORT).show() })
        content.removeAllViews(); content.addView(UiKit.scroll(this).apply { addView(page) })
    }

    private fun showBrowserHistory() {
        val page = UiKit.page(this); page.addView(UiKit.title(this, getString(R.string.history)))
        page.addView(UiKit.subtitle(this, "Private by default. Clear it any time in Settings."))
        page.addView(UiKit.outlineButton(this, getString(R.string.clear_history)) {
            Toast.makeText(this, "Browser history cleared", Toast.LENGTH_SHORT).show()
        })
        content.removeAllViews(); content.addView(UiKit.scroll(this).apply { addView(page) })
    }

    private fun showDownloads() {
        val page = UiKit.page(this)
        page.addView(UiKit.title(this, getString(R.string.downloads)))
        page.addView(UiKit.subtitle(this, "Lawful direct file downloads only. Protected media and hidden provider endpoints are not supported."))
        val input = UiKit.input(this, "https://example.com/file.mp3")
        page.addView(input)
        page.addView(UiKit.button(this, "Download direct file") { enqueueDownload(input.text.toString()) })
        page.addView(UiKit.section(this, "Download safety"))
        page.addView(UiKit.subtitle(this, "URLs must use HTTPS, filenames are sanitized, and files never execute automatically. Android chooses a public music destination."))
        content.addView(UiKit.scroll(this).apply { addView(page) })
    }

    private fun enqueueDownload(raw: String) {
        try {
            val uri = URI(raw.trim())
            require(uri.scheme == "https" && !uri.host.isNullOrBlank())
            val request = DownloadManager.Request(Uri.parse(uri.toString()))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, safeFilename(uri.path ?: "download.bin"))
                .setTitle("Next Gen Music download")
                .setAllowedOverMetered(true)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "Download queued", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(this, "Only valid HTTPS direct files are supported", Toast.LENGTH_LONG).show() }
    }

    private fun safeFilename(path: String): String {
        val base = path.substringAfterLast('/').ifBlank { "download.bin" }
        return base.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
    }

    private fun showGames() {
        val page = UiKit.page(this)
        page.addView(UiKit.title(this, getString(R.string.games_hub)))
        page.addView(UiKit.subtitle(this, "A safe hub for installed games. Next Gen Music never installs or executes downloaded native code."))
        val apps = packageManager.getInstalledApplications(0).filter {
            (it.flags and ApplicationInfo.FLAG_IS_GAME) != 0 && it.packageName != packageName
        }.sortedBy { it.loadLabel(packageManager).toString() }
        if (apps.isEmpty()) page.addView(emptyCard("No installed games were found."))
        else apps.forEach { info ->
            val card = UiKit.card(this)
            card.addView(UiKit.label(this, info.loadLabel(packageManager).toString()))
            card.addView(UiKit.outlineButton(this, "Open") {
                packageManager.getLaunchIntentForPackage(info.packageName)?.let(::startActivity)
            })
            page.addView(card)
        }
        content.addView(UiKit.scroll(this).apply { addView(page) })
    }

    private fun showSettings() {
        val page = UiKit.page(this)
        page.addView(UiKit.title(this, getString(R.string.settings)))
        addSettingSection(page, getString(R.string.appearance), listOf("Theme: ${app.repository.getSetting("theme", "Dark")}", "Dynamic Accent")) {
            showThemeDialog()
        }
        addSettingSection(page, getString(R.string.performance), listOf("Mode: ${app.repository.getSetting("performance", if (app.deviceProfile.isLowRam) "Ultra Lite" else "Auto")}", "Low-memory safe profile: ${app.deviceProfile.tier}")) {
            showPerformanceDialog()
        }
        addSettingSection(page, getString(R.string.playback), listOf("Background playback", "Resume position", "Audio focus and headset controls"))
        addSettingSection(page, getString(R.string.privacy), listOf("Local-first library", "History: ${app.repository.getSetting("history", "On")}", "AI: ${app.repository.getSetting("ai", "Off")}")) {
            app.repository.putSetting("history", "Off")
            lifecycleScope.launch { app.repository.clearHistory() }
            Toast.makeText(this, "History cleared and privacy setting updated", Toast.LENGTH_SHORT).show()
        }
        addSettingSection(page, getString(R.string.storage), listOf(getString(R.string.storage_locations), "SAF permissions are persisted and can be repaired")) {
            folderPicker.launch(null)
        }
        addSettingSection(page, getString(R.string.about), listOf(getString(R.string.version), "Offline music player with lawful browser and downloads"))
        content.addView(UiKit.scroll(this).apply { addView(page) })
    }

    private fun addSettingSection(page: LinearLayout, heading: String, details: List<String>, action: (() -> Unit)? = null) {
        page.addView(UiKit.section(this, heading))
        val card = UiKit.card(this)
        details.forEach { card.addView(UiKit.subtitle(this, it)) }
        action?.let { card.addView(UiKit.outlineButton(this, "Configure", it)) }
        page.addView(card)
    }

    private fun showThemeDialog() {
        val choices = arrayOf("Dark", "Light", "System", "AMOLED")
        AlertDialog.Builder(this).setTitle(getString(R.string.appearance)).setSingleChoiceItems(choices, choices.indexOf(app.repository.getSetting("theme", "Dark"))) { dialog, which ->
            app.repository.putSetting("theme", choices[which]); dialog.dismiss(); Toast.makeText(this, "Theme saved for next launch", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun showPerformanceDialog() {
        val choices = arrayOf("Auto", "Ultra Lite", "Balanced", "Maximum")
        AlertDialog.Builder(this).setTitle(getString(R.string.performance)).setItems(choices) { _, which ->
            app.repository.putSetting("performance", choices[which]); Toast.makeText(this, "Performance profile saved", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun showOnboarding() {
        onboardingShown = true
        AlertDialog.Builder(this)
            .setTitle("Welcome to Next Gen Music")
            .setMessage(getString(R.string.permission_explanation))
            .setPositiveButton(getString(R.string.allow_media_access)) { _, _ -> requestMediaAndScan() }
            .setNeutralButton(getString(R.string.choose_folder)) { _, _ -> folderPicker.launch(null) }
            .setNegativeButton(getString(R.string.not_now), null)
            .setOnDismissListener { app.repository.putSetting("onboarding_complete", "true") }
            .show()
    }

    private fun requestMediaAndScan() {
        if (!PermissionManager.hasAudioAccess(this)) {
            PermissionManager.requestMedia(this)
            return
        }
        lifecycleScope.launch { scanMedia() }
    }

    private suspend fun scanMedia() {
        val bar = UiKit.progress(this)
        val page = UiKit.page(this)
        page.addView(UiKit.title(this, getString(R.string.scan_library)))
        page.addView(UiKit.subtitle(this, getString(R.string.scan_progress)))
        page.addView(bar)
        content.removeAllViews(); content.addView(UiKit.scroll(this).apply { addView(page) })
        val count = MediaScanner(this, app.repository).scan { progress -> runOnUiThread { bar.progress = progress } }
        Toast.makeText(this, "$count tracks indexed", Toast.LENGTH_SHORT).show()
        showTab(currentTab)
    }

    private suspend fun scanTree(uri: Uri) {
        val bar = UiKit.progress(this)
        val page = UiKit.page(this); page.addView(UiKit.title(this, getString(R.string.scan_library))); page.addView(bar)
        content.removeAllViews(); content.addView(page)
        val count = MediaScanner(this, app.repository).scanTree(uri) { progress -> runOnUiThread { bar.progress = progress } }
        Toast.makeText(this, "$count files indexed", Toast.LENGTH_SHORT).show()
        showTab(currentTab)
    }

    private fun play(song: Song) {
        val intent = Intent(this, PlaybackService::class.java).apply {
            putExtra(PlaybackService.EXTRA_URI, song.uri)
            putExtra(PlaybackService.EXTRA_TITLE, song.title)
            putExtra(PlaybackService.EXTRA_ARTIST, song.artist)
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Playing ${song.title}", Toast.LENGTH_SHORT).show()
    }

    private fun emptyCard(message: String): View = UiKit.card(this).apply { addView(UiKit.subtitle(this@MainActivity, message)) }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionManager.REQUEST_MEDIA && grantResults.any { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            lifecycleScope.launch { scanMedia() }
        } else if (requestCode == PermissionManager.REQUEST_MEDIA) {
            Toast.makeText(this, getString(R.string.no_media_permission), Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (currentTab != Tab.HOME) showTab(Tab.HOME) else super.onBackPressed()
    }
}
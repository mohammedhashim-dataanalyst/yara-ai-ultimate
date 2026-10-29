package com.yara.ai.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.yara.ai.ui.MainActivity

/**
 * Background voice assistant. Keeps listening for "Yara" (or "Hey Yara"), then answers by voice
 * and in a floating bubble over any other app. Uses Android's built-in speech recognizer.
 */
class YaraService : Service() {
    companion object {
        const val ACTION_STOP = "com.yara.ai.STOP"
        const val ACTION_NEW = "com.yara.ai.NEW_MESSAGE"
        private const val CHANNEL = "yara_background"
        private const val NOTE_ID = 1
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var brain: Brain
    private lateinit var speaker: Speaker
    private var recognizer: SpeechRecognizer? = null
    private var alive = false
    private var busy = false
    private var bubble: TextView? = null
    private val hideBubble = Runnable { removeBubble() }
    private val wakeRe = Regex("(hey |hi |ok )?(yara|yaara|യാര|യാരാ)", RegexOption.IGNORE_CASE)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        brain = Brain(this)
        speaker = Speaker(this, prefs)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.wakeEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Perm.hasMic(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            val n = buildNote("Say \u201cYara\u201d to talk to me, anywhere \uD83D\uDC95")
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTE_ID, n)
        } catch (e: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        if (!alive) {
            alive = true
            prefs.wakeEnabled = true
            listen(false, 0)
        }
        return START_STICKY
    }

    // ---------- notification ----------
    private fun buildNote(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Yara background listening", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, YaraService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Yara is listening")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    private fun updateNote(text: String) {
        try { getSystemService(NotificationManager::class.java).notify(NOTE_ID, buildNote(text)) } catch (_: Exception) {}
    }

    // ---------- listening loop ----------
    private fun listen(awaitingCommand: Boolean, delayMs: Long) {
        handler.postDelayed({ startRecognizer(awaitingCommand) }, delayMs)
    }

    private fun startRecognizer(awaiting: Boolean) {
        if (!alive || busy) return
        releaseRecognizer()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNote("Speech recognition is not available on this phone. Install/enable the Google app.")
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                if (!alive) return
                when {
                    error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { stopSelf() }
                    awaiting -> listen(false, 400)
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> listen(false, 1500)
                    else -> listen(false, 350)
                }
            }

            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                onHeard(list, awaiting)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (awaiting) prefs.speechTag else "en-IN")
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        r.startListening(i)
    }

    private fun onHeard(list: List<String>, awaiting: Boolean) {
        if (awaiting) {
            val cmd = list.firstOrNull()?.trim()
            if (cmd.isNullOrBlank()) listen(false, 300) else runCommand(cmd)
            return
        }
        for (s in list) {
            val m = wakeRe.find(s) ?: continue
            val cmd = s.substring(m.range.last + 1).trim(' ', ',', '.', '!')
            if (cmd.length >= 2) {
                runCommand(cmd)
            } else {
                busy = true
                showBubble("\uD83D\uDC95 Yes? I'm listening...")
                say("Yes?") { busy = false; listen(true, 100) }
            }
            return
        }
        listen(false, 200)
    }

    private fun runCommand(cmd: String) {
        busy = true
        releaseRecognizer()
        showBubble("\uD83C\uDFA4 $cmd")
        Thread {
            val r = brain.reply(cmd)
            handler.post {
                if (!alive) return@post
                showBubble(r.text)
                updateNote(r.text)
                sendBroadcast(Intent(ACTION_NEW).setPackage(packageName))
                say(r.text) { busy = false; listen(false, 300) }
            }
        }.start()
    }

    /** Speaks and calls [done] exactly once on the main thread (with a safety timeout). */
    private fun say(text: String, done: () -> Unit) {
        var called = false
        val once = Runnable { if (!called) { called = true; done() } }
        handler.postDelayed(once, 5000L + text.length * 110L)
        speaker.speak(text) { handler.post { handler.removeCallbacks(once); once.run() } }
    }

    private fun releaseRecognizer() {
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    // ---------- floating bubble over other apps ----------
    private fun showBubble(text: String) {
        if (!Settings.canDrawOverlays(this)) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            var tv = bubble
            if (tv == null) {
                tv = TextView(this)
                tv.setTextColor(Color.WHITE)
                tv.textSize = 15f
                val pad = (14 * resources.displayMetrics.density).toInt()
                tv.setPadding(pad, pad, pad, pad)
                val bg = GradientDrawable()
                bg.setColor(Color.parseColor("#E6FF4B5C"))
                bg.cornerRadius = 26f * resources.displayMetrics.density
                tv.background = bg
                tv.maxWidth = (resources.displayMetrics.widthPixels * 0.85f).toInt()
                tv.setOnClickListener { removeBubble() }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                lp.y = (90 * resources.displayMetrics.density).toInt()
                wm.addView(tv, lp)
                bubble = tv
            }
            tv.text = text
            handler.removeCallbacks(hideBubble)
            handler.postDelayed(hideBubble, 3500L + text.length * 90L)
        } catch (_: Exception) {
            bubble = null
        }
    }

    private fun removeBubble() {
        handler.removeCallbacks(hideBubble)
        val tv = bubble ?: return
        try { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(tv) } catch (_: Exception) {}
        bubble = null
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        removeBubble()
        speaker.shutdown()
        super.onDestroy()
    }
}

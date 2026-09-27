package io.github.rehmut.sketchandthrow

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.widget.ScrollView
import android.widget.TextView

/**
 * The about part of settings: version, open-source licences and the privacy
 * policy. Apache 2.0 asks that its text travel with the software, so it ships
 * as an asset and is shown in full.
 */
internal class AboutSection(private val activity: Activity, root: View, versionName: String) {
    init {
        root.findViewById<TextView>(R.id.versionText).text = "v$versionName"
        root.findViewById<View>(R.id.licencesButton).setOnClickListener { showLicences() }
        root.findViewById<View>(R.id.privacyButton).setOnClickListener { openPrivacyPolicy() }
    }

    private fun showLicences() {
        val apache = try {
            activity.assets.open(APACHE_LICENCE).bufferedReader().use { it.readText() }
        } catch (error: java.io.IOException) {
            "The licence text is at https://www.apache.org/licenses/LICENSE-2.0"
        }
        val density = activity.resources.displayMetrics.density
        val text = TextView(activity).apply {
            text = COMPONENTS + "\n\n" + apache
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(activity.getColor(R.color.graphite))
            val pad = (20 * density).toInt()
            setPadding(pad, pad / 2, pad, pad)
            setTextIsSelectable(true)
        }
        AlertDialog.Builder(activity).setTitle("Open-source licences")
            .setView(ScrollView(activity).apply { addView(text) })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun openPrivacyPolicy() {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse(activity.getString(R.string.privacy_policy_url))))
        } catch (error: ActivityNotFoundException) {
            AlertDialog.Builder(activity).setTitle("Privacy policy")
                .setMessage(activity.getString(R.string.privacy_policy_url))
                .setPositiveButton("Close", null).show()
        }
    }

    private companion object {
        const val APACHE_LICENCE = "licenses/apache-2.0.txt"
        val COMPONENTS = """
            This app includes:

            MediaPipe Tasks Vision and the MagicTouch
            interactive segmentation model
              Apache License 2.0 · Google LLC

            AndroidX Activity and CameraX
              Apache License 2.0 · The Android Open Source Project

            Kotlin standard library
              Apache License 2.0 · JetBrains s.r.o.

            ML Kit Object Detection
              ML Kit Terms of Service
              https://developers.google.com/ml-kit/terms

            The Apache License 2.0 follows in full.
        """.trimIndent()
    }
}

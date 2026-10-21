package com.lucabol.onelinejournal

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lucabol.onelinejournal.reminders.Reminders
import com.lucabol.onelinejournal.ui.JournalApp
import com.lucabol.onelinejournal.ui.OneLineTheme
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class MainActivity : FragmentActivity() {
    private val model: JournalViewModel by viewModels()
    private var locked by mutableStateOf(true)
    private var lockEnabled = false
    private var settingsLoaded by mutableStateOf(false)
    private var unlockError by mutableStateOf<String?>(null)
    private var authenticating = false
    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openNotificationDate(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    model.graph.settings.flow.collect { settings ->
                        lockEnabled = settings.appLock
                        settingsLoaded = true
                        if (!lockEnabled) locked = false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    settingsLoaded = false
                    unlockError = "Privacy settings could not be loaded. Restart the app; do not clear its data without a backup."
                    model.reportFailure(e)
                }
            }
        }
        setContent {
            OneLineTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!settingsLoaded || locked) {
                        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                            Text("Your journal is private.", style = MaterialTheme.typography.headlineSmall)
                            if (settingsLoaded) Button(onClick = ::unlock) { Text("Unlock journal") }
                            unlockError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    } else {
                        JournalApp(model, canUseLock = {
                            BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
                        })
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.graph.reminders.createChannel()
    }

    override fun onStop() {
        if (lockEnabled) locked = true
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotificationDate(intent)
    }

    private fun openNotificationDate(intent: Intent?) {
        val raw = intent?.getStringExtra(Reminders.DATE) ?: return
        try {
            model.requestDate(LocalDate.parse(raw))
        } catch (_: java.time.format.DateTimeParseException) {
            model.message("The reminder date was invalid. Showing the current journal date instead.")
        }
        intent.removeExtra(Reminders.DATE)
    }

    private fun unlock() {
        if (authenticating) return
        authenticating = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    locked = false
                    unlockError = null
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                    unlockError = errString.toString()
                }
                override fun onAuthenticationFailed() { unlockError = "Not recognized. Try again or use your device credential." }
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock One Line Journal")
            .setSubtitle("Use your device's biometric or screen lock.")
            .setAllowedAuthenticators(authenticators).build())
    }
}

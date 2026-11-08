package app.playnest

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.playnest.ui.App
import app.playnest.ui.PlayNestTheme
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()
    private var controller: ListenableFuture<MediaController>? = null
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        // Connecting a controller binds PlaybackService so playback keeps going in the background.
        controller = MediaController.Builder(
            this, SessionToken(this, ComponentName(this, PlaybackService::class.java)),
        ).buildAsync()

        if (savedInstanceState == null) handleIntent(intent)
        setContent { PlayNestTheme { App(vm, this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val uri = i?.data ?: return
        val host = uri.host ?: return
        if (host.contains("youtube.com") || host == "youtu.be") vm.openLink(uri.toString())
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val c = vm.ctl
        if (Build.VERSION.SDK_INT >= 26 && c.current != null && c.expanded && c.player.isPlaying) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.ctl.inPip = isInPictureInPictureMode
    }

    override fun onDestroy() {
        controller?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

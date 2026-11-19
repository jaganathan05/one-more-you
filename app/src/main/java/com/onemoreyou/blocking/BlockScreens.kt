package com.onemoreyou.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemoreyou.ui.OneMoreYouTheme
import com.onemoreyou.ui.overlay.BlockingOverlay
import kotlinx.coroutines.flow.MutableStateFlow

object BlockScreens {
    val state = MutableStateFlow<BlockScreenState?>(null)
    var onConfirm: () -> Unit = {}
    var onLeave: () -> Unit = {}

    @Volatile
    var visible: Boolean = false

    fun show(context: Context, screen: BlockScreenState, confirm: () -> Unit, leave: () -> Unit) {
        onConfirm = confirm
        onLeave = leave
        state.value = screen
        if (visible) return
        val intent = Intent(context, BlockActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        context.startActivity(intent)
    }

    fun dismiss() {
        state.value = null
    }
}

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val screen by BlockScreens.state.collectAsStateWithLifecycle()
            LaunchedEffect(screen) {
                if (screen == null) finish()
            }
            val current = screen ?: return@setContent
            OneMoreYouTheme {
                BlockingOverlay(
                    state = current,
                    onConfirmPass = { BlockScreens.onConfirm() },
                    onLeave = { BlockScreens.onLeave() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        BlockScreens.visible = true
    }

    override fun onStop() {
        BlockScreens.visible = false
        super.onStop()
    }
}

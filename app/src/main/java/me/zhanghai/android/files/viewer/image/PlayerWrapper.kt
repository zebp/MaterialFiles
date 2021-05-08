package me.zhanghai.android.files.viewer.image

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer

class PlayerWrapper {
    var player: SimpleExoPlayer? = null

    fun create(context: Context, uri: Uri) {
        this.player?.release()
        val player = SimpleExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }

        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()

        this.player = player
    }

    fun resume() {
        this.player?.apply {
            playWhenReady = true
            prepare()
            play()
        }
    }

    fun pause() {
        this.player?.pause()
    }

    fun release() {
        this.player?.release()
    }
}
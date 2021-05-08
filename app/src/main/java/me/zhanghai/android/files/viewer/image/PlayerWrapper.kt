package me.zhanghai.android.files.viewer.image

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer

class PlayerWrapper {
    /**
     * If the player should be marked to play when ready, this prevents a logical data race where
     * the video tapped on doesn't auto play.
     */
    var autoPlayWhenReady = false
    var player: SimpleExoPlayer? = null

    fun create(context: Context, uri: Uri) {
        this.player?.release()
        val player = SimpleExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }

        player.apply {
            if (autoPlayWhenReady) {
                playWhenReady = autoPlayWhenReady
            }

            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }

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

    fun markForInitialAutoPlay() {
        if (this.player == null) autoPlayWhenReady = true
    }
}
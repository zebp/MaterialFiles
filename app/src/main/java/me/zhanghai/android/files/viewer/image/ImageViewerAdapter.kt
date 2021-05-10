/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.image

import android.graphics.BitmapFactory
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil.clear
import coil.loadAny
import coil.size.OriginalSize
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.DefaultOnImageEventListener
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player.REPEAT_MODE_ALL
import com.google.android.exoplayer2.SimpleExoPlayer
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import me.zhanghai.android.files.coil.fadeIn
import me.zhanghai.android.files.databinding.ImageViewerItemBinding
import me.zhanghai.android.files.file.*
import me.zhanghai.android.files.provider.common.AndroidFileTypeDetector
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.readAttributes
import me.zhanghai.android.files.ui.SimpleAdapter
import me.zhanghai.android.files.util.*
import kotlin.math.max

class ImageViewerAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val listener: (View) -> Unit
) : SimpleAdapter<ImageViewerAdapter.ViewerItem, ImageViewerAdapter.ViewHolder>() {
    override val hasStableIds: Boolean
        get() = true

    override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ImageViewerItemBinding.inflate(parent.context.layoutInflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding
        binding.image.setOnPhotoTapListener { view, _, _ -> listener(view) }
        binding.gif.setOnClickListener(listener)
        binding.video.setOnClickListener(listener)
        binding.largeImage.setOnClickListener(listener)
        loadImage(binding, item)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)

        val binding = holder.binding
        binding.image.clear()
        binding.gif.clear()
        binding.largeImage.recycle()
        binding.video.player?.release()
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        val item = getItem(holder.bindingAdapterPosition)
        item.player.resume()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        val item = getItem(holder.bindingAdapterPosition)
        item.player.pause()
    }

    private fun loadImage(binding: ImageViewerItemBinding, item: ViewerItem) {
        binding.progress.fadeInUnsafe(true)
        binding.errorText.fadeOutUnsafe()
        binding.gif.isVisible = false
        binding.video.isVisible = false
        binding.image.isVisible = false
        binding.largeImage.isVisible = false
        lifecycleOwner.lifecycleScope.launch {
            val imageInfo = try {
                withContext(Dispatchers.IO) { item.path.loadImageInfo() }
            } catch (e: Exception) {
                e.printStackTrace()
                showError(binding, e)
                return@launch
            }
            loadImageWithInfo(binding, item, imageInfo)
        }
    }

    private fun Path.loadImageInfo(): ImageInfo {
        val attributes = readAttributes(BasicFileAttributes::class.java)
        val mimeType = AndroidFileTypeDetector.getMimeType(this, attributes).asMimeType()
        val bitmapOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        newInputStream().use { BitmapFactory.decodeStream(it, null, bitmapOptions) }
        return ImageInfo(
            attributes, bitmapOptions.outWidth, bitmapOptions.outHeight,
            bitmapOptions.outMimeType?.asMimeTypeOrNull() ?: mimeType
        )
    }

    private fun loadImageWithInfo(
        binding: ImageViewerItemBinding,
        item: ViewerItem,
        imageInfo: ImageInfo
    ) {
        when {
            imageInfo.mimeType.value == "image/gif" -> {
                binding.gif.apply {
                    isVisible = true
                    loadAny(item.path to imageInfo.attributes) {
                        size(OriginalSize)
                        fadeIn(context.shortAnimTime)
                        listener(
                            onSuccess = { _, _ -> binding.progress.fadeOutUnsafe() },
                            onError = { _, e -> showError(binding, e) }
                        )
                    }
                }
            }
            imageInfo.mimeType.isVideo -> {
                item.player.create(binding.video.context, item.path.fileProviderUri)
                binding.video.isVisible = true
                binding.video.player = item.player.player
            }
            imageInfo.shouldUseLargeImageView -> {
                binding.image.apply {
                    isVisible = true
                    loadAny(item.path to imageInfo.attributes) {
                        size(OriginalSize)
                        fadeIn(context.shortAnimTime)
                        listener(
                            onSuccess = { _, _ -> binding.progress.fadeOutUnsafe() },
                            onError = { _, e -> showError(binding, e) }
                        )
                    }
                }
            }
            else -> {
                binding.largeImage.apply {
                    setDoubleTapZoomDuration(300)
                    orientation = SubsamplingScaleImageView.ORIENTATION_USE_EXIF
                    // Otherwise OnImageEventListener.onReady() is never called.
                    isVisible = true
                    alpha = 0f
                    setOnImageEventListener(object : DefaultOnImageEventListener() {
                        override fun onReady() {
                            setDoubleTapZoomScale(binding.largeImage.cropScale)
                            binding.progress.fadeOutUnsafe()
                            binding.largeImage.fadeInUnsafe(true)
                        }

                        override fun onImageLoadError(e: Exception) {
                            e.printStackTrace()
                            showError(binding, e)
                        }
                    })
                    setImageRestoringSavedState(ImageSource.uri(item.path.fileProviderUri))
                }
            }
        }
    }

    private val ImageInfo.shouldUseLargeImageView: Boolean
        get() {
            // See BitmapFactory.cpp encodedFormatToString()
            if (mimeType == MimeType.IMAGE_GIF) {
                return false
            }
            if (width <= 0 || height <= 0) {
                return false
            }
            // 4 bytes per pixel for ARGB_8888.
            if (width * height * 4 > MAX_BITMAP_SIZE) {
                return true
            }
            if (width > 2048 || height > 2048) {
                val ratio = width.toFloat() / height
                if (ratio < 0.5 || ratio > 2) {
                    return true
                }
            }
            return false
        }

    private val SubsamplingScaleImageView.cropScale: Float
        get() {
            val viewWidth = (width - paddingLeft - paddingRight)
            val viewHeight = (height - paddingTop - paddingBottom)
            val orientation = appliedOrientation
            val rotated90Or270 = orientation == SubsamplingScaleImageView.ORIENTATION_90
                || orientation == SubsamplingScaleImageView.ORIENTATION_270
            val imageWidth = if (rotated90Or270) sHeight else sWidth
            val imageHeight = if (rotated90Or270) sWidth else sHeight
            return max(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
        }

    private fun showError(binding: ImageViewerItemBinding, throwable: Throwable) {
        binding.progress.fadeOutUnsafe()
        binding.errorText.text = throwable.toString()
        binding.errorText.fadeInUnsafe(true)
        binding.image.isVisible = false
        binding.largeImage.isVisible = false
    }

    companion object {
        // @see android.graphics.RecordingCanvas#MAX_BITMAP_SIZE
        private const val MAX_BITMAP_SIZE = 100 * 1024 * 1024
    }

    @Parcelize
    class ViewerItem(val path: @RawValue Path, var player: PlayerWrapper) : Parcelable

    class ViewHolder(val binding: ImageViewerItemBinding) : RecyclerView.ViewHolder(binding.root)

    private class ImageInfo(
        val attributes: BasicFileAttributes,
        val width: Int,
        val height: Int,
        val mimeType: MimeType
    )
}

/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import java8.nio.file.Path
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.valueCompat

class FileListActivity : AppActivity() {
    private lateinit var fragment: FileDisplayFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)

        var prev: Boolean = Settings.FILE_LIST_USE_GRID_VIEW.valueCompat
        Settings.FILE_LIST_USE_GRID_VIEW.observeForever {
            if (it == prev) {
                return@observeForever
            }

            prev = it

            supportFragmentManager.commit {
                remove(fragment)
            }

            if (it == true) {
                if (savedInstanceState == null) {
                    fragment = FileGridFragment().putArgs(FileGridFragment.Args(intent))
                    supportFragmentManager.commit { add(android.R.id.content, fragment) }
                } else {
                    fragment = supportFragmentManager.findFragmentById(android.R.id.content)
                            as FileGridFragment
                }
            } else {
                if (savedInstanceState == null) {
                    fragment = FileListFragment().putArgs(FileListFragment.Args(intent))
                    supportFragmentManager.commit { add(android.R.id.content, fragment) }
                } else {
                    fragment = supportFragmentManager.findFragmentById(android.R.id.content)
                            as FileListFragment
                }
            }
        }

        if (Settings.FILE_LIST_USE_GRID_VIEW.valueCompat) {
            if (savedInstanceState == null) {
                fragment = FileGridFragment().putArgs(FileGridFragment.Args(intent))
                supportFragmentManager.commit { add(android.R.id.content, fragment) }
            } else {
                fragment = supportFragmentManager.findFragmentById(android.R.id.content)
                        as FileGridFragment
            }
        } else {
            if (savedInstanceState == null) {
                fragment = FileListFragment().putArgs(FileListFragment.Args(intent))
                supportFragmentManager.commit { add(android.R.id.content, fragment) }
            } else {
                fragment = supportFragmentManager.findFragmentById(android.R.id.content)
                        as FileListFragment
            }
        }
    }

    override fun onBackPressed() {
        if (fragment.onBackPressed()) {
            return
        }
        super.onBackPressed()
    }

    companion object {
        fun createViewIntent(path: Path): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_VIEW)
                .apply { extraPath = path }
    }

    class PickDirectoryContract : ActivityResultContract<Path?, Path?>() {
        override fun createIntent(context: Context, input: Path?): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .apply { input?.let { extraPath = it } }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class PickFileContract : ActivityResultContract<List<MimeType>, Path?>() {
        override fun createIntent(context: Context, input: List<MimeType>): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT)
                .setType(MimeType.ANY.value)
                .putExtra(Intent.EXTRA_MIME_TYPES, input.map { it.value }.toTypedArray())

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    abstract class FileDisplayFragment : Fragment() {
        abstract fun onBackPressed(): Boolean
    }
}

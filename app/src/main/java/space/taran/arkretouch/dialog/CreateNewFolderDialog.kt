package space.taran.arkretouch.dialog

import android.view.View
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.createAndroidSAFDirectory
import com.simplemobiletools.commons.extensions.getDocumentFile
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.getParentPath
import com.simplemobiletools.commons.extensions.humanizePath
import com.simplemobiletools.commons.extensions.isAValidFilename
import com.simplemobiletools.commons.extensions.isRestrictedSAFOnlyRoot
import com.simplemobiletools.commons.extensions.needsStupidWritePermissions
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.extensions.showKeyboard
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.value
import space.taran.arkretouch.R
import space.taran.arkretouch.internal.BaseActivity
import java.io.File
import kotlinx.android.synthetic.main.dialog_create_new_folder.view.folder_name
import kotlinx.android.synthetic.main.dialog_create_new_folder.view.folder_path

class CreateNewFolderDialog(
    val activity: BaseActivity,
    val path: String,
    val callback: (path: String) -> Unit
) {
    init {
        val view =
            activity.layoutInflater.inflate(R.layout.dialog_create_new_folder, null)
        view.folder_path.text = "${activity.humanizePath(path).trimEnd('/')}/"

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.create_new_folder) {
                    showKeyboard(view.folder_name)
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        View.OnClickListener {
                            val name = view.folder_name.value
                            when {
                                name.isEmpty() -> activity.toast(R.string.empty_name)
                                name.isAValidFilename() -> {
                                    val file = File(path, name)
                                    if (file.exists()) {
                                        activity.toast(R.string.name_taken)
                                        return@OnClickListener
                                    }

                                    createFolder(
                                        "$path/$name",
                                        this
                                    )
                                }
                                else -> activity.toast(
                                    R.string.invalid_name
                                )
                            }
                        }
                    )
                }
            }
    }

    private fun createFolder(path: String, alertDialog: AlertDialog) {
        try {
            when {
                activity.isRestrictedSAFOnlyRoot(path) &&
                    activity.createAndroidSAFDirectory(
                        path
                    ) -> sendSuccess(
                    alertDialog,
                    path
                )
                activity.needsStupidWritePermissions(path) ->
                    activity.handleSAFDialog(
                        path
                    ) {
                        if (it) {
                            try {
                                val documentFile =
                                    activity.getDocumentFile(path.getParentPath())
                                val newDir = documentFile?.createDirectory(
                                    path.getFilenameFromPath()
                                )
                                    ?: activity.getDocumentFile(path)
                                if (newDir != null) {
                                    sendSuccess(alertDialog, path)
                                } else {
                                    activity.toast(R.string.unknown_error_occurred)
                                }
                            } catch (e: SecurityException) {
                                activity.showErrorToast(e)
                            }
                        }
                    }
                File(path).mkdirs() -> sendSuccess(alertDialog, path)
                else -> activity.toast(R.string.unknown_error_occurred)
            }
        } catch (e: Exception) {
            activity.showErrorToast(e)
        }
    }

    private fun sendSuccess(alertDialog: AlertDialog, path: String) {
        callback(path.trimEnd('/'))
        alertDialog.dismiss()
    }
}

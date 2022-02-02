package space.taran.arkretouch.dialog

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import kotlinx.android.synthetic.main.dialog_save_as.view.*
import space.taran.arkretouch.R
import space.taran.arkretouch.internal.BaseActivity

class SaveAsDialog(
    val activity: BaseActivity,
    val path: String,
    val appendFilename: Boolean,
    val cancelCallback: (() -> Unit)? = null,
    val callback: (savePath: String) -> Unit
) {

    private var filenameSuffix: String = if (appendFilename) "_1" else ""

    init {
        var realPath = path.getParentPath()

        val view = activity.layoutInflater.inflate(R.layout.dialog_save_as, null).apply {
            save_as_path.text = "${activity.humanizePath(realPath).trimEnd('/')}/"

            val fullName = path.getFilenameFromPath()
            val dotAt = fullName.lastIndexOf(".")
            var name = fullName

            if (dotAt > 0) {
                name = fullName.substring(0, dotAt)
                val extension = fullName.substring(dotAt + 1)
                save_as_extension.setText(extension)
            }

            overwrite.setOnCheckedChangeListener { _, isChecked ->
                filenameSuffix = if (isChecked) "" else "_1"
                save_as_name.setText(name + filenameSuffix)
            }

            save_as_name.setText(name + filenameSuffix)
            save_as_path.setOnClickListener {
                activity.hideKeyboard(save_as_path)
                FilePickerDialog(activity, realPath, false, false, true, false) {
                    save_as_path.text = activity.humanizePath(it)
                    realPath = it
                }
            }
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel) { dialog, which -> cancelCallback?.invoke() }
            .setOnCancelListener { cancelCallback?.invoke() }
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.save_as) {
                    showKeyboard(view.save_as_name)
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = view.save_as_name.value
                        val extension = view.save_as_extension.value

                        if (filename.isEmpty()) {
                            activity.toast(R.string.filename_cannot_be_empty)
                            return@setOnClickListener
                        }

                        if (extension.isEmpty()) {
                            activity.toast(R.string.extension_cannot_be_empty)
                            return@setOnClickListener
                        }

                        val newFilename = "$filename.$extension"
                        val newPath = "${realPath.trimEnd('/')}/$newFilename"
                        if (!newFilename.isAValidFilename()) {
                            activity.toast(R.string.filename_invalid_characters)
                            return@setOnClickListener
                        }

                        if (activity.getDoesFilePathExist(newPath) && !view.overwrite.isChecked) {
                            val title = String.format(
                                activity.getString(R.string.file_already_exists_overwrite),
                                newFilename
                            )
                            ConfirmationDialog(activity, title) {
                                callback(newPath)
                                dismiss()
                            }
                        } else {
                            callback(newPath)
                            dismiss()
                        }
                    }
                }
            }
    }
}

package space.taran.arkretouch.dialog

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.LifecycleOwner
import by.kirich1409.viewbindingdelegate.viewBinding
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.getDoesFilePathExist
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.getParentPath
import com.simplemobiletools.commons.extensions.hideKeyboard
import com.simplemobiletools.commons.extensions.humanizePath
import com.simplemobiletools.commons.extensions.isAValidFilename
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.value
import space.taran.arkfilepicker.ArkFilePickerConfig
import space.taran.arkfilepicker.ArkFilePickerFragment
import space.taran.arkfilepicker.ArkFilePickerMode
import space.taran.arkfilepicker.onArkPathPicked
import space.taran.arkretouch.R
import space.taran.arkretouch.databinding.DialogSaveAsBinding
import space.taran.arkretouch.internal.BaseActivity
import space.taran.arkretouch.utils.args
import kotlin.io.path.Path

class SaveAsDialogFragment : DialogFragment(R.layout.dialog_save_as) {
    private val binding by viewBinding(DialogSaveAsBinding::bind)
    private var folderPath by args<String?>()
    private var path by args<String>()
    private var appendFilename by args<Boolean>()
    private lateinit var filenameSuffix: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUI()
    }

    private fun initUI() = binding.apply {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val activity = requireActivity()

        var realPath = folderPath ?: path!!.getParentPath()
        filenameSuffix = if (appendFilename!!) "_1" else ""

        saveAsPath.text = "${activity.humanizePath(realPath).trimEnd('/')}/"

        val fullName = path!!.getFilenameFromPath()
        val dotAt = fullName.lastIndexOf(".")
        var name = fullName

        if (dotAt > 0) {
            name = fullName.substring(0, dotAt)
            val extension = fullName.substring(dotAt + 1)
            saveAsExtension.setText(extension)
        }

        overwrite.setOnCheckedChangeListener { _, isChecked ->
            filenameSuffix = if (isChecked) "" else "_1"
            saveAsName.setText(name + filenameSuffix)
        }

        saveAsName.setText(name + filenameSuffix)
        saveAsPath.setOnClickListener {
            activity.hideKeyboard(saveAsPath)
            ArkFilePickerFragment
                .newInstance(getFilePickerConfig(realPath))
                .show(childFragmentManager, null)

            childFragmentManager.onArkPathPicked(activity) {
                saveAsPath.text = activity.humanizePath(it.toString())
                realPath = it.toString()
            }
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnOk.setOnClickListener {
            val filename = saveAsName.value
            val extension = saveAsExtension.value

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

            if (activity.getDoesFilePathExist(newPath) && !overwrite.isChecked) {
                val title = String.format(
                    activity.getString(R.string.file_already_exists_overwrite),
                    newFilename
                )
                ConfirmationDialog(activity, title) {
                    setResult(newPath)
                    dismiss()
                }
            } else {
                setResult(newPath)
                dismiss()
            }
        }
    }

    private fun setResult(path: String) = setFragmentResult(
        SAVE_AS_REQUEST_KEY,
        Bundle().apply {
            putString(
                PICKED_PATH_BUNDLE_KEY,
                path
            )
        }
    )

    private fun getFilePickerConfig(initPath: String) = ArkFilePickerConfig(
        initialPath = Path(initPath),
        mode = ArkFilePickerMode.FOLDER,
        titleStringId = R.string.pick_folder
    )

    companion object {
        const val SAVE_AS_REQUEST_KEY = "saveAs"
        const val PICKED_PATH_BUNDLE_KEY = "pickedPath"

        fun newInstance(
            folderPath: String?,
            path: String,
            appendFilename: Boolean,
        ) = SaveAsDialogFragment().apply {
            this.folderPath = folderPath
            this.path = path
            this.appendFilename = appendFilename
        }

        fun newInstanceAndShow(
            activity: BaseActivity,
            folderPath: String?,
            path: String,
            appendFilename: Boolean,
            listener: (String) -> Unit
        ) {
            activity.supportFragmentManager.onSaveAsConfirmed(activity) {
                listener(it)
            }
            newInstance(folderPath, path, appendFilename).show(
                activity.supportFragmentManager,
                null
            )
        }
    }
}

fun FragmentManager.onSaveAsConfirmed(
    lifecycleOwner: LifecycleOwner,
    listener: (String) -> Unit
) {
    setFragmentResultListener(
        SaveAsDialogFragment.SAVE_AS_REQUEST_KEY,
        lifecycleOwner
    ) { _, bundle ->
        listener(
            bundle.getString(SaveAsDialogFragment.PICKED_PATH_BUNDLE_KEY)!!
        )
    }
}


package space.taran.arkretouch.dialog

import android.os.Environment
import android.os.Parcelable
import android.view.KeyEvent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.simplemobiletools.commons.R
import com.simplemobiletools.commons.extensions.areSystemAnimationsEnabled
import com.simplemobiletools.commons.extensions.baseConfig
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.beVisible
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getAdjustedPrimaryColor
import com.simplemobiletools.commons.extensions.getAndroidSAFFileItems
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.getContrastColor
import com.simplemobiletools.commons.extensions.getDirectChildrenCount
import com.simplemobiletools.commons.extensions.getDoesFilePathExist
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.getFolderLastModifieds
import com.simplemobiletools.commons.extensions.getIsPathDirectory
import com.simplemobiletools.commons.extensions.getOTGItems
import com.simplemobiletools.commons.extensions.getParentPath
import com.simplemobiletools.commons.extensions.getSomeAndroidSAFDocument
import com.simplemobiletools.commons.extensions.getSomeDocumentFile
import com.simplemobiletools.commons.extensions.getTextSize
import com.simplemobiletools.commons.extensions.handleHiddenFolderPasswordProtection
import com.simplemobiletools.commons.extensions.handleLockedFolderOpening
import com.simplemobiletools.commons.extensions.internalStoragePath
import com.simplemobiletools.commons.extensions.isPathOnOTG
import com.simplemobiletools.commons.extensions.isRestrictedSAFOnlyRoot
import com.simplemobiletools.commons.extensions.isVisible
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.views.Breadcrumbs
import space.taran.arkretouch.internal.BaseActivity
import java.io.File
import kotlinx.android.synthetic.main.dialog_filepicker.view.filepicker_breadcrumbs
import kotlinx.android.synthetic.main.dialog_filepicker.view.filepicker_fab
import kotlinx.android.synthetic.main.dialog_filepicker.view.filepicker_fab_show_favorites
import kotlinx.android.synthetic.main.dialog_filepicker.view.filepicker_fab_show_hidden
import kotlinx.android.synthetic.main.dialog_filepicker.view.filepicker_fabs_holder
import kotlinx.android.synthetic.main.dialog_filepicker.view.filepicker_fastscroller
import kotlinx.android.synthetic.main.dialog_filepicker.view.filepicker_favorites_holder
import kotlinx.android.synthetic.main.dialog_filepicker.view.filepicker_favorites_label
import kotlinx.android.synthetic.main.dialog_filepicker.view.filepicker_files_holder
import kotlinx.android.synthetic.main.dialog_filepicker.view.filepicker_list

/**
 * The only filepicker constructor with a couple optional parameters
 *
 * @param activity has to be activity to avoid some Theme.AppCompat issues
 * @param currPath initial path of the dialog, defaults to the external storage
 * @param pickFile toggle used to determine if we are picking a file or a folder
 * @param showHidden toggle for showing hidden items, whose name starts with a dot
 * @param showFAB toggle the displaying of a Floating Action Button for creating new folders
 * @param callback the callback used for returning the selected file/folder
 */
class FilePickerDialog(
    val activity: BaseActivity,
    var currPath: String = Environment.getExternalStorageDirectory().toString(),
    val pickFile: Boolean = true,
    var showHidden: Boolean = false,
    val showFAB: Boolean = false,
    val canAddShowHiddenButton: Boolean = false,
    val forceShowRoot: Boolean = false,
    val showFavoritesButton: Boolean = false,
    val callback: (pickedPath: String) -> Unit
) : Breadcrumbs.BreadcrumbsListener {

    private var mFirstUpdate = true
    private var mPrevPath = ""
    private var mScrollStates = HashMap<String, Parcelable>()

    private lateinit var mDialog: AlertDialog
    private var mDialogView =
        activity.layoutInflater.inflate(R.layout.dialog_filepicker, null)

    val listOfImageMIMEType = listOf(
        "image/gif",
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/svg+xml",
        "image/webp",
        "image/svg+xml",
        "image/vnd.wap.wbmp",
        "image/vnd.nok-wallpaper"
    )

    init {
        if (!activity.getDoesFilePathExist(currPath)) {
            currPath = activity.internalStoragePath
        }

        if (!activity.getIsPathDirectory(currPath)) {
            currPath = currPath.getParentPath()
        }

        // do not allow copying files in the recycle bin manually
        if (currPath.startsWith(activity.filesDir.absolutePath)) {
            currPath = activity.internalStoragePath
        }

        mDialogView.filepicker_breadcrumbs.apply {
            listener = this@FilePickerDialog
            updateFontSize(activity.getTextSize())
        }

        tryUpdateItems()
        // TODO favorites
//        setupFavorites()

        val builder = AlertDialog.Builder(activity)
            .setNegativeButton(R.string.cancel, null)
            .setOnKeyListener { dialogInterface, i, keyEvent ->
                if (keyEvent.action == KeyEvent.ACTION_UP &&
                    i == KeyEvent.KEYCODE_BACK
                ) {
                    val breadcrumbs = mDialogView.filepicker_breadcrumbs
                    if (breadcrumbs.itemsCount > 1) {
                        breadcrumbs.removeBreadcrumb()
                        currPath = breadcrumbs.getLastItem().path.trimEnd('/')
                        tryUpdateItems()
                    } else {
                        mDialog.dismiss()
                    }
                }
                true
            }

        if (!pickFile) {
            builder.setPositiveButton(R.string.ok, null)
        }

        if (showFAB) {
            mDialogView.filepicker_fab.apply {
                beVisible()
                setOnClickListener { createNewFolder() }
            }
        }

        val secondaryFabBottomMargin = activity.resources.getDimension(
            if (showFAB) R.dimen.secondary_fab_bottom_margin
            else R.dimen.activity_margin
        ).toInt()
        mDialogView.filepicker_fabs_holder.apply {
            (layoutParams as CoordinatorLayout.LayoutParams).bottomMargin =
                secondaryFabBottomMargin
        }

        mDialogView.filepicker_fastscroller.updateColors(
            activity.getAdjustedPrimaryColor()
        )
        mDialogView.filepicker_fab_show_hidden.apply {
            beVisibleIf(!showHidden && canAddShowHiddenButton)
            setOnClickListener {
                activity.handleHiddenFolderPasswordProtection {
                    beGone()
                    showHidden = true
                    tryUpdateItems()
                }
            }
        }

        mDialogView.filepicker_favorites_label.text =
            "${activity.getString(R.string.favorites)}:"
        mDialogView.filepicker_fab_show_favorites.apply {
            beVisibleIf(
                showFavoritesButton &&
                    context.baseConfig.favorites.isNotEmpty()
            )
            setOnClickListener {
                if (mDialogView.filepicker_favorites_holder.isVisible()) {
                    hideFavorites()
                } else {
                    showFavorites()
                }
            }
        }

        mDialog = builder.create().apply {
            activity.setupDialogStuff(mDialogView, this, getTitle())
        }

        if (!pickFile) {
            mDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                verifyPath()
            }
        }
    }

    private fun getTitle() =
        if (pickFile) R.string.select_file else R.string.select_folder

    private fun createNewFolder() {
        CreateNewFolderDialog(activity, currPath) {
            callback(it)
            mDialog.dismiss()
        }
    }

    private fun tryUpdateItems() {
        ensureBackgroundThread {
            getItems(currPath) {
                activity.runOnUiThread {
                    updateItems(it as ArrayList<FileDirItem>)
                }
            }
        }
    }

    private fun updateItems(items: ArrayList<FileDirItem>) {
        if (!containsDirectory(items) && !mFirstUpdate && !pickFile && !showFAB) {
            verifyPath()
            return
        }

        val sortedItems = items.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.toLowerCase() })
        )
        val adapter = FilepickerItemsAdapter(
            activity,
            sortedItems,
            mDialogView.filepicker_list
        ) {
            if ((it as FileDirItem).isDirectory) {
                activity.handleLockedFolderOpening(it.path) { success ->
                    if (success) {
                        currPath = it.path
                        tryUpdateItems()
                    }
                }
            } else if (pickFile) {
                currPath = it.path
                verifyPath()
            }
        }

        val layoutManager =
            mDialogView.filepicker_list.layoutManager as LinearLayoutManager
        mScrollStates[mPrevPath.trimEnd('/')] = layoutManager.onSaveInstanceState()!!

        mDialogView.apply {
            filepicker_list.adapter = adapter
            filepicker_breadcrumbs.setBreadcrumb(currPath)

            if (context.areSystemAnimationsEnabled) {
                filepicker_list.scheduleLayoutAnimation()
            }

            layoutManager.onRestoreInstanceState(
                mScrollStates[currPath.trimEnd('/')]
            )
        }

        mFirstUpdate = false
        mPrevPath = currPath
    }

    private fun verifyPath() {
        if (activity.isRestrictedSAFOnlyRoot(currPath)) {
            val document = activity.getSomeAndroidSAFDocument(currPath) ?: return
            if ((pickFile && document.isFile) ||
                (!pickFile && document.isDirectory)
            ) {
                sendSuccess()
            }
        } else if (activity.isPathOnOTG(currPath)) {
            val fileDocument = activity.getSomeDocumentFile(currPath) ?: return
            if ((pickFile && fileDocument.isFile) ||
                (!pickFile && fileDocument.isDirectory)
            ) {
                sendSuccess()
            }
        } else {
            val file = File(currPath)
            if ((pickFile && file.isFile) || (!pickFile && file.isDirectory)) {
                sendSuccess()
            }
        }
    }

    private fun sendSuccess() {
        currPath = if (currPath.length == 1) {
            currPath
        } else {
            currPath.trimEnd('/')
        }
        // Allow only image to be selected
        if (listOfImageMIMEType.contains(getMimeType(currPath))) {
            callback(currPath)
            mDialog.dismiss()
        } else {
            Toast.makeText(activity, "Please select image only!", Toast.LENGTH_SHORT)
                .show()
        }
    }

    // url = file path or whatever suitable URL you want.
    fun getMimeType(url: String?): String? {
        var type: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        }
        return type
    }

    private fun getItems(path: String, callback: (List<FileDirItem>) -> Unit) {
        when {
            activity.isRestrictedSAFOnlyRoot(path) -> {
                activity.handleAndroidSAFDialog(path) {
                    activity.getAndroidSAFFileItems(path, showHidden) {
                        callback(it)
                    }
                }
            }
            activity.isPathOnOTG(path) -> activity.getOTGItems(
                path,
                showHidden,
                false,
                callback
            )
            else -> {
                val lastModifieds = activity.getFolderLastModifieds(path)
                getRegularItems(path, lastModifieds, callback)
            }
        }
    }

    private fun getRegularItems(
        path: String,
        lastModifieds: HashMap<String, Long>,
        callback: (List<FileDirItem>) -> Unit
    ) {
        val items = ArrayList<FileDirItem>()
        val files = File(path).listFiles()?.filterNotNull()
        if (files == null) {
            callback(items)
            return
        }

        for (file in files) {
            if (!showHidden && file.name.startsWith('.')) {
                continue
            }

            val curPath = file.absolutePath
            val curName = curPath.getFilenameFromPath()
            val size = file.length()
            var lastModified = lastModifieds.remove(curPath)
            val isDirectory = if (lastModified != null) false else file.isDirectory
            // we don't actually need the real lastModified that badly, do not check file.lastModified()
            if (lastModified == null) {
                lastModified = 0
            }

            val children = if (isDirectory) file.getDirectChildrenCount(
                activity,
                showHidden
            ) else 0
            items.add(
                FileDirItem(
                    curPath,
                    curName,
                    isDirectory,
                    children,
                    size,
                    lastModified
                )
            )
        }
        callback(items)
    }

    private fun containsDirectory(items: List<FileDirItem>) =
        items.any { it.isDirectory }

    /*private fun setupFavorites() {
        FilepickerFavoritesAdapter(activity, activity.baseConfig.favorites.toMutableList(), mDialogView.filepicker_favorites_list) {
            currPath = it as String
            verifyPath()
        }.apply {
            mDialogView.filepicker_favorites_list.adapter = this
        }
    }*/

    private fun showFavorites() {
        mDialogView.apply {
            filepicker_favorites_holder.beVisible()
            filepicker_files_holder.beGone()
            val drawable = activity.resources.getColoredDrawableWithColor(
                R.drawable.ic_folder_vector,
                activity.getAdjustedPrimaryColor().getContrastColor()
            )
            filepicker_fab_show_favorites.setImageDrawable(drawable)
        }
    }

    private fun hideFavorites() {
        mDialogView.apply {
            filepicker_favorites_holder.beGone()
            filepicker_files_holder.beVisible()
            val drawable = activity.resources.getColoredDrawableWithColor(
                R.drawable.ic_star_vector,
                activity.getAdjustedPrimaryColor().getContrastColor()
            )
            filepicker_fab_show_favorites.setImageDrawable(drawable)
        }
    }

    override fun breadcrumbClicked(id: Int) {
        if (id == 0) {
            StoragePickerDialog(activity, currPath, forceShowRoot, true) {
                currPath = it
                tryUpdateItems()
            }
        } else {
            val item = mDialogView.filepicker_breadcrumbs.getItem(id)
            if (currPath != item.path.trimEnd('/')) {
                currPath = item.path
                tryUpdateItems()
            }
        }
    }
}

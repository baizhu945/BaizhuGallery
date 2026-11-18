package org.fossifymigration.baizhu.gallery.activities

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFileCount
import org.fossify.commons.extensions.getFilePublicUri
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLatestMediaByDateId
import org.fossify.commons.extensions.getLatestMediaId
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperSize
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getStorageDirectories
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.hasAllPermissions
import org.fossify.commons.extensions.hasOTGConnected
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.launchMoreAppsFromUsIntent
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toFileDirItem
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.DAY_SECONDS
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.PERMISSION_READ_STORAGE
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.Release
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossifymigration.baizhu.gallery.BuildConfig
import org.fossifymigration.baizhu.gallery.R
import org.fossifymigration.baizhu.gallery.adapters.DirectoryAdapter
import org.fossifymigration.baizhu.gallery.databases.GalleryDatabase
import org.fossifymigration.baizhu.gallery.databinding.ActivityMainBinding
import org.fossifymigration.baizhu.gallery.dialogs.ChangeSortingDialog
import org.fossifymigration.baizhu.gallery.dialogs.ChangeViewTypeDialog
import org.fossifymigration.baizhu.gallery.dialogs.FilterMediaDialog
import org.fossifymigration.baizhu.gallery.dialogs.GrantAllFilesDialog
import org.fossifymigration.baizhu.gallery.extensions.addTempFolderIfNeeded
import org.fossifymigration.baizhu.gallery.extensions.beginOperation
import org.fossifymigration.baizhu.gallery.extensions.config
import org.fossifymigration.baizhu.gallery.extensions.deleteMediaWithProgress
import org.fossifymigration.baizhu.gallery.extensions.createDirectoryFromMedia
import org.fossifymigration.baizhu.gallery.extensions.directoryDB
import org.fossifymigration.baizhu.gallery.extensions.getCachedMediaDirectories
import org.fossifymigration.baizhu.gallery.extensions.getDirectorySortingValue
import org.fossifymigration.baizhu.gallery.extensions.getDirsToShow
import org.fossifymigration.baizhu.gallery.extensions.getDistinctPath
import org.fossifymigration.baizhu.gallery.extensions.getFavoritePaths
import org.fossifymigration.baizhu.gallery.extensions.getHiddenMediaFilter
import org.fossifymigration.baizhu.gallery.extensions.getHiddenMediaState
import org.fossifymigration.baizhu.gallery.extensions.getNoMediaFoldersSync
import org.fossifymigration.baizhu.gallery.extensions.getOTGFolderChildrenNames
import org.fossifymigration.baizhu.gallery.extensions.getSortedDirectories
import org.fossifymigration.baizhu.gallery.extensions.handleExcludedFolderPasswordProtection
import org.fossifymigration.baizhu.gallery.extensions.handleMediaManagementPrompt
import org.fossifymigration.baizhu.gallery.extensions.isDownloadsFolder
import org.fossifymigration.baizhu.gallery.extensions.launchAbout
import org.fossifymigration.baizhu.gallery.extensions.launchCamera
import org.fossifymigration.baizhu.gallery.extensions.launchSettings
import org.fossifymigration.baizhu.gallery.extensions.mediaDB
import org.fossifymigration.baizhu.gallery.extensions.movePinnedDirectoriesToFront
import org.fossifymigration.baizhu.gallery.extensions.openRecycleBin
import org.fossifymigration.baizhu.gallery.extensions.removeInvalidDBDirectories
import org.fossifymigration.baizhu.gallery.extensions.storeDirectoryItems
import org.fossifymigration.baizhu.gallery.extensions.toggleHiddenOnly
import org.fossifymigration.baizhu.gallery.extensions.toggleOtherHidden
import org.fossifymigration.baizhu.gallery.extensions.tryDeleteFileDirItem
import org.fossifymigration.baizhu.gallery.extensions.updateWidgets
import org.fossifymigration.baizhu.gallery.helpers.DIRECTORY
import org.fossifymigration.baizhu.gallery.helpers.DirectoryPublication
import org.fossifymigration.baizhu.gallery.helpers.GalleryExecutors
import org.fossifymigration.baizhu.gallery.helpers.GET_ANY_INTENT
import org.fossifymigration.baizhu.gallery.helpers.GET_IMAGE_INTENT
import org.fossifymigration.baizhu.gallery.helpers.GET_VIDEO_INTENT
import org.fossifymigration.baizhu.gallery.helpers.GROUP_BY_DATE_TAKEN_DAILY
import org.fossifymigration.baizhu.gallery.helpers.GROUP_BY_DATE_TAKEN_MONTHLY
import org.fossifymigration.baizhu.gallery.helpers.GROUP_BY_LAST_MODIFIED_DAILY
import org.fossifymigration.baizhu.gallery.helpers.GROUP_BY_LAST_MODIFIED_MONTHLY
import org.fossifymigration.baizhu.gallery.helpers.GROUP_DESCENDING
import org.fossifymigration.baizhu.gallery.helpers.GalleryPresentationChangeWatch
import org.fossifymigration.baizhu.gallery.helpers.HiddenMediaRequest
import org.fossifymigration.baizhu.gallery.helpers.HiddenMediaState
import org.fossifymigration.baizhu.gallery.helpers.LOCATION_INTERNAL
import org.fossifymigration.baizhu.gallery.helpers.MAX_COLUMN_COUNT
import org.fossifymigration.baizhu.gallery.helpers.MONTH_MILLISECONDS
import org.fossifymigration.baizhu.gallery.helpers.MediaFetcher
import org.fossifymigration.baizhu.gallery.helpers.PICKED_PATHS
import org.fossifymigration.baizhu.gallery.helpers.RECYCLE_BIN
import org.fossifymigration.baizhu.gallery.helpers.SET_WALLPAPER_INTENT
import org.fossifymigration.baizhu.gallery.helpers.SHOW_ALL
import org.fossifymigration.baizhu.gallery.helpers.SHOW_TEMP_HIDDEN_DURATION
import org.fossifymigration.baizhu.gallery.helpers.SKIP_AUTHENTICATION
import org.fossifymigration.baizhu.gallery.helpers.TYPE_GIFS
import org.fossifymigration.baizhu.gallery.helpers.TYPE_IMAGES
import org.fossifymigration.baizhu.gallery.helpers.TYPE_RAWS
import org.fossifymigration.baizhu.gallery.helpers.TYPE_SVGS
import org.fossifymigration.baizhu.gallery.helpers.TYPE_VIDEOS
import org.fossifymigration.baizhu.gallery.helpers.getDefaultFileFilter
import org.fossifymigration.baizhu.gallery.helpers.getPermissionToRequest
import org.fossifymigration.baizhu.gallery.helpers.getPermissionsToRequest
import org.fossifymigration.baizhu.gallery.interfaces.DirectoryOperationsListener
import org.fossifymigration.baizhu.gallery.jobs.NewPhotoFetcher
import org.fossifymigration.baizhu.gallery.models.Directory
import org.fossifymigration.baizhu.gallery.models.Medium
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

class MainActivity : SimpleActivity(), DirectoryOperationsListener {
    override var isSearchBarEnabled = true
    
    companion object {
        private const val PICK_MEDIA = 2
        private const val PICK_WALLPAPER = 3
        private const val LAST_MEDIA_CHECK_PERIOD = 3000L
        private const val DIRECTORY_LAYOUT_WORKAROUND_DELAY = 500L
    }

    private var mIsPickImageIntent = false
    private var mIsPickVideoIntent = false
    private var mIsGetImageContentIntent = false
    private var mIsGetVideoContentIntent = false
    private var mIsGetAnyContentIntent = false
    private var mIsSetWallpaperIntent = false
    private var mAllowPickingMultiple = false
    private var mIsThirdPartyIntent = false
    private var mIsGettingDirs = false
    @Volatile private var mDirectoryGeneration = 0
    private val mDirectoryPublication = DirectoryPublication()
    private var mRecreateDirectoryAdapter = false
    private val mLayoutWorkaround = Runnable { binding.directoriesGrid.scrollBy(0, 0) }
    private var mRequestedHiddenMediaState: HiddenMediaState? = null
    private var mRequestedDirectoryPolicy: List<Any>? = null
    private var mDirectoryStoreGeneration: Map<String, Long>? = null
    private var mDirectoryRevision = -1L
    private var mLoadedInitialPhotos = false
    private var mWasDefaultFolderChecked = false
    private var mWasMediaManagementPromptShown = false
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L

    // used at "Group direct subfolders" for navigation
    private var mCurrentPathPrefix = ""

    // used at "Group direct subfolders" for navigating Up with the back button
    private var mOpenedSubfolders = arrayListOf("")

    private var mDateFormat = ""
    private var mTimeFormat = ""
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mZoomListener: MyRecyclerView.MyZoomListener? = null
    private var mLastMediaFetcher: MediaFetcher? = null
    private var mDirs = ArrayList<Directory>()
    private var mDirsIgnoringSearch = ArrayList<Directory>()

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0
    private var mStoredStyleString = ""
    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)

        if (savedInstanceState == null) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
            removeTempFolder()
            checkRecycleBinItems()
            startNewPhotoFetcher()
        }

        mIsPickImageIntent = isPickImageIntent(intent)
        mIsPickVideoIntent = isPickVideoIntent(intent)
        mIsGetImageContentIntent = isGetImageContentIntent(intent)
        mIsGetVideoContentIntent = isGetVideoContentIntent(intent)
        mIsGetAnyContentIntent = isGetAnyContentIntent(intent)
        mIsSetWallpaperIntent = isSetWallpaperIntent(intent)
        mAllowPickingMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        mIsThirdPartyIntent = mIsPickImageIntent
                || mIsPickVideoIntent
                || mIsGetImageContentIntent
                || mIsGetVideoContentIntent
                || mIsGetAnyContentIntent
                || mIsSetWallpaperIntent

        setupOptionsMenu()
        refreshMenuItems()

        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(binding.directoriesGrid)
        )

        binding.directoriesRefreshLayout.setOnRefreshListener { getDirectories() }
        storeStateVariables()
        checkWhatsNewDialog()
        setupLatestMediaId()

        if (!config.wereFavoritesPinned) {
            config.addPinnedFolders(hashSetOf(FAVORITES))
            config.wereFavoritesPinned = true
        }

        if (!config.wasRecycleBinPinned) {
            config.addPinnedFolders(hashSetOf(RECYCLE_BIN))
            config.wasRecycleBinPinned = true
            config.saveFolderGrouping(SHOW_ALL, GROUP_BY_DATE_TAKEN_DAILY or GROUP_DESCENDING)
        }

        if (!config.wasSVGShowingHandled) {
            config.wasSVGShowingHandled = true
            if (config.filterMedia and TYPE_SVGS == 0) {
                config.filterMedia += TYPE_SVGS
            }
        }

        if (!config.wasSortingByNumericValueAdded) {
            config.wasSortingByNumericValueAdded = true
            config.sorting = config.sorting or SORT_USE_NUMERIC_VALUE
        }

        updateWidgets()
        registerFileUpdateListener()

        binding.directoriesSwitchSearching.setOnClickListener {
            launchSearchActivity()
        }

        // just request the permission, tryLoadGallery will then trigger in onResume
        handleMediaPermissions()
    }

    private fun handleMediaPermissions(callback: (() -> Unit)? = null) {
        requestMediaPermissions(enableRationale = true) {
            callback?.invoke()
            if (isRPlus() && !mWasMediaManagementPromptShown) {
                mWasMediaManagementPromptShown = true
                handleMediaManagementPrompt { }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        if (mRequestedHiddenMediaState != hiddenFilterState() || mRequestedDirectoryPolicy != directoryPolicy()) {
            mDirectoryGeneration++
            clearDirectoryPresentation()
            if (binding.mainMenu.isSearchOpen) {
                tryLoadGallery()
            }
        }
        updateMenuColors()
        config.isThirdPartyIntent = false
        mDateFormat = config.dateFormat
        mTimeFormat = getTimeFormat()

        refreshMenuItems()

        if (mStoredAnimateGifs != config.animateGifs) {
            getRecyclerAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getRecyclerAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            binding.directoriesGrid.adapter = null
            getDirectories()
        }

        if (mStoredTextColor != getProperTextColor()) {
            getRecyclerAdapter()?.updateTextColor(getProperTextColor())
        }

        val primaryColor = getProperPrimaryColor()
        if (mStoredPrimaryColor != primaryColor) {
            getRecyclerAdapter()?.updatePrimaryColor()
        }

        val styleString =
            "${config.folderStyle}${config.showFolderMediaCount}${config.limitFolderTitle}"
        if (mStoredStyleString != styleString) {
            setupAdapter(mDirs, forceRecreate = true)
        }

        binding.directoriesFastscroller.updateColors(primaryColor)
        binding.directoriesRefreshLayout.isEnabled = config.enablePullToRefresh
        getRecyclerAdapter()?.apply {
            dateFormat = config.dateFormat
            timeFormat = getTimeFormat()
        }

        binding.directoriesEmptyPlaceholder.setTextColor(getProperTextColor())
        binding.directoriesEmptyPlaceholder2.setTextColor(primaryColor)
        binding.directoriesSwitchSearching.setTextColor(primaryColor)
        binding.directoriesSwitchSearching.underlineText()
        binding.directoriesEmptyPlaceholder2.bringToFront()

        if (!binding.mainMenu.isSearchOpen) {
            refreshMenuItems()
            tryLoadGallery()
        }

        if (config.searchAllFilesByDefault) {
            binding.mainMenu.updateHintText(getString(org.fossify.commons.R.string.search_files))
        } else {
            binding.mainMenu.updateHintText(getString(org.fossify.commons.R.string.search_folders))
        }
    }

    override fun onPause() {
        super.onPause()
        mDirectoryGeneration++
        mDirectoryPublication.clear()
        binding.directoriesGrid.removeCallbacks(mLayoutWorkaround)
        mLastMediaFetcher?.shouldStop = true
        binding.directoriesRefreshLayout.isRefreshing = false
        mIsGettingDirs = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)
    }

    override fun onStop() {
        super.onStop()

        if (config.temporarilyShowHidden || config.tempSkipDeleteConfirmation || config.temporarilyShowExcluded) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.temporarilyShowExcluded = false
                config.tempSkipDeleteConfirmation = false
                config.tempSkipRecycleBin = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.temporarilyShowExcluded = false
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
            removeTempFolder()
            unregisterFileUpdateListener()

            if (!config.showAll) {
                mLastMediaFetcher?.shouldStop = true
                GalleryDatabase.destroyInstance()
            }
        }
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else if (config.groupDirectSubfolders) {
            if (mCurrentPathPrefix.isEmpty()) {
                appLockManager.lock()
                false
            } else {
                mOpenedSubfolders.removeAt(mOpenedSubfolders.lastIndex)
                mCurrentPathPrefix = mOpenedSubfolders.last()
                setupAdapter(mDirs)
                true
            }
        } else {
            appLockManager.lock()
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_MEDIA && resultData != null) {
                val resultIntent = Intent()
                var resultUri: Uri? = null
                if (mIsThirdPartyIntent) {
                    when {
                        intent.extras?.containsKey(MediaStore.EXTRA_OUTPUT) == true
                                && intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 -> {
                            resultUri = fillExtraOutput(resultData)
                        }

                        resultData.extras?.containsKey(PICKED_PATHS) == true -> {
                            fillPickedPaths(resultData, resultIntent)
                        }

                        else -> fillIntentPath(resultData, resultIntent)
                    }
                }

                if (resultUri != null) {
                    resultIntent.data = resultUri
                    resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                setResult(RESULT_OK, resultIntent)
                finish()
            } else if (requestCode == PICK_WALLPAPER) {
                setResult(RESULT_OK)
                finish()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun refreshMenuItems() {
        if (!mIsThirdPartyIntent) {
            binding.mainMenu.requireToolbar().menu.apply {
                findItem(R.id.column_count).isVisible = config.viewTypeFolders == VIEW_TYPE_GRID
                findItem(R.id.set_as_default_folder).isVisible = !config.defaultFolder.isEmpty()
                findItem(R.id.open_recycle_bin).isVisible =
                    config.useRecycleBin && !config.showRecycleBinAtFolders
                findItem(R.id.more_apps_from_us).isVisible =
                    resources.getBoolean(org.fossify.commons.R.bool.is_google_play_build)
            }
        }

        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.show_only_hidden).isChecked = config.filterHiddenOnly
            findItem(R.id.include_other_hidden).isChecked = config.includeOtherHidden
            findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden
            findItem(R.id.stop_showing_hidden).isVisible =
                (!isRPlus() || isExternalStorageManager()) && config.temporarilyShowHidden

            findItem(R.id.temporarily_show_excluded).isVisible = !config.temporarilyShowExcluded
            findItem(R.id.stop_showing_excluded).isVisible = config.temporarilyShowExcluded
        }
    }

    private fun setupOptionsMenu() {
        val menuId = if (mIsThirdPartyIntent) {
            R.menu.menu_main_intent
        } else {
            R.menu.menu_main
        }

        binding.mainMenu.requireToolbar().inflateMenu(menuId)
        binding.mainMenu.toggleHideOnScroll(!config.scrollHorizontally)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchOpenListener = {
            if (config.searchAllFilesByDefault) {
                launchSearchActivity()
            }
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            setupAdapter(mDirsIgnoringSearch, text)
            binding.directoriesRefreshLayout.isEnabled =
                text.isEmpty() && config.enablePullToRefresh
            binding.directoriesSwitchSearching.beVisibleIf(text.isNotEmpty())
        }

        binding.mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog()
                R.id.filter -> showFilterMediaDialog()
                R.id.open_camera -> launchCamera()
                R.id.show_all -> showAllMedia()
                R.id.change_view_type -> changeViewType()
                R.id.show_only_hidden -> toggleHiddenOnly { refreshHiddenFilter() }
                R.id.include_other_hidden -> toggleOtherHidden { refreshHiddenFilter() }
                R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
                R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
                R.id.temporarily_show_excluded -> tryToggleTemporarilyShowExcluded()
                R.id.stop_showing_excluded -> tryToggleTemporarilyShowExcluded()
                R.id.create_new_folder -> createNewFolder()
                R.id.open_recycle_bin -> openRecycleBin()
                R.id.column_count -> changeColumnCount()
                R.id.set_as_default_folder -> setAsDefaultFolder()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
    }

    private fun getRecyclerAdapter() = binding.directoriesGrid.adapter as? DirectoryAdapter

    private fun storeStateVariables() {
        mStoredTextColor = getProperTextColor()
        mStoredPrimaryColor = getProperPrimaryColor()
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredStyleString = "$folderStyle$showFolderMediaCount$limitFolderTitle"
        }
    }

    private fun startNewPhotoFetcher() {
        val photoFetcher = NewPhotoFetcher()
        if (!photoFetcher.isScheduled(applicationContext)) {
            photoFetcher.scheduleJob(applicationContext)
        }
    }

    private fun removeTempFolder() {
        if (config.tempFolderPath.isNotEmpty()) {
            val newFolder = File(config.tempFolderPath)
            if (getDoesFilePathExist(newFolder.absolutePath) && newFolder.isDirectory) {
                if (
                    newFolder.getProperSize(true) == 0L
                    && newFolder.getFileCount(true) == 0
                    && newFolder.list()?.isEmpty() == true
                ) {
                    toast(
                        String.format(
                            getString(org.fossify.commons.R.string.deleting_folder),
                            config.tempFolderPath
                        ), Toast.LENGTH_LONG
                    )
                    tryDeleteFileDirItem(newFolder.toFileDirItem(applicationContext), true, true)
                }
            }
            config.tempFolderPath = ""
        }
    }

    private fun checkOTGPath() {
        ensureBackgroundThread {
            if (!config.wasOTGHandled && hasPermission(getPermissionToRequest()) && hasOTGConnected() && config.OTGPath.isEmpty()) {
                getStorageDirectories().firstOrNull {
                    it.trimEnd('/') != internalStoragePath
                            && it.trimEnd('/') != sdCardPath
                }?.apply {
                    config.wasOTGHandled = true
                    val otgPath = trimEnd('/')
                    config.OTGPath = otgPath
                    config.addIncludedFolder(otgPath)
                }
            }
        }
    }

    private fun checkDefaultSpamFolders() {
        if (!config.spamFoldersChecked) {
            val spamFolders = arrayListOf(
                "/storage/emulated/0/Android/data/com.facebook.orca/files/stickers"
            )

            val OTGPath = config.OTGPath
            spamFolders.forEach {
                if (getDoesFilePathExist(it, OTGPath)) {
                    config.addExcludedFolder(it)
                }
            }
            config.spamFoldersChecked = true
        }
    }

    private fun tryLoadGallery() {
        // avoid calling anything right after granting the permission, it will be called from onResume()
        val wasMissingPermission =
            config.appRunCount == 1 && !hasAllPermissions(getPermissionsToRequest())
        handleMediaPermissions {
            if (wasMissingPermission) {
                return@handleMediaPermissions
            }

            if (!mWasDefaultFolderChecked) {
                openDefaultFolder()
                mWasDefaultFolderChecked = true
            }

            checkOTGPath()
            checkDefaultSpamFolders()

            if (config.showAll) {
                showAllMedia()
            } else if (!mLoadedInitialPhotos || mRequestedHiddenMediaState != hiddenFilterState() ||
                mDirectoryRevision != GalleryPresentationChangeWatch.revision
            ) {
                getDirectories()
            } else {
                val generation = mDirectoryGeneration
                GalleryExecutors.media.execute {
                    val storeGeneration = directoryStoreGeneration()
                    runOnUiThread {
                        if (generation == mDirectoryGeneration && !isDestroyed && !isFinishing) {
                            if (storeGeneration == null || storeGeneration != mDirectoryStoreGeneration) {
                                getDirectories()
                            } else {
                                checkLastMediaChanged()
                            }
                        }
                    }
                }
            }

            setupLayoutManager()
        }
    }

    private fun getDirectories() {
        val state = hiddenFilterState()
        val policy = directoryPolicy()
        if (mRequestedHiddenMediaState != state || mRequestedDirectoryPolicy != policy) {
            mDirectoryGeneration++
            mLastMediaFetcher?.shouldStop = true
            mIsGettingDirs = false
            clearDirectoryPresentation()
        }
        mRequestedHiddenMediaState = state
        mRequestedDirectoryPolicy = policy
        if (mIsGettingDirs) {
            return
        }

        mLastMediaFetcher?.shouldStop = true
        mIsGettingDirs = true
        val getImages = mIsPickImageIntent || mIsGetImageContentIntent
        val getVideos = mIsPickVideoIntent || mIsGetVideoContentIntent

        val generation = ++mDirectoryGeneration
        val filterState = state
        val revision = GalleryPresentationChangeWatch.revision
        getCachedMediaDirectories(getVideos && !getImages, getImages && !getVideos) {
            if (generation != mDirectoryGeneration) {
                return@getCachedMediaDirectories
            }
            if (filterState == hiddenFilterState()) {
                gotDirectories(addTempFolderIfNeeded(it), HiddenMediaRequest(generation, filterState), revision)
            } else {
                runOnUiThread {
                    if (generation == mDirectoryGeneration && !isDestroyed && !isFinishing) {
                        mIsGettingDirs = false
                        getDirectories()
                    }
                }
            }
        }
    }

    private fun launchSearchActivity() {
        hideKeyboard()
        Intent(this, SearchActivity::class.java).apply {
            startActivity(this)
        }

        binding.mainMenu.postDelayed({
            binding.mainMenu.closeSearch()
        }, 500)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, true, false) {
            binding.directoriesGrid.adapter = null
            if (config.directorySorting and SORT_BY_DATE_MODIFIED != 0 || config.directorySorting and SORT_BY_DATE_TAKEN != 0) {
                getDirectories()
            } else {
                reloadDisplayedDirectories { it }
            }

            getRecyclerAdapter()?.directorySorting = config.directorySorting
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            mLastMediaFetcher?.shouldStop = true
            binding.directoriesRefreshLayout.isRefreshing = true
            binding.directoriesGrid.adapter = null
            getDirectories()
        }
    }

    private fun showAllMedia() {
        config.showAll = true
        Intent(this, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, "")

            if (mIsThirdPartyIntent) {
                handleMediaIntent(this)
            } else {
                hideKeyboard()
                startActivity(this)
                finish()
            }
        }
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, true) {
            refreshMenuItems()
            setupLayoutManager()
            binding.directoriesGrid.adapter = null
            setupAdapter(getRecyclerAdapter()?.dirs ?: mDirs)
        }
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                handleHiddenFolderPasswordProtection {
                    toggleTemporarilyShowHidden(true)
                }
            }
        }
    }

    private fun toggleTemporarilyShowHidden(show: Boolean) {
        config.temporarilyShowHidden = show
        if (!show) {
            config.filterHiddenOnly = false
        }
        refreshHiddenFilter()
    }

    private fun refreshHiddenFilter() {
        mDirectoryGeneration++
        mLastMediaFetcher?.shouldStop = true
        mIsGettingDirs = false
        clearDirectoryPresentation()
        getDirectories()
        refreshMenuItems()
    }

    private fun clearDirectoryPresentation() {
        mDirectoryPublication.clear()
        mLoadedInitialPhotos = false
        mDirs = arrayListOf()
        mDirsIgnoringSearch = arrayListOf()
        binding.directoriesGrid.adapter = null
    }

    private fun tryToggleTemporarilyShowExcluded() {
        if (config.temporarilyShowExcluded) {
            toggleTemporarilyShowExcluded(false)
        } else {
            handleExcludedFolderPasswordProtection {
                toggleTemporarilyShowExcluded(true)
            }
        }
    }

    private fun toggleTemporarilyShowExcluded(show: Boolean) {
        mLoadedInitialPhotos = false
        config.temporarilyShowExcluded = show
        binding.directoriesGrid.adapter = null
        getDirectories()
        refreshMenuItems()
    }

    override fun deleteFolders(folders: ArrayList<File>) {
        val folderSnapshot = folders.toList()
        val progress = beginOperation(R.string.bulk_preparing, folderSnapshot.size) ?: return
        val hiddenFilter = getHiddenMediaFilter()
        val types = config.filterMedia
        val skipBin = !config.useRecycleBin || config.tempSkipRecycleBin
        val deleteEmptyFolders = config.deleteEmptyFolders
        GalleryExecutors.media.execute {
            val selected = ArrayList<FileDirItem>()
            var failure: Exception? = null
            try {
                folderSnapshot.forEachIndexed { index, folder ->
                    if (!progress.isClosed) {
                        folder.listFiles()?.filter { file ->
                            val path = file.absolutePath
                            file.isFile && path.isMediaFile() && hiddenFilter.matches(path) &&
                                mediaMatchesTypes(path, types)
                        }?.mapTo(selected) { it.toFileDirItem(applicationContext) }
                        progress.update(index + 1)
                    }
                }
            } catch (e: Exception) {
                failure = e
            } finally {
                runOnUiThread {
                    val alive = !progress.isClosed && !isDestroyed
                    progress.finish()
                    if (alive) {
                        val error = failure
                        if (error != null) {
                            showErrorToast(error)
                        } else {
                            deleteMediaWithProgress(selected, skipBin) {
                                GalleryExecutors.media.execute {
                                    if (deleteEmptyFolders) {
                                        folderSnapshot.forEach { folder ->
                                            try {
                                                if (!folder.absolutePath.isDownloadsFolder() && folder.isDirectory &&
                                                    folder.listFiles()?.isEmpty() == true
                                                ) {
                                                    // Non-recursive deletion also rejects newly added children.
                                                    if (folder.delete()) directoryDB.deleteDirPath(folder.absolutePath)
                                                }
                                            } catch (e: Exception) {
                                                runOnUiThread { showErrorToast(e) }
                                            }
                                        }
                                    }
                                    runOnUiThread { if (!isDestroyed) refreshItems() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun mediaMatchesTypes(path: String, types: Int): Boolean =
        (path.isImageFast() && types and TYPE_IMAGES != 0) ||
            (path.isVideoFast() && types and TYPE_VIDEOS != 0) ||
            (path.isGif() && types and TYPE_GIFS != 0) ||
            (path.isRawFast() && types and TYPE_RAWS != 0) ||
            (path.isSvg() && types and TYPE_SVGS != 0)

    private fun setupLayoutManager() {
        if (config.viewTypeFolders == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }

        (binding.directoriesRefreshLayout.layoutParams as RelativeLayout.LayoutParams)
            .addRule(RelativeLayout.BELOW, R.id.directories_switch_searching)
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.directoriesGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.directoriesRefreshLayout.layoutParams =
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.directoriesRefreshLayout.layoutParams =
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
        }

        layoutManager.spanCount = config.dirColumnCnt
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.directoriesGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        binding.directoriesRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mZoomListener = null
    }

    private fun initZoomListener() {
        if (config.viewTypeFolders == VIEW_TYPE_GRID) {
            val layoutManager = binding.directoriesGrid.layoutManager as MyGridLayoutManager
            mZoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            mZoomListener = null
        }
    }

    private fun createNewFolder() {
        FilePickerDialog(this, internalStoragePath, false, config.shouldShowHidden, false, true) {
            CreateNewFolderDialog(this, it) {
                config.tempFolderPath = it
                reloadDisplayedDirectories { addTempFolderIfNeeded(it) }
            }
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..MAX_COLUMN_COUNT) {
            items.add(
                RadioItem(
                    id = i,
                    title = resources.getQuantityString(
                        org.fossify.commons.R.plurals.column_counts, i, i
                    )
                )
            )
        }

        val currentColumnCount =
            (binding.directoriesGrid.layoutManager as MyGridLayoutManager).spanCount
        RadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.dirColumnCnt = newColumnCount
                columnCountChanged()
            }
        }
    }

    private fun increaseColumnCount() {
        config.dirColumnCnt += 1
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.dirColumnCnt -= 1
        columnCountChanged()
    }

    private fun columnCountChanged() {
        (binding.directoriesGrid.layoutManager as MyGridLayoutManager).spanCount =
            config.dirColumnCnt
        refreshMenuItems()
        getRecyclerAdapter()?.apply {
            notifyItemRangeChanged(0, dirs.size)
        }
    }

    private fun isPickImageIntent(intent: Intent): Boolean {
        return isPickIntent(intent) && (hasImageContentData(intent) || isImageType(intent))
    }

    private fun isPickVideoIntent(intent: Intent): Boolean {
        return isPickIntent(intent) && (hasVideoContentData(intent) || isVideoType(intent))
    }

    private fun isPickIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_PICK
    }

    private fun isGetContentIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_GET_CONTENT && intent.type != null
    }

    private fun anyExtraMimeTypeStartingWith(intent: Intent, mimeTypePrefix: String): Boolean {
        return intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
            ?.any { it.startsWith(mimeTypePrefix) } == true
    }

    private fun isGetImageContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent)
                && (intent.type!!.startsWith("image/")
                || intent.type == Images.Media.CONTENT_TYPE
                || anyExtraMimeTypeStartingWith(intent, "image/"))
    }

    private fun isGetVideoContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent)
                && (intent.type!!.startsWith("video/")
                || intent.type == Video.Media.CONTENT_TYPE
                || anyExtraMimeTypeStartingWith(intent, "video/"))
    }

    private fun isGetAnyContentIntent(intent: Intent): Boolean {
        return isGetContentIntent(intent) && intent.type == "*/*"
    }

    private fun isSetWallpaperIntent(intent: Intent?): Boolean {
        return intent?.action == Intent.ACTION_SET_WALLPAPER
    }

    private fun hasImageContentData(intent: Intent): Boolean {
        return intent.data == Images.Media.EXTERNAL_CONTENT_URI
                || intent.data == Images.Media.INTERNAL_CONTENT_URI
    }

    private fun hasVideoContentData(intent: Intent): Boolean {
        return intent.data == Video.Media.EXTERNAL_CONTENT_URI
                || intent.data == Video.Media.INTERNAL_CONTENT_URI
    }

    private fun isImageType(intent: Intent): Boolean {
        return (intent.type?.startsWith("image/") == true
                || intent.type == Images.Media.CONTENT_TYPE)
    }

    private fun isVideoType(intent: Intent): Boolean {
        return (intent.type?.startsWith("video/") == true
                || intent.type == Video.Media.CONTENT_TYPE)
    }

    private fun fillExtraOutput(resultData: Intent): Uri? {
        val file = File(resultData.data!!.path!!)
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            val output = intent.extras!!.get(MediaStore.EXTRA_OUTPUT) as Uri
            inputStream = FileInputStream(file)
            outputStream = contentResolver.openOutputStream(output)
            inputStream.copyTo(outputStream!!)
        } catch (e: SecurityException) {
            showErrorToast(e)
        } catch (ignored: FileNotFoundException) {
            return getFilePublicUri(file, BuildConfig.APPLICATION_ID)
        } finally {
            inputStream?.close()
            outputStream?.close()
        }

        return null
    }

    private fun fillPickedPaths(resultData: Intent, resultIntent: Intent) {
        val paths = resultData.extras!!.getStringArrayList(PICKED_PATHS)
        val uris = paths!!
            .map { getFilePublicUri(File(it), BuildConfig.APPLICATION_ID) } as ArrayList
        val clipData = ClipData(
            "Attachment",
            arrayOf("image/*", "video/*"),
            ClipData.Item(uris.removeAt(0))
        )

        uris.forEach {
            clipData.addItem(ClipData.Item(it))
        }

        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resultIntent.clipData = clipData
    }

    private fun fillIntentPath(resultData: Intent, resultIntent: Intent) {
        val data = resultData.data
        val path = if (data.toString().startsWith("/")) data.toString() else data!!.path
        val uri = getFilePublicUri(File(path!!), BuildConfig.APPLICATION_ID)
        val type = path.getMimeType()
        resultIntent.setDataAndTypeAndNormalize(uri, type)
        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun itemClicked(path: String) {
        handleLockedFolderOpening(path) { success ->
            if (success) {
                Intent(this, MediaActivity::class.java).apply {
                    putExtra(SKIP_AUTHENTICATION, true)
                    putExtra(DIRECTORY, path)
                    handleMediaIntent(this)
                }
            }
        }
    }

    private fun handleMediaIntent(intent: Intent) {
        hideKeyboard()
        intent.apply {
            if (mIsSetWallpaperIntent) {
                putExtra(SET_WALLPAPER_INTENT, true)
                startActivityForResult(this, PICK_WALLPAPER)
            } else {
                putExtra(GET_IMAGE_INTENT, mIsPickImageIntent || mIsGetImageContentIntent)
                putExtra(GET_VIDEO_INTENT, mIsPickVideoIntent || mIsGetVideoContentIntent)
                putExtra(GET_ANY_INTENT, mIsGetAnyContentIntent)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, mAllowPickingMultiple)
                startActivityForResult(this, PICK_MEDIA)
            }
        }
    }

    // Worker-only. Older providers have no reliable mutation token, so retain their resume scan.
    private fun directoryStoreGeneration(): Map<String, Long>? = if (isRPlus()) {
        try {
            MediaStore.getExternalVolumeNames(this).associateWith { MediaStore.getGeneration(this, it) }
        } catch (ignored: Exception) {
            null
        }
    } else null

    private fun directoryPolicy(): List<Any> = config.run {
        listOf(excludedFolders.toSet(), includedFolders.toSet(), temporarilyShowExcluded, filterMedia,
            directorySorting, groupDirectSubfolders, showRecycleBinAtFolders, showRecycleBinLast)
    }

    private fun hiddenFilterState() = getHiddenMediaState()

    private fun directoryRequest() = HiddenMediaRequest(mDirectoryGeneration, hiddenFilterState())

    private fun isDirectoryRequestCurrent(request: HiddenMediaRequest) =
        request.isCurrent(mDirectoryGeneration, hiddenFilterState())

    private fun reloadDisplayedDirectories(transform: (ArrayList<Directory>) -> ArrayList<Directory>) {
        val request = HiddenMediaRequest(++mDirectoryGeneration, hiddenFilterState())
        mLastMediaFetcher?.shouldStop = true
        val displayed = getCurrentlyDisplayedDirs().mapTo(ArrayList()) { it.copy() }
        val revision = GalleryPresentationChangeWatch.revision
        ensureBackgroundThread { gotDirectories(transform(displayed), request, revision) }
    }

    private fun gotDirectories(newDirs: ArrayList<Directory>, request: HiddenMediaRequest, revision: Long) {
        if (!isDirectoryRequestCurrent(request)) return
        val storeGeneration = directoryStoreGeneration()
        val fetcher = MediaFetcher(applicationContext)
        runOnUiThread {
            if (!isDirectoryUiRequestCurrent(request)) return@runOnUiThread
            mLastMediaFetcher?.shouldStop = true
            mLastMediaFetcher = fetcher
            mIsGettingDirs = false
        }

        // if hidden item showing is disabled but all Favorite items are hidden, hide the Favorites folder
        if (!config.shouldShowHidden) {
            val favoritesFolder = newDirs.firstOrNull { it.areFavorites() }
            if (
                favoritesFolder != null
                && favoritesFolder.tmb.getFilenameFromPath().startsWith('.')
            ) {
                newDirs.remove(favoritesFolder)
            }
        }

        val dirs = getSortedDirectories(newDirs)
        val cached = dirs.mapTo(ArrayList()) { it.copy() }
        runOnUiThread {
            if (!isDirectoryUiRequestCurrent(request)) return@runOnUiThread
            if (config.groupDirectSubfolders) mDirs = cached
            checkPlaceholderVisibility(cached)
            setupAdapter(cached, request = request)
        }

        val scan = DirectoryScan(dirs, request, fetcher)
        addCachedVirtualDirectories(dirs)
        scan.refreshCached()
        if (!scan.isCurrent()) return
        scan.discover(remainingFolders(dirs, fetcher))
        if (scan.isCurrent()) {
            scan.store()
            finishDirectoryLoading(dirs, request, storeGeneration, revision)
        }
    }

    private fun addCachedVirtualDirectories(dirs: ArrayList<Directory>) {
        if (config.showRecycleBinAtFolders && !config.showRecycleBinLast && dirs.none { it.isRecycleBin() }) {
            cachedRecycleBin()?.let { dirs.add(0, it) }
        }

        if (dirs.any { it.areFavorites() } && mediaDB.getFavoritesCount() > 0) {
            val favorites = Directory().apply {
                path = FAVORITES
                name = getString(org.fossify.commons.R.string.favorites)
                location = LOCATION_INTERNAL
            }

            dirs.add(0, favorites)
        }
    }

    private fun cachedRecycleBin(): Directory? = try {
        if (mediaDB.getDeletedMediaCount() > 0) {
            Directory().apply {
                path = RECYCLE_BIN
                name = getString(org.fossify.commons.R.string.recycle_bin)
                location = LOCATION_INTERNAL
            }
        } else null
    } catch (_: Exception) {
        null
    }

    private inner class DirectoryScan(
        private val dirs: ArrayList<Directory>,
        private val request: HiddenMediaRequest,
        private val fetcher: MediaFetcher
    ) {
        private val getImages = mIsPickImageIntent || mIsGetImageContentIntent
        private val getVideos = mIsPickVideoIntent || mIsGetVideoContentIntent
        private val getImagesOnly = getImages && !getVideos
        private val getVideosOnly = getVideos && !getImages
        private val favoritePaths = getFavoritePaths()
        private val hiddenString = getString(R.string.hidden)
        private val albumCovers = config.parseAlbumCovers()
        private val includedFolders = config.includedFolders
        private val noMediaFolders = getNoMediaFoldersSync()
        private val tempFolderPath = config.tempFolderPath
        private val getProperFileSize = config.directorySorting and SORT_BY_SIZE != 0
        private val mediaToStore = ArrayList<Medium>()
        private val directoriesToStore = LinkedHashMap<String, Directory>()
        private val completeAggregates = !request.state.isFilteringActive &&
            config.filterMedia == getDefaultFileFilter() && !getImagesOnly && !getVideosOnly
        private var isPlaceholderVisible = dirs.isEmpty()
        private val lastModifieds = fetcher.getLastModifieds()
        private val dateTakens = fetcher.getDateTakens()
        private val android11Files by lazy {
            fetcher.getAndroid11FolderMedia(
                isPickImage = getImagesOnly,
                isPickVideo = getVideosOnly,
                favoritePaths = favoritePaths,
                getFavoritePathsOnly = false,
                getProperDateTaken = true,
                dateTakens = dateTakens
            )
        }

        fun isCurrent() = !fetcher.shouldStop && isDirectoryUiRequestCurrent(request)

        fun refreshCached() {
            val dirPathsToRemove = ArrayList<String>()
            try {
                updateCachedDirectories(dirPathsToRemove)
                if (isCurrent() && dirPathsToRemove.isNotEmpty()) removeMissingDirectories(dirPathsToRemove)
            } catch (ignored: Exception) {
            }
        }

        private fun updateCachedDirectories(dirPathsToRemove: ArrayList<String>) {
            for (directory in dirs) {
                if (!isCurrent()) return
                val curMedia = readMedia(directory.path)
                if (!isCurrent()) return
                val newDir = if (curMedia.isEmpty()) {
                    if (directory.path != tempFolderPath) {
                        dirPathsToRemove.add(directory.path)
                    }
                    directory
                } else {
                    summarize(directory.path, curMedia)
                }

                // Avoid republishing an unchanged cached folder.
                if (directory.copy(subfoldersCount = 0, subfoldersMediaCount = 0) == newDir) {
                    continue
                }

                directory.apply {
                    tmb = newDir.tmb
                    name = newDir.name
                    mediaCnt = newDir.mediaCnt
                    modified = newDir.modified
                    taken = newDir.taken
                    this@apply.size = newDir.size
                    types = newDir.types
                    sortValue = getDirectorySortingValue(curMedia, path, name, size, mediaCnt)
                }

                setupAdapter(dirs, request = request)

                stage(directory, curMedia)
            }
        }

        private fun removeMissingDirectories(paths: List<String>) {
            val dirsToRemove = dirs.filter { it.path in paths }
            dirsToRemove.forEach {
                // A filtered result cannot invalidate a cache record unless its path is gone.
                if (completeAggregates && isCurrent() && !getDoesFilePathExist(it.path, config.OTGPath)) {
                    directoryDB.deleteDirPath(it.path)
                }
            }
            dirs.removeAll(dirsToRemove)
            setupAdapter(dirs, request = request)
        }

        private fun readMedia(folder: String): ArrayList<Medium> {
            val sorting = config.getFolderSorting(folder)
            val grouping = config.getFolderGrouping(folder)
            val getProperDateTaken = config.directorySorting and SORT_BY_DATE_TAKEN != 0
                    || sorting and SORT_BY_DATE_TAKEN != 0
                    || grouping and GROUP_BY_DATE_TAKEN_DAILY != 0
                    || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

            val getProperLastModified = config.directorySorting and SORT_BY_DATE_MODIFIED != 0
                    || sorting and SORT_BY_DATE_MODIFIED != 0
                    || grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0
                    || grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

            return fetcher.getFilesFrom(
                curPath = folder,
                isPickImage = getImagesOnly,
                isPickVideo = getVideosOnly,
                getProperDateTaken = getProperDateTaken,
                getProperLastModified = getProperLastModified,
                getProperFileSize = getProperFileSize,
                favoritePaths = favoritePaths,
                getVideoDurations = false,
                lastModifieds = lastModifieds,
                dateTakens = dateTakens,
                android11Files = android11Files
            )
        }

        fun discover(folders: List<String>) {
            for (folder in folders) {
                if (!isCurrent()) return
                val newMedia = readMedia(folder)
                if (!isCurrent()) return
                if (newMedia.isEmpty()) continue
                if (isPlaceholderVisible) {
                    isPlaceholderVisible = false
                    hideDirectoryPlaceholder(request)
                }
                val newDir = summarize(folder, newMedia)
                dirs.add(newDir)
                setupAdapter(dirs, request = request)
                stage(newDir, newMedia)
            }
        }

        private fun summarize(folder: String, media: ArrayList<Medium>) = createDirectoryFromMedia(
            path = folder,
            curMedia = media,
            albumCovers = albumCovers,
            hiddenString = hiddenString,
            includedFolders = includedFolders,
            getProperFileSize = getProperFileSize,
            noMediaFolders = noMediaFolders
        )

        private fun stage(directory: Directory, media: List<Medium>) {
            // A display subset is not a complete album aggregate.
            if (completeAggregates) directoriesToStore[directory.path] = directory.copy()
            if (!directory.isRecycleBin() && !directory.areFavorites()) mediaToStore.addAll(media)
        }

        fun store() {
            GalleryExecutors.database.execute {
                if (!isDirectoryRequestCurrent(request)) return@execute
                try {
                    if (completeAggregates && !config.isHiddenMediaFilteringActive) {
                        directoryDB.insertAll(directoriesToStore.values.toList())
                    }
                    mediaDB.insertAll(mediaToStore)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun remainingFolders(dirs: List<Directory>, fetcher: MediaFetcher): List<String> {
        val folders = fetcher.getFoldersToScan()
        folders.remove(FAVORITES)
        folders.add(0, FAVORITES)
        folders.remove(RECYCLE_BIN)
        if (config.showRecycleBinAtFolders) folders.add(0, RECYCLE_BIN)
        dirs.filterNot { it.isRecycleBin() || it.areFavorites() }.forEach { folders.remove(it.path) }
        return folders
    }

    private fun hideDirectoryPlaceholder(request: HiddenMediaRequest) {
        runOnUiThread {
            if (!isDirectoryUiRequestCurrent(request)) return@runOnUiThread
            binding.directoriesEmptyPlaceholder.beGone()
            binding.directoriesEmptyPlaceholder2.beGone()
            binding.directoriesFastscroller.beVisible()
        }
    }

    private fun finishDirectoryLoading(
        dirs: ArrayList<Directory>, request: HiddenMediaRequest, storeGeneration: Map<String, Long>?, revision: Long
    ) {
        checkInvalidDirectories(dirs, request)
        if (!isDirectoryRequestCurrent(request)) return
        runOnUiThread {
            if (!isDirectoryUiRequestCurrent(request)) return@runOnUiThread
            mLoadedInitialPhotos = true
            mDirectoryStoreGeneration = storeGeneration
            mDirectoryRevision = revision
            mDirs = dirs
            binding.directoriesRefreshLayout.isRefreshing = false
            setupAdapter(dirs, immediate = true, request = request)
            if (config.appRunCount > 1) checkLastMediaChanged()
        }
        if (dirs.size > 50) {
            excludeSpamFolders()
        }

        val excludedFolders = config.excludedFolders
        val everShownFolders = config.everShownFolders.toMutableSet() as HashSet<String>

        // do not add excluded folders and their subfolders at everShownFolders
        dirs.filter { dir ->
            return@filter !excludedFolders.any { dir.path.startsWith(it) }
        }.mapTo(everShownFolders) { it.path }

        try {
            // scan the internal storage from time to time for new folders
            if (config.appRunCount == 1 || config.appRunCount % 30 == 0) {
                everShownFolders.addAll(getFoldersWithMedia(config.internalStoragePath))
            }

            // catch some extreme exceptions like too many everShownFolders for storing, shouldnt really happen
            config.everShownFolders = everShownFolders
        } catch (e: Exception) {
            config.everShownFolders = HashSet()
        }
    }

    private fun setAsDefaultFolder() {
        config.defaultFolder = ""
        refreshMenuItems()
    }

    private fun openDefaultFolder() {
        if (config.defaultFolder.isEmpty()) {
            return
        }

        val defaultDir = File(config.defaultFolder)

        if ((!defaultDir.exists() || !defaultDir.isDirectory) && (config.defaultFolder != RECYCLE_BIN && config.defaultFolder != FAVORITES)) {
            config.defaultFolder = ""
            return
        }

        Intent(this, MediaActivity::class.java).apply {
            putExtra(DIRECTORY, config.defaultFolder)
            handleMediaIntent(this)
        }
    }

    private fun checkPlaceholderVisibility(dirs: ArrayList<Directory>) {
        binding.directoriesEmptyPlaceholder.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)
        binding.directoriesEmptyPlaceholder2.beVisibleIf(dirs.isEmpty() && mLoadedInitialPhotos)

        if (dirs.isEmpty() && config.filterHiddenOnly && !config.includeOtherHidden) {
            binding.directoriesEmptyPlaceholder.text = getString(R.string.no_my_hidden_media)
            binding.directoriesEmptyPlaceholder2.text = getString(R.string.include_other_hidden)
            binding.directoriesEmptyPlaceholder2.setOnClickListener {
                toggleOtherHidden { refreshHiddenFilter() }
            }
        } else if (binding.mainMenu.isSearchOpen) {
            binding.directoriesEmptyPlaceholder.text =
                getString(org.fossify.commons.R.string.no_items_found)
            binding.directoriesEmptyPlaceholder2.beGone()
        } else if (
            dirs.isEmpty() && !config.isHiddenMediaFilteringActive && config.filterMedia == getDefaultFileFilter()
        ) {
            if (isRPlus() && !isExternalStorageManager()) {
                binding.directoriesEmptyPlaceholder.text =
                    getString(org.fossify.commons.R.string.no_items_found)
                binding.directoriesEmptyPlaceholder2.beGone()
            } else {
                binding.directoriesEmptyPlaceholder.text = getString(R.string.no_media_add_included)
                binding.directoriesEmptyPlaceholder2.text = getString(R.string.add_folder)
            }

            binding.directoriesEmptyPlaceholder2.setOnClickListener {
                showAddIncludedFolderDialog {
                    refreshItems()
                }
            }
        } else {
            binding.directoriesEmptyPlaceholder.text = getString(R.string.no_media_with_filters)
            binding.directoriesEmptyPlaceholder2.text =
                getString(R.string.change_filters_underlined)

            binding.directoriesEmptyPlaceholder2.setOnClickListener {
                showFilterMediaDialog()
            }
        }

        binding.directoriesEmptyPlaceholder2.underlineText()
        binding.directoriesFastscroller.beVisibleIf(binding.directoriesEmptyPlaceholder.isGone())
    }

    private fun setupAdapter(
        dirs: ArrayList<Directory>,
        textToSearch: String? = null,
        forceRecreate: Boolean = false,
        immediate: Boolean = false,
        request: HiddenMediaRequest = directoryRequest()
    ) {
        if (!isDirectoryRequestCurrent(request)) return
        // Copy mutable scan results before the scanner advances to the next folder.
        val snapshot = dirs.mapTo(ArrayList()) { it.copy() }
        runOnUiThread {
            if (!isDirectoryUiRequestCurrent(request)) return@runOnUiThread
            val allSnapshot = mDirs.mapTo(ArrayList()) { it.copy() }
            val requestedQuery = binding.mainMenu.getCurrentQuery()
            val query = textToSearch ?: requestedQuery
            mRecreateDirectoryAdapter = mRecreateDirectoryAdapter || forceRecreate
            val prefix = mCurrentPathPrefix
            val firstContent = binding.directoriesGrid.adapter == null
            mDirectoryPublication.submit(immediate || firstContent || forceRecreate) {
                if (!isDirectoryRequestCurrent(request)) return@submit
                val visible = prepareDirectoryProjection(snapshot, allSnapshot, prefix, query)
                runOnUiThread apply@{
                    if (!isDirectoryProjectionCurrent(request, prefix, requestedQuery)) return@apply
                    applyDirectoryProjection(snapshot, visible)
                }
            }
        }
    }

    private fun isDirectoryUiRequestCurrent(request: HiddenMediaRequest) =
        isDirectoryRequestCurrent(request) && !isDestroyed && !isFinishing

    private fun isDirectoryProjectionCurrent(request: HiddenMediaRequest, prefix: String, query: String) =
        isDirectoryUiRequestCurrent(request) && prefix == mCurrentPathPrefix
        && query == binding.mainMenu.getCurrentQuery()

    private fun prepareDirectoryProjection(
        snapshot: ArrayList<Directory>, allSnapshot: ArrayList<Directory>, prefix: String, query: String
    ): ArrayList<Directory> {
        val hiddenFilter = getHiddenMediaFilter()
        val canonicalPaths = HashMap<String, String>()
        val distinct = snapshot.filter { hiddenFilter.matches(it.tmb) }
            .distinctBy { canonicalPaths.getOrPut(it.path) { it.path.getDistinctPath() } }
            .toCollection(ArrayList())
        val visible = getDirsToShow(
            getSortedDirectories(distinct),
            allSnapshot.filterTo(ArrayList()) { hiddenFilter.matches(it.tmb) },
            prefix
        )
        if (query.isEmpty()) return visible
        return visible.filter { it.name.contains(query, true) }
            .sortedBy { !it.name.startsWith(query, true) }.toCollection(ArrayList())
    }

    private fun applyDirectoryProjection(snapshot: ArrayList<Directory>, visible: ArrayList<Directory>) {
        mDirsIgnoringSearch = snapshot
        if (binding.directoriesGrid.adapter == null || mRecreateDirectoryAdapter) {
            mRecreateDirectoryAdapter = false
            initZoomListener()
            binding.directoriesGrid.adapter = createDirectoryAdapter(visible)
            setupScrollDirection()
            if (config.viewTypeFolders == VIEW_TYPE_LIST && areSystemAnimationsEnabled) {
                binding.directoriesGrid.scheduleLayoutAnimation()
            }
        } else {
            getRecyclerAdapter()?.updateDirs(visible)
        }
        checkPlaceholderVisibility(visible)
        binding.directoriesGrid.removeCallbacks(mLayoutWorkaround)
        binding.directoriesGrid.postDelayed(mLayoutWorkaround, DIRECTORY_LAYOUT_WORKAROUND_DELAY)
    }

    private fun createDirectoryAdapter(visible: ArrayList<Directory>) = DirectoryAdapter(
        this, visible, this, binding.directoriesGrid,
        isPickIntent(intent) || isGetAnyContentIntent(intent), binding.directoriesRefreshLayout
    ) {
        directoryClicked(it as Directory)
    }.apply { setupZoomListener(mZoomListener) }

    private fun directoryClicked(directory: Directory) {
        val path = directory.path
        if (directory.subfoldersCount == 1 || !config.groupDirectSubfolders) {
            if (path != config.tempFolderPath) itemClicked(path)
        } else {
            mCurrentPathPrefix = path
            mOpenedSubfolders.add(path)
            setupAdapter(mDirs, "")
        }
    }

    private fun setupScrollDirection() {
        val scrollHorizontally =
            config.scrollHorizontally && config.viewTypeFolders == VIEW_TYPE_GRID
        binding.directoriesFastscroller.setScrollVertically(!scrollHorizontally)
    }

    private fun checkInvalidDirectories(dirs: ArrayList<Directory>, request: HiddenMediaRequest) {
        val invalidDirs = ArrayList<Directory>()
        val OTGPath = config.OTGPath
        dirs.filterTo(invalidDirs) {
            !it.areFavorites() && !it.isRecycleBin() && isInvalidMediaDirectory(it.path, OTGPath)
        }

        if (getFavoritePaths().isEmpty()) {
            val favoritesFolder = dirs.firstOrNull { it.areFavorites() }
            if (favoritesFolder != null) {
                invalidDirs.add(favoritesFolder)
            }
        }

        if (config.useRecycleBin) {
            try {
                val binFolder = dirs.firstOrNull { it.path == RECYCLE_BIN }
                if (binFolder != null && mediaDB.getDeletedMedia().isEmpty()) {
                    invalidDirs.add(binFolder)
                }
            } catch (ignored: Exception) {
            }
        }

        if (invalidDirs.isNotEmpty()) {
            dirs.removeAll(invalidDirs)
            if (isDirectoryRequestCurrent(request)) deleteInvalidDirectoryCache(invalidDirs, request.state)
        }
    }

    private fun isInvalidMediaDirectory(path: String, otgPath: String): Boolean {
        if (!getDoesFilePathExist(path, otgPath)) return true
        if (path == config.tempFolderPath || (isRPlus() && !isExternalStorageManager())) return false

        val children = if (isPathOnOTG(path)) getOTGFolderChildrenNames(path) else File(path).list()?.asList()
        return children?.any { name ->
            name != null && (name.isMediaFile() || (name.startsWith("img_", true) && File(name).isDirectory))
        } != true
    }

    private fun deleteInvalidDirectoryCache(invalidDirs: ArrayList<Directory>, filterState: HiddenMediaState) {
        val stateChanged = filterState != hiddenFilterState()
        if (filterState.isFilteringActive || config.isHiddenMediaFilteringActive || stateChanged) {
            return
        }
        invalidDirs.forEach {
            try {
                directoryDB.deleteDirPath(it.path)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun getCurrentlyDisplayedDirs() = getRecyclerAdapter()?.dirs ?: ArrayList()

    private fun setupLatestMediaId() {
        ensureBackgroundThread {
            if (hasPermission(PERMISSION_READ_STORAGE)) {
                mLatestMediaId = getLatestMediaId()
                mLatestMediaDateId = getLatestMediaByDateId()
            }
        }
    }

    private fun checkLastMediaChanged() {
        if (isDestroyed) {
            return
        }

        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getDirectories()
                    }
                } else {
                    mLastMediaHandler.removeCallbacksAndMessages(null)
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun checkRecycleBinItems() {
        if (config.useRecycleBin && config.lastBinCheck < System.currentTimeMillis() - DAY_SECONDS * 1000) {
            config.lastBinCheck = System.currentTimeMillis()
            Handler().postDelayed({
                ensureBackgroundThread {
                    try {
                        val filesToDelete = mediaDB.getOldRecycleBinItems(
                            System.currentTimeMillis() - MONTH_MILLISECONDS
                        )
                        filesToDelete.forEach {
                            if (File(it.path.replaceFirst(RECYCLE_BIN, recycleBinPath)).delete()) {
                                mediaDB.deleteMediumPath(it.path)
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }, 3000L)
        }
    }

    // exclude probably unwanted folders, for example facebook stickers are split between hundreds of separate folders like
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/175139712676531/209575122566323
    // /storage/emulated/0/Android/data/com.facebook.orca/files/stickers/497837993632037/499671223448714
    private fun excludeSpamFolders() {
        ensureBackgroundThread {
            try {
                val internalPath = internalStoragePath
                val checkedPaths = ArrayList<String>()
                val oftenRepeatedPaths = ArrayList<String>()
                val paths = mDirs
                    .map { it.path.removePrefix(internalPath) }
                    .toMutableList() as ArrayList<String>
                paths.forEach {
                    val parts = it.split("/")
                    var currentString = ""
                    for (i in 0 until parts.size) {
                        currentString += "${parts[i]}/"

                        if (!checkedPaths.contains(currentString)) {
                            val cnt = paths.count { it.startsWith(currentString) }
                            if (cnt > 50 && currentString.startsWith("/Android/data", true)) {
                                oftenRepeatedPaths.add(currentString)
                            }
                        }

                        checkedPaths.add(currentString)
                    }
                }

                val substringToRemove = oftenRepeatedPaths.filter {
                    val path = it
                    it == "/" || oftenRepeatedPaths.any { it != path && it.startsWith(path) }
                }

                oftenRepeatedPaths.removeAll(substringToRemove)
                val OTGPath = config.OTGPath
                oftenRepeatedPaths.forEach {
                    val file = File("$internalPath/$it")
                    if (getDoesFilePathExist(file.absolutePath, OTGPath)) {
                        config.addExcludedFolder(file.absolutePath)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun getFoldersWithMedia(path: String): HashSet<String> {
        val folders = HashSet<String>()
        try {
            val files = File(path).listFiles()
            if (files != null) {
                files.sortBy { !it.isDirectory }
                for (file in files) {
                    if (file.isDirectory && !file.startsWith("${config.internalStoragePath}/Android")) {
                        folders.addAll(getFoldersWithMedia(file.absolutePath))
                    } else if (file.isFile && file.isMediaFile()) {
                        folders.add(file.parent ?: "")
                        break
                    }
                }
            }
        } catch (ignored: Exception) {
        }

        return folders
    }

    override fun refreshItems() {
        refreshHiddenFilter()
    }

    override fun recheckPinnedFolders() {
        reloadDisplayedDirectories { movePinnedDirectoriesToFront(it) }
    }

    override fun updateDirectories(directories: ArrayList<Directory>) {
        val filterState = hiddenFilterState()
        ensureBackgroundThread {
            if (filterState != hiddenFilterState() || !canStoreDirectoryAggregates(filterState)) {
                return@ensureBackgroundThread
            }
            storeDirectoryItems(directories)
            removeInvalidDBDirectories()
        }
    }

    private fun isPickingMediaSubset() =
        mIsPickImageIntent || mIsPickVideoIntent || mIsGetImageContentIntent || mIsGetVideoContentIntent

    private fun canStoreDirectoryAggregates(filterState: HiddenMediaState) =
        !filterState.isFilteringActive && !config.isHiddenMediaFilteringActive
            && config.filterMedia == getDefaultFileFilter() && !isPickingMediaSubset()

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}

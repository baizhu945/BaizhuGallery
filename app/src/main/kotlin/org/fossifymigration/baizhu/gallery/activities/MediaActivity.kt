package org.fossifymigration.baizhu.gallery.activities

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.net.toUri
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import org.fossify.commons.dialogs.CreateNewFolderDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beInvisible
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getLatestMediaByDateId
import org.fossify.commons.extensions.getLatestMediaId
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.IS_FROM_GALLERY
import org.fossify.commons.helpers.REQUEST_EDIT_IMAGE
import org.fossify.commons.helpers.SORT_BY_RANDOM
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.models.RadioItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.commons.views.MyRecyclerView
import org.fossifymigration.baizhu.gallery.R
import org.fossifymigration.baizhu.gallery.adapters.MediaAdapter
import org.fossifymigration.baizhu.gallery.asynctasks.GetMediaAsynctask
import org.fossifymigration.baizhu.gallery.databases.GalleryDatabase
import org.fossifymigration.baizhu.gallery.databinding.ActivityMediaBinding
import org.fossifymigration.baizhu.gallery.dialogs.ChangeGroupingDialog
import org.fossifymigration.baizhu.gallery.dialogs.ChangeSortingDialog
import org.fossifymigration.baizhu.gallery.dialogs.ChangeViewTypeDialog
import org.fossifymigration.baizhu.gallery.dialogs.FilterMediaDialog
import org.fossifymigration.baizhu.gallery.dialogs.GrantAllFilesDialog
import org.fossifymigration.baizhu.gallery.extensions.config
import org.fossifymigration.baizhu.gallery.extensions.deleteMediaWithProgress
import org.fossifymigration.baizhu.gallery.extensions.directoryDB
import org.fossifymigration.baizhu.gallery.extensions.emptyAndDisableTheRecycleBin
import org.fossifymigration.baizhu.gallery.extensions.emptyTheRecycleBin
import org.fossifymigration.baizhu.gallery.extensions.getCachedMedia
import org.fossifymigration.baizhu.gallery.extensions.getHiddenMediaState
import org.fossifymigration.baizhu.gallery.extensions.getHumanizedFilename
import org.fossifymigration.baizhu.gallery.extensions.isDownloadsFolder
import org.fossifymigration.baizhu.gallery.extensions.launchAbout
import org.fossifymigration.baizhu.gallery.extensions.launchCamera
import org.fossifymigration.baizhu.gallery.extensions.launchSettings
import org.fossifymigration.baizhu.gallery.extensions.launchGesturePlayer
import org.fossifymigration.baizhu.gallery.extensions.mediaDB
import org.fossifymigration.baizhu.gallery.extensions.openPath
import org.fossifymigration.baizhu.gallery.extensions.openRecycleBin
import org.fossifymigration.baizhu.gallery.extensions.restoreRecycleBinPaths
import org.fossifymigration.baizhu.gallery.extensions.showRecycleBinEmptyingDialog
import org.fossifymigration.baizhu.gallery.extensions.showRestoreConfirmationDialog
import org.fossifymigration.baizhu.gallery.extensions.toggleHiddenOnly
import org.fossifymigration.baizhu.gallery.extensions.toggleOtherHidden
import org.fossifymigration.baizhu.gallery.extensions.tryDeleteFileDirItem
import org.fossifymigration.baizhu.gallery.extensions.updateWidgets
import org.fossifymigration.baizhu.gallery.helpers.DIRECTORY
import org.fossifymigration.baizhu.gallery.helpers.GET_ANY_INTENT
import org.fossifymigration.baizhu.gallery.helpers.GET_IMAGE_INTENT
import org.fossifymigration.baizhu.gallery.helpers.GET_VIDEO_INTENT
import org.fossifymigration.baizhu.gallery.helpers.GridSpacingItemDecoration
import org.fossifymigration.baizhu.gallery.helpers.HiddenMediaRequest
import org.fossifymigration.baizhu.gallery.helpers.HiddenMediaState
import org.fossifymigration.baizhu.gallery.helpers.GalleryExecutors
import org.fossifymigration.baizhu.gallery.helpers.GALLERY_QUERY_DEBOUNCE_MS
import org.fossifymigration.baizhu.gallery.helpers.GalleryPresentationChangeWatch
import org.fossifymigration.baizhu.gallery.helpers.LatestGalleryPresentationWork
import org.fossifymigration.baizhu.gallery.helpers.MediaFetcher
import org.fossifymigration.baizhu.gallery.helpers.copyGalleryPresentationMedium
import org.fossifymigration.baizhu.gallery.helpers.captureGalleryPresentationPolicy
import org.fossifymigration.baizhu.gallery.helpers.prepareGalleryPresentation
import org.fossifymigration.baizhu.gallery.helpers.IS_IN_RECYCLE_BIN
import org.fossifymigration.baizhu.gallery.helpers.MAX_COLUMN_COUNT
import org.fossifymigration.baizhu.gallery.helpers.PATH
import org.fossifymigration.baizhu.gallery.helpers.PICKED_PATHS
import org.fossifymigration.baizhu.gallery.helpers.RECYCLE_BIN
import org.fossifymigration.baizhu.gallery.helpers.SET_WALLPAPER_INTENT
import org.fossifymigration.baizhu.gallery.helpers.SHOW_ALL
import org.fossifymigration.baizhu.gallery.helpers.SHOW_FAVORITES
import org.fossifymigration.baizhu.gallery.helpers.SHOW_RECYCLE_BIN
import org.fossifymigration.baizhu.gallery.helpers.SHOW_TEMP_HIDDEN_DURATION
import org.fossifymigration.baizhu.gallery.helpers.SKIP_AUTHENTICATION
import org.fossifymigration.baizhu.gallery.helpers.SLIDESHOW_START_ON_ENTER
import org.fossifymigration.baizhu.gallery.helpers.VIDEO_PLAYER_APP
import org.fossifymigration.baizhu.gallery.helpers.VIDEO_PLAYER_SYSTEM
import org.fossifymigration.baizhu.gallery.interfaces.MediaOperationsListener
import org.fossifymigration.baizhu.gallery.models.Medium
import org.fossifymigration.baizhu.gallery.models.ThumbnailItem
import org.fossifymigration.baizhu.gallery.models.ThumbnailSection
import java.io.File
import java.io.IOException

class MediaActivity : SimpleActivity(), MediaOperationsListener {
    override var isSearchBarEnabled = true
    
    private val LAST_MEDIA_CHECK_PERIOD = 3000L

    private var mPath = ""
    private var mIsGetImageIntent = false
    private var mIsGetVideoIntent = false
    private var mIsGetAnyIntent = false
    private var mIsGettingMedia = false
    @Volatile
    private var mMediaGeneration = 0
    private var mQueryGeneration = 0
    private var mChangeWatch: GalleryPresentationChangeWatch? = null
    private var mSnapshotRevision = -1L
    private var mHasFreshSnapshot = false
    private var mRequestedRevision = -1L
    private var mSnapshotPolicy: List<Any?>? = null
    private var mRequestedPolicy: List<Any?> = emptyList()
    private var mReconciliationMedia = ArrayList<ThumbnailItem>()
    private fun presentationPolicy() = captureGalleryPresentationPolicy(this, if (mShowAll) SHOW_ALL else mPath)
    private val mSearchWork = LatestGalleryPresentationWork()
    private val mPresentationWork = LatestGalleryPresentationWork()
    private var mRequestedHiddenMediaState: HiddenMediaState? = null
    private var mAllowPickingMultiple = false
    private var mShowAll = false
    private var mLoadedInitialPhotos = false
    private var mShowLoadingIndicator = true
    private var mWasFullscreenViewOpen = false
    private var mLastSearchedText = ""
    private var mAppliedSearchQuery = ""
    private var mLatestMediaId = 0L
    private var mLatestMediaDateId = 0L
    private var mLastMediaHandler = Handler()
    private var mTempShowHiddenHandler = Handler()
    private var mCurrAsyncTask: GetMediaAsynctask? = null
    private var mZoomListener: MyRecyclerView.MyZoomListener? = null

    private var mStoredAnimateGifs = true
    private var mStoredCropThumbnails = true
    private var mStoredScrollHorizontally = true
    private var mStoredShowFileTypes = true
    private var mStoredRoundedCorners = false
    private var mStoredMarkFavoriteItems = true
    private var mStoredTextColor = 0
    private var mStoredPrimaryColor = 0
    private var mStoredThumbnailSpacing = 0

    private val binding by viewBinding(ActivityMediaBinding::inflate)

    companion object {
        var mMedia = ArrayList<ThumbnailItem>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        mChangeWatch = GalleryPresentationChangeWatch(applicationContext)

        intent.apply {
            mIsGetImageIntent = getBooleanExtra(GET_IMAGE_INTENT, false)
            mIsGetVideoIntent = getBooleanExtra(GET_VIDEO_INTENT, false)
            mIsGetAnyIntent = getBooleanExtra(GET_ANY_INTENT, false)
            mAllowPickingMultiple = getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }

        binding.mediaRefreshLayout.setOnRefreshListener { getMedia() }
        try {
            mPath = intent.getStringExtra(DIRECTORY) ?: ""
        } catch (e: Exception) {
            showErrorToast(e)
            finish()
            return
        }

        setupOptionsMenu()
        refreshMenuItems()
        storeStateVariables()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.mediaMenu),
            padBottomImeAndSystem = listOf(binding.mediaGrid)
        )

        if (mShowAll) {
            registerFileUpdateListener()
        }

        binding.mediaEmptyTextPlaceholder2.setOnClickListener {
            showFilterMediaDialog()
        }

        updateWidgets()
    }

    override fun onStart() {
        super.onStart()
        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        if (mRequestedHiddenMediaState != hiddenFilterState() || mSnapshotPolicy != presentationPolicy()) {
            clearPresentation()
        }
        updateMenuColors()
        if (mStoredAnimateGifs != config.animateGifs) {
            getMediaAdapter()?.updateAnimateGifs(config.animateGifs)
        }

        if (mStoredCropThumbnails != config.cropThumbnails) {
            getMediaAdapter()?.updateCropThumbnails(config.cropThumbnails)
        }

        if (mStoredScrollHorizontally != config.scrollHorizontally) {
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }

        if (mStoredShowFileTypes != config.showThumbnailFileTypes) {
            getMediaAdapter()?.updateShowFileTypes(config.showThumbnailFileTypes)
        }

        if (mStoredTextColor != getProperTextColor()) {
            getMediaAdapter()?.updateTextColor(getProperTextColor())
        }

        val primaryColor = getProperPrimaryColor()
        if (mStoredPrimaryColor != primaryColor) {
            getMediaAdapter()?.updatePrimaryColor()
        }

        if (
            mStoredThumbnailSpacing != config.thumbnailSpacing
            || mStoredRoundedCorners != config.fileRoundedCorners
            || mStoredMarkFavoriteItems != config.markFavoriteItems
        ) {
            binding.mediaGrid.adapter = null
            setupAdapter()
        }

        refreshMenuItems()

        binding.mediaFastscroller.updateColors(primaryColor)
        binding.mediaRefreshLayout.isEnabled = config.enablePullToRefresh
        getMediaAdapter()?.apply {
            dateFormat = config.dateFormat
            timeFormat = getTimeFormat()
        }

        binding.loadingIndicator.setIndicatorColor(getProperPrimaryColor())
        binding.mediaEmptyTextPlaceholder.setTextColor(getProperTextColor())
        binding.mediaEmptyTextPlaceholder2.setTextColor(getProperPrimaryColor())
        binding.mediaEmptyTextPlaceholder2.bringToFront()

        // do not refresh Random sorted files after opening a fullscreen image and going Back
        val shouldReload = mRequestedHiddenMediaState != hiddenFilterState() || !mHasFreshSnapshot ||
            mSnapshotRevision != GalleryPresentationChangeWatch.revision
        if (shouldReload || !mWasFullscreenViewOpen) {
            if (shouldSkipAuthentication()) {
                tryLoadGallery()
            } else {
                handleLockedFolderOpening(mPath) { success ->
                    if (success) {
                        tryLoadGallery()
                    } else {
                        finish()
                    }
                }
            }
        } else {
            checkLastMediaChanged()
            if (mAppliedSearchQuery != mLastSearchedText) searchQueryChanged(mLastSearchedText)
        }
    }

    override fun onPause() {
        super.onPause()
        mMediaGeneration++
        mSearchWork.cancel()
        mPresentationWork.cancel()
        mIsGettingMedia = false
        binding.mediaRefreshLayout.isRefreshing = false
        storeStateVariables()
        mLastMediaHandler.removeCallbacksAndMessages(null)

        if (!mMedia.isEmpty()) {
            mCurrAsyncTask?.stopFetching()
        }
    }

    override fun onStop() {
        super.onStop()

        if (config.temporarilyShowHidden || config.tempSkipDeleteConfirmation) {
            mTempShowHiddenHandler.postDelayed({
                config.temporarilyShowHidden = false
                config.tempSkipDeleteConfirmation = false
                config.tempSkipRecycleBin = false
            }, SHOW_TEMP_HIDDEN_DURATION)
        } else {
            mTempShowHiddenHandler.removeCallbacksAndMessages(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mChangeWatch?.close()
        mSearchWork.cancel()
        mPresentationWork.cancel()
        if (config.showAll && !isChangingConfigurations) {
            config.temporarilyShowHidden = false
            config.tempSkipDeleteConfirmation = false
            config.tempSkipRecycleBin = false
            unregisterFileUpdateListener()
            GalleryDatabase.destroyInstance()
        }

        mTempShowHiddenHandler.removeCallbacksAndMessages(null)
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mediaMenu.isSearchOpen) {
            binding.mediaMenu.closeSearch()
            true
        } else {
            if (config.showAll) {
                appLockManager.lock()
            }

            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == REQUEST_EDIT_IMAGE) {
            if (resultCode == RESULT_OK && resultData != null) {
                mMedia.clear()
                refreshItems()
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    private fun refreshMenuItems() {
        val isDefaultFolder = !config.defaultFolder.isEmpty()
                && File(config.defaultFolder).compareTo(File(mPath)) == 0

        binding.mediaMenu.requireToolbar().menu.apply {
            findItem(R.id.group).isVisible = !config.scrollHorizontally

            findItem(R.id.empty_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.empty_disable_recycle_bin).isVisible = mPath == RECYCLE_BIN
            findItem(R.id.restore_all_files).isVisible = mPath == RECYCLE_BIN

            findItem(R.id.folder_view).isVisible = mShowAll
            findItem(R.id.open_camera).isVisible = mShowAll
            findItem(R.id.about).isVisible = mShowAll
            findItem(R.id.create_new_folder).isVisible =
                !mShowAll && mPath != RECYCLE_BIN && mPath != FAVORITES
            findItem(R.id.open_recycle_bin).isVisible = config.useRecycleBin && mPath != RECYCLE_BIN

            findItem(R.id.show_only_hidden).isChecked = config.filterHiddenOnly
            findItem(R.id.include_other_hidden).isChecked = config.includeOtherHidden
            findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden
            findItem(R.id.stop_showing_hidden).isVisible =
                (!isRPlus() || isExternalStorageManager()) && config.temporarilyShowHidden

            findItem(R.id.set_as_default_folder).isVisible = !isDefaultFolder
            findItem(R.id.unset_as_default_folder).isVisible = isDefaultFolder

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            findItem(R.id.column_count).isVisible = viewType == VIEW_TYPE_GRID
            findItem(R.id.toggle_filename).isVisible = viewType == VIEW_TYPE_GRID
        }
    }

    private fun setupOptionsMenu() {
        binding.mediaMenu.requireToolbar().inflateMenu(R.menu.menu_media)
        binding.mediaMenu.toggleHideOnScroll(!config.scrollHorizontally)
        binding.mediaMenu.setupMenu()

        binding.mediaMenu.onSearchTextChangedListener = { text ->
            mLastSearchedText = text
            searchQueryChanged(text)
            binding.mediaRefreshLayout.isEnabled = text.isEmpty() && config.enablePullToRefresh
        }

        binding.mediaMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog()
                R.id.filter -> showFilterMediaDialog()
                R.id.empty_recycle_bin -> emptyRecycleBin()
                R.id.empty_disable_recycle_bin -> emptyAndDisableRecycleBin()
                R.id.restore_all_files -> restoreAllFiles()
                R.id.toggle_filename -> toggleFilenameVisibility()
                R.id.open_camera -> launchCamera()
                R.id.folder_view -> switchToFolderView()
                R.id.change_view_type -> changeViewType()
                R.id.group -> showGroupByDialog()
                R.id.create_new_folder -> createNewFolder()
                R.id.open_recycle_bin -> openRecycleBin()
                R.id.show_only_hidden -> toggleHiddenOnly { refreshHiddenFilter() }
                R.id.include_other_hidden -> toggleOtherHidden { refreshHiddenFilter() }
                R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
                R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
                R.id.column_count -> changeColumnCount()
                R.id.set_as_default_folder -> setAsDefaultFolder()
                R.id.unset_as_default_folder -> unsetAsDefaultFolder()
                R.id.slideshow -> startSlideshow()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun startSlideshow() {
        if (mMedia.isNotEmpty()) {
            hideKeyboard()
            Intent(this, ViewPagerActivity::class.java).apply {
                val item = mMedia.firstOrNull { it is Medium } as? Medium ?: return
                putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
                putExtra(PATH, item.path)
                putExtra(SHOW_ALL, mShowAll)
                putExtra(SLIDESHOW_START_ON_ENTER, true)
                startActivity(this)
            }
        }
    }

    private fun updateMenuColors() {
        binding.mediaMenu.updateColors()
    }

    private fun storeStateVariables() {
        mStoredTextColor = getProperTextColor()
        mStoredPrimaryColor = getProperPrimaryColor()
        config.apply {
            mStoredAnimateGifs = animateGifs
            mStoredCropThumbnails = cropThumbnails
            mStoredScrollHorizontally = scrollHorizontally
            mStoredShowFileTypes = showThumbnailFileTypes
            mStoredMarkFavoriteItems = markFavoriteItems
            mStoredThumbnailSpacing = thumbnailSpacing
            mStoredRoundedCorners = fileRoundedCorners
            mShowAll = showAll && mPath != RECYCLE_BIN
        }
    }

    private fun searchQueryChanged(text: String) {
        if (mRequestedHiddenMediaState != hiddenFilterState()) {
            getMedia()
            return
        }
        val queryGeneration = ++mQueryGeneration
        val generation = mMediaGeneration
        val state = hiddenFilterState()
        val policy = mRequestedPolicy
        val source = ArrayList(mMedia)
        mSearchWork.submit(GalleryExecutors.search, GALLERY_QUERY_DEBOUNCE_MS) {
            try {
                val grouped = prepareGalleryPresentation(
                    applicationContext, source, if (mShowAll) SHOW_ALL else mPath, text
                )
                    ?: return@submit
                runOnUiThread {
                    if (isDestroyed || generation != mMediaGeneration) return@runOnUiThread
                    if (state != hiddenFilterState() || policy != presentationPolicy()) {
                        clearPresentation()
                        mIsGettingMedia = false
                        getMedia()
                        return@runOnUiThread
                    }
                    if (queryGeneration != mQueryGeneration || text != mLastSearchedText) return@runOnUiThread
                    if (grouped.isEmpty()) {
                        binding.mediaEmptyTextPlaceholder.text =
                            getString(org.fossify.commons.R.string.no_items_found)
                        binding.mediaEmptyTextPlaceholder.beVisible()
                        binding.mediaFastscroller.beGone()
                    } else {
                        binding.mediaEmptyTextPlaceholder.beGone()
                        binding.mediaFastscroller.beVisible()
                    }

                    mAppliedSearchQuery = text
                    handleGridSpacing(grouped)
                    getMediaAdapter()?.updateMedia(grouped)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun tryLoadGallery() {
        requestMediaPermissions {
            val dirName = when (mPath) {
                FAVORITES -> getString(org.fossify.commons.R.string.favorites)
                RECYCLE_BIN -> getString(org.fossify.commons.R.string.recycle_bin)
                config.OTGPath -> getString(org.fossify.commons.R.string.usb)
                else -> getHumanizedFilename(mPath)
            }

            val searchHint = if (mShowAll) {
                getString(org.fossify.commons.R.string.search_files)
            } else {
                getString(org.fossify.commons.R.string.search_in_placeholder, dirName)
            }

            binding.mediaMenu.updateHintText(searchHint)
            if (!mShowAll) {
                binding.mediaMenu.toggleForceArrowBackIcon(true)
                binding.mediaMenu.onNavigateBackClickListener = {
                    performDefaultBack()
                }
            }

            if (mShowLoadingIndicator) {
                binding.loadingIndicator.show()
                mShowLoadingIndicator = false
            }

            getMedia()
            setupLayoutManager()
        }
    }

    private fun getMediaAdapter() = binding.mediaGrid.adapter as? MediaAdapter

    private fun setupAdapter() {
        if (!mShowAll && mSnapshotPolicy != null && isDirEmpty()) {
            return
        }

        val currAdapter = binding.mediaGrid.adapter
        if (currAdapter == null) {
            initZoomListener()
            MediaAdapter(
                activity = this,
                media = mMedia.clone() as ArrayList<ThumbnailItem>,
                listener = this,
                isAGetIntent = mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent,
                allowMultiplePicks = mAllowPickingMultiple,
                path = mPath,
                recyclerView = binding.mediaGrid
            ) {
                if (it is Medium && !isFinishing) {
                    itemClicked(it.path)
                }
            }.apply {
                setupZoomListener(mZoomListener)
                binding.mediaGrid.adapter = this
            }

            val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
            if (viewType == VIEW_TYPE_LIST && areSystemAnimationsEnabled) {
                binding.mediaGrid.scheduleLayoutAnimation()
            }

            setupLayoutManager()
            handleGridSpacing()
        } else if (mLastSearchedText.isEmpty()) {
            mAppliedSearchQuery = ""
            (currAdapter as MediaAdapter).updateMedia(mMedia)
            handleGridSpacing()
        } else {
            searchQueryChanged(mLastSearchedText)
        }

        setupScrollDirection()
    }

    private fun setupScrollDirection() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        val scrollHorizontally = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
        binding.mediaFastscroller.setScrollVertically(!scrollHorizontally)
    }

    private fun checkLastMediaChanged() {
        if (isDestroyed || config.getFolderSorting(mPath) and SORT_BY_RANDOM != 0) {
            return
        }

        mLastMediaHandler.removeCallbacksAndMessages(null)
        mLastMediaHandler.postDelayed({
            ensureBackgroundThread {
                val mediaId = getLatestMediaId()
                val mediaDateId = getLatestMediaByDateId()
                if (mLatestMediaId != mediaId || mLatestMediaDateId != mediaDateId) {
                    mLatestMediaId = mediaId
                    mLatestMediaDateId = mediaDateId
                    runOnUiThread {
                        getMedia()
                    }
                } else {
                    checkLastMediaChanged()
                }
            }
        }, LAST_MEDIA_CHECK_PERIOD)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, false, true, mPath) {
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun showFilterMediaDialog() {
        FilterMediaDialog(this) {
            mCurrAsyncTask?.stopFetching()
            mIsGettingMedia = false
            clearPresentation()
            mLoadedInitialPhotos = false
            binding.mediaRefreshLayout.isRefreshing = true
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun emptyRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyTheRecycleBin {
                finish()
            }
        }
    }

    private fun emptyAndDisableRecycleBin() {
        showRecycleBinEmptyingDialog {
            emptyAndDisableTheRecycleBin {
                finish()
            }
        }
    }

    private fun restoreAllFiles() {
        val paths = mMedia.filter { it is Medium }.map { (it as Medium).path } as ArrayList<String>
        showRestoreConfirmationDialog(paths.size) {
            restoreRecycleBinPaths(paths) {
                ensureBackgroundThread {
                    directoryDB.deleteDirPath(RECYCLE_BIN)
                }
                finish()
            }
        }
    }

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        getMediaAdapter()?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun switchToFolderView() {
        hideKeyboard()
        config.showAll = false
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun changeViewType() {
        ChangeViewTypeDialog(this, false, mPath) {
            refreshMenuItems()
            setupLayoutManager()
            binding.mediaGrid.adapter = null
            setupAdapter()
        }
    }

    private fun showGroupByDialog() {
        ChangeGroupingDialog(this, mPath) {
            mLoadedInitialPhotos = false
            binding.mediaGrid.adapter = null
            getMedia()
        }
    }

    private fun deleteDirectoryIfEmpty() {
        if (config.deleteEmptyFolders && !config.isHiddenMediaFilteringActive) {
            val fileDirItem = FileDirItem(mPath, mPath.getFilenameFromPath(), true)
            if (!fileDirItem.isDownloadsFolder() && fileDirItem.isDirectory) {
                ensureBackgroundThread {
                    if (fileDirItem.getProperFileCount(this, true) == 0) {
                        tryDeleteFileDirItem(fileDirItem, true, true)
                    }
                }
            }
        }
    }

    private fun getMedia() {
        val state = hiddenFilterState()
        val policy = presentationPolicy()
        if (mRequestedHiddenMediaState != state || mRequestedPolicy != policy) {
            mMediaGeneration++
            mCurrAsyncTask?.stopFetching()
            mIsGettingMedia = false
            mLoadedInitialPhotos = false
            clearPresentation()
        }
        mRequestedHiddenMediaState = state
        if (mIsGettingMedia) {
            return
        }

        mIsGettingMedia = true
        mReconciliationMedia = ArrayList(mMedia)
        mRequestedRevision = GalleryPresentationChangeWatch.revision
        mRequestedPolicy = policy
        val request = HiddenMediaRequest(++mMediaGeneration, state)
        if (mLoadedInitialPhotos) {
            startAsyncTask(request)
        } else {
            getCachedMedia(
                mPath,
                mIsGetVideoIntent && !mIsGetImageIntent,
                mIsGetImageIntent && !mIsGetVideoIntent
            ) {
                val cached = it
                runOnUiThread {
                    if (request.generation != mMediaGeneration || isDestroyed || isFinishing) return@runOnUiThread
                    if (reloadIfPolicyChanged(request, policy)) return@runOnUiThread
                    mReconciliationMedia = ArrayList(cached)
                    if (cached.isEmpty()) {
                        binding.mediaRefreshLayout.isRefreshing = true
                    } else {
                        gotMedia(cached, true, request)
                    }
                    startAsyncTask(request)
                }
            }
        }

        mLoadedInitialPhotos = true
    }

    private fun hiddenFilterState() = getHiddenMediaState()

    private fun clearPresentation() {
        mSearchWork.cancel()
        mPresentationWork.cancel()
        mQueryGeneration++
        mHasFreshSnapshot = false
        mSnapshotPolicy = null
        mReconciliationMedia = arrayListOf()
        mMedia = arrayListOf()
        binding.mediaGrid.beInvisible()
        getMediaAdapter()?.updateMedia(arrayListOf())
    }

    private fun isMediaRequestCurrent(request: HiddenMediaRequest) =
        request.isCurrent(mMediaGeneration, hiddenFilterState())

    private fun reloadIfPolicyChanged(request: HiddenMediaRequest, policy: List<Any?>): Boolean {
        if (isMediaRequestCurrent(request) && policy == presentationPolicy()) return false
        getMedia()
        return true
    }

    private fun startAsyncTask(request: HiddenMediaRequest) {
        if (request.generation != mMediaGeneration) return
        val policy = mRequestedPolicy
        if (reloadIfPolicyChanged(request, policy)) return
        mCurrAsyncTask?.stopFetching()
        mCurrAsyncTask = GetMediaAsynctask(
            context = applicationContext,
            mPath = mPath,
            isPickImage = mIsGetImageIntent && !mIsGetVideoIntent,
            isPickVideo = mIsGetVideoIntent && !mIsGetImageIntent,
            showAll = mShowAll
        ) { newMedia ->
            runOnUiThread {
                if (request.generation != mMediaGeneration || isDestroyed || isFinishing) return@runOnUiThread
                if (reloadIfPolicyChanged(request, policy)) return@runOnUiThread
                val oldMedia = ArrayList(mReconciliationMedia)
                gotMedia(newMedia, false, request)
                reconcileMediaCache(oldMedia, newMedia, request)
            }
        }

        mCurrAsyncTask!!.executeOnExecutor(GalleryExecutors.media)
    }

    private fun reconcileMediaCache(
        oldMedia: List<ThumbnailItem>, newMedia: List<ThumbnailItem>, request: HiddenMediaRequest
    ) {
        GalleryExecutors.database.execute {
            try {
                val newPaths = newMedia.filterIsInstance<Medium>().mapTo(HashSet()) { it.path }
                val canReconcile = isMediaRequestCurrent(request) &&
                    !request.state.isFilteringActive && !config.isHiddenMediaFilteringActive
                if (!canReconcile) return@execute
                oldMedia.filterIsInstance<Medium>().filter { it.path !in newPaths }.forEach {
                    if (!isMediaRequestCurrent(request)) return@forEach
                    // Absence from a filtered favorite list does not establish deletion.
                    if (!getDoesFilePathExist(it.path)) mediaDB.deleteMediumPath(it.path)
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun isDirEmpty(): Boolean {
        return if (!config.isHiddenMediaFilteringActive && mMedia.isEmpty() && config.filterMedia > 0) {
            if (mPath != FAVORITES && mPath != RECYCLE_BIN) {
                deleteDirectoryIfEmpty()
                deleteDBDirectory()
            }

            if (mPath == FAVORITES) {
                ensureBackgroundThread {
                    directoryDB.deleteDirPath(FAVORITES)
                }
            }

            if (mPath == RECYCLE_BIN) {
                binding.mediaEmptyTextPlaceholder.setText(org.fossify.commons.R.string.no_items_found)
                binding.mediaEmptyTextPlaceholder.beVisible()
                binding.mediaEmptyTextPlaceholder2.beGone()
            } else {
                finish()
            }

            true
        } else {
            false
        }
    }

    private fun deleteDBDirectory() {
        ensureBackgroundThread {
            try {
                directoryDB.deleteDirPath(mPath)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun createNewFolder() {
        CreateNewFolderDialog(this, mPath) {
            config.tempFolderPath = it
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
        mMediaGeneration++
        mCurrAsyncTask?.stopFetching()
        mIsGettingMedia = false
        mLoadedInitialPhotos = false
        clearPresentation()
        getMedia()
        refreshMenuItems()
    }

    private fun setupLayoutManager() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        layoutManager.spanCount = config.mediaColumnCnt
        val adapter = getMediaAdapter()
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter?.isASectionTitle(position) == true) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
        binding.mediaRefreshLayout.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        mZoomListener = null
    }

    private fun handleGridSpacing(media: ArrayList<ThumbnailItem> = mMedia) {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            val spanCount = config.mediaColumnCnt
            val spacing = config.thumbnailSpacing
            val useGridPosition = media.firstOrNull() is ThumbnailSection

            var currentGridDecoration: GridSpacingItemDecoration? = null
            if (binding.mediaGrid.itemDecorationCount > 0) {
                currentGridDecoration =
                    binding.mediaGrid.getItemDecorationAt(0) as GridSpacingItemDecoration
                currentGridDecoration.items = media
            }

            val newGridDecoration = GridSpacingItemDecoration(
                spanCount = spanCount,
                spacing = spacing,
                isScrollingHorizontally = config.scrollHorizontally,
                addSideSpacing = config.fileRoundedCorners,
                items = media,
                useGridPosition = useGridPosition
            )
            if (!matchesGridGeometry(currentGridDecoration, newGridDecoration)) {
                if (currentGridDecoration != null) {
                    binding.mediaGrid.removeItemDecoration(currentGridDecoration)
                }
                binding.mediaGrid.addItemDecoration(newGridDecoration)
            }
        }
    }

    private fun matchesGridGeometry(old: GridSpacingItemDecoration?, new: GridSpacingItemDecoration): Boolean {
        if (old == null) return false
        val sizeMatches = old.spanCount == new.spanCount && old.spacing == new.spacing
        val directionMatches = old.isScrollingHorizontally == new.isScrollingHorizontally
        val edgesMatch = old.addSideSpacing == new.addSideSpacing && old.useGridPosition == new.useGridPosition
        return sizeMatches && directionMatches && edgesMatch
    }

    private fun initZoomListener() {
        val viewType = config.getFolderViewType(if (mShowAll) SHOW_ALL else mPath)
        if (viewType == VIEW_TYPE_GRID) {
            val layoutManager = binding.mediaGrid.layoutManager as MyGridLayoutManager
            mZoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getMediaAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getMediaAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            mZoomListener = null
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

        val currentColumnCount = (binding.mediaGrid.layoutManager as MyGridLayoutManager).spanCount
        RadioGroupDialog(this, items, currentColumnCount) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.mediaColumnCnt = newColumnCount
                columnCountChanged()
            }
        }
    }

    private fun increaseColumnCount() {
        config.mediaColumnCnt += 1
        columnCountChanged()
    }

    private fun reduceColumnCount() {
        config.mediaColumnCnt -= 1
        columnCountChanged()
    }

    private fun columnCountChanged() {
        (binding.mediaGrid.layoutManager as MyGridLayoutManager).spanCount = config.mediaColumnCnt
        handleGridSpacing()
        refreshMenuItems()
        getMediaAdapter()?.apply {
            notifyItemRangeChanged(0, media.size)
        }
    }

    private fun isSetWallpaperIntent() = intent.getBooleanExtra(SET_WALLPAPER_INTENT, false)

    private fun itemClicked(path: String) {
        hideKeyboard()
        if (isSetWallpaperIntent()) {
            toast(R.string.setting_wallpaper)

            val wantedWidth = wallpaperDesiredMinimumWidth
            val wantedHeight = wallpaperDesiredMinimumHeight
            val ratio = wantedWidth.toFloat() / wantedHeight

            val options = RequestOptions()
                .override((wantedWidth * ratio).toInt(), wantedHeight)
                .fitCenter()

            Glide.with(this)
                .asBitmap()
                .load(File(path))
                .apply(options)
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        try {
                            WallpaperManager.getInstance(applicationContext).setBitmap(resource)
                            setResult(RESULT_OK)
                        } catch (ignored: IOException) {
                        }

                        finish()
                    }
                })
        } else if (mIsGetImageIntent || mIsGetVideoIntent || mIsGetAnyIntent) {
            Intent().apply {
                data = path.toUri()
                setResult(RESULT_OK, this)
            }
            finish()
        } else {
            mWasFullscreenViewOpen = true
            if (!path.isVideoFast()) {
                openInViewPager(path)
                return
            }

            when (config.videoPlayerType) {
                VIDEO_PLAYER_SYSTEM -> openSystemDefaultPlayer(path)
                VIDEO_PLAYER_APP -> if (config.gestureVideoPlayer) launchGesturePlayer(path) else openInViewPager(path)
                else -> openInViewPager(path) // unreachable by design
            }
        }
    }

    private fun openInViewPager(path: String) {
        Intent(this, ViewPagerActivity::class.java).apply {
            putExtra(SKIP_AUTHENTICATION, shouldSkipAuthentication())
            putExtra(PATH, path)
            putExtra(SHOW_ALL, mShowAll)
            putExtra(SHOW_FAVORITES, mPath == FAVORITES)
            putExtra(SHOW_RECYCLE_BIN, mPath == RECYCLE_BIN)
            putExtra(IS_FROM_GALLERY, true)
            startActivity(this)
        }
    }

    private fun openSystemDefaultPlayer(path: String) {
        openPath(
            path = path,
            forceChooser = false,
            extras = hashMapOf(SHOW_FAVORITES to (mPath == FAVORITES)).apply {
                if (path.startsWith(recycleBinPath)) put(IS_IN_RECYCLE_BIN, true)
                if (shouldSkipAuthentication()) put(SKIP_AUTHENTICATION, true)
            }
        )
    }

    private data class MediaPublicationRequest(
        val load: HiddenMediaRequest, val policy: List<Any?>, val revision: Long, val fromCache: Boolean
    )

    private fun gotMedia(media: ArrayList<ThumbnailItem>, isFromCache: Boolean, load: HiddenMediaRequest) {
        runOnUiThread {
            if (!isMediaRequestCurrent(load) || isDestroyed || isFinishing) return@runOnUiThread
            val source = ArrayList(media)
            val requestedRevision = mRequestedRevision
            val policy = mRequestedPolicy
            val preserveRandomOrder = shouldPreserveRandomOrder(isFromCache, policy)
            val previous = if (preserveRandomOrder) ArrayList(mMedia) else null
            mPresentationWork.submit(GalleryExecutors.media) {
                val prepared = prepareMediaPublication(source, isFromCache, previous) ?: return@submit
                if (Thread.currentThread().isInterrupted) return@submit
                val latestId = getLatestMediaId()
                val latestDateId = getLatestMediaByDateId()
                val request = MediaPublicationRequest(load, policy, requestedRevision, isFromCache)
                runOnUiThread { publishMediaPresentation(request, prepared, latestId, latestDateId) }
                if (!isFromCache) storePreparedMedia(request, prepared)
            }
        }
    }

    private fun shouldPreserveRandomOrder(isFromCache: Boolean, policy: List<Any?>): Boolean {
        if (isFromCache || !mWasFullscreenViewOpen || mSnapshotPolicy != policy) return false
        return config.getFolderSorting(if (mShowAll) SHOW_ALL else mPath) and SORT_BY_RANDOM != 0
    }

    private fun prepareMediaPublication(
        source: ArrayList<ThumbnailItem>, isFromCache: Boolean, previous: List<ThumbnailItem>?
    ): ArrayList<ThumbnailItem>? {
        val path = if (mShowAll) SHOW_ALL else mPath
        if (isFromCache) return prepareGalleryPresentation(applicationContext, source, path)
        if (previous == null) return source
        val positions = previous.filterIsInstance<Medium>().mapIndexed { index, item -> item.path to index }.toMap()
        val detached = source.filterIsInstance<Medium>().mapTo(ArrayList()) { it.copyGalleryPresentationMedium() }
        detached.sortBy { positions[it.path] ?: Int.MAX_VALUE }
        return MediaFetcher(applicationContext).groupMedia(detached, path)
    }

    private fun publishMediaPresentation(
        request: MediaPublicationRequest, prepared: ArrayList<ThumbnailItem>, latestId: Long, latestDateId: Long
    ) {
        if (request.load.generation != mMediaGeneration || isDestroyed || isFinishing) return
        if (reloadIfPolicyChanged(request.load, request.policy)) return
        if (!request.fromCache) {
            mIsGettingMedia = false
            mHasFreshSnapshot = true
        }
        mLatestMediaId = latestId
        mLatestMediaDateId = latestDateId
        mMedia = prepared
        mSnapshotRevision = request.revision
        mSnapshotPolicy = request.policy
        mQueryGeneration++
        mSearchWork.cancel()
        checkLastMediaChanged()
        binding.loadingIndicator.hide()
        binding.mediaRefreshLayout.isRefreshing = false
        val showEmpty = mMedia.isEmpty() && !request.fromCache
        binding.mediaEmptyTextPlaceholder.beVisibleIf(showEmpty)
        binding.mediaEmptyTextPlaceholder2.beVisibleIf(showEmpty)
        updateEmptyMediaPresentation()
        binding.mediaFastscroller.beVisibleIf(binding.mediaEmptyTextPlaceholder.isGone())
        setupAdapter()
        binding.mediaGrid.beVisible()
    }

    private fun storePreparedMedia(request: MediaPublicationRequest, prepared: List<ThumbnailItem>) {
        val mediaToInsert = prepared.filterIsInstance<Medium>().filter { it.deletedTS == 0L }
        GalleryExecutors.database.execute {
            try {
                if (isMediaRequestCurrent(request.load)) mediaDB.insertAll(mediaToInsert)
            } catch (e: Exception) {
            }
        }
    }

    private fun updateEmptyMediaPresentation() {
        if (config.filterHiddenOnly && !config.includeOtherHidden) {
            binding.mediaEmptyTextPlaceholder.text = getString(R.string.no_my_hidden_media)
            binding.mediaEmptyTextPlaceholder2.text = getString(R.string.include_other_hidden)
            binding.mediaEmptyTextPlaceholder2.setOnClickListener {
                toggleOtherHidden { refreshHiddenFilter() }
            }
        } else {
            binding.mediaEmptyTextPlaceholder.text = getString(R.string.no_media_with_filters)
            binding.mediaEmptyTextPlaceholder2.text = getString(R.string.change_filters_underlined)
            binding.mediaEmptyTextPlaceholder2.setOnClickListener {
                showFilterMediaDialog()
            }
        }
    }

    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>, skipRecycleBin: Boolean) {
        val selected = fileDirItems.filterTo(ArrayList()) { !it.isDirectory && it.path.isMediaFile() }
        if (selected.isEmpty()) return
        deleteMediaWithProgress(selected, skipRecycleBin) { refreshItems() }
    }

    private fun shouldSkipAuthentication(): Boolean {
        return intent.getBooleanExtra(SKIP_AUTHENTICATION, false)
    }

    override fun refreshItems() {
        refreshHiddenFilter()
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        Intent().apply {
            putExtra(PICKED_PATHS, paths)
            setResult(RESULT_OK, this)
        }
        finish()
    }

    override fun updateMediaGridDecoration(media: ArrayList<ThumbnailItem>) {
        var currentGridPosition = 0
        media.forEach {
            if (it is Medium) {
                it.gridPosition = currentGridPosition++
            } else if (it is ThumbnailSection) {
                currentGridPosition = 0
            }
        }

        if (binding.mediaGrid.itemDecorationCount > 0) {
            val currentGridDecoration =
                binding.mediaGrid.getItemDecorationAt(0) as GridSpacingItemDecoration
            currentGridDecoration.items = media
        }
    }

    private fun setAsDefaultFolder() {
        config.defaultFolder = mPath
        refreshMenuItems()
    }

    private fun unsetAsDefaultFolder() {
        config.defaultFolder = ""
        refreshMenuItems()
    }
}

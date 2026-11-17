package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyGridLayoutManager
import org.fossify.gallery.R
import org.fossify.gallery.adapters.MediaAdapter
import org.fossify.gallery.asynctasks.GetMediaAsynctask
import org.fossify.gallery.databinding.ActivitySearchBinding
import org.fossify.gallery.extensions.*
import org.fossify.gallery.helpers.GalleryExecutors
import org.fossify.gallery.helpers.GALLERY_QUERY_DEBOUNCE_MS
import org.fossify.gallery.helpers.GalleryPresentationChangeWatch
import org.fossify.gallery.helpers.HiddenMediaState
import org.fossify.gallery.helpers.LatestGalleryPresentationWork
import org.fossify.gallery.helpers.prepareGalleryPresentation
import org.fossify.gallery.helpers.captureGalleryPresentationPolicy
import org.fossify.gallery.helpers.GridSpacingItemDecoration
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.VIDEO_PLAYER_APP
import org.fossify.gallery.helpers.VIDEO_PLAYER_SYSTEM
import org.fossify.gallery.interfaces.MediaOperationsListener
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

class SearchActivity : SimpleActivity(), MediaOperationsListener {
    override var isSearchBarEnabled = true
    
    private var mLastSearchedText = ""

    private var mCurrAsyncTask: GetMediaAsynctask? = null
    private var mAllMedia = ArrayList<ThumbnailItem>()
    private var mRequestGeneration = 0
    private var mQueryGeneration = 0
    private var mSnapshotState: HiddenMediaState? = null
    private var mChangeWatch: GalleryPresentationChangeWatch? = null
    private var mSnapshotRevision = -1L
    private var mSnapshotPolicy: List<Any?>? = null
    private fun presentationPolicy() = captureGalleryPresentationPolicy(this, SHOW_ALL)
    private val mSearchWork = LatestGalleryPresentationWork()
    private val mLoadWork = LatestGalleryPresentationWork()

    private val binding by viewBinding(ActivitySearchBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        mChangeWatch = GalleryPresentationChangeWatch(applicationContext)
        setupOptionsMenu()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.searchMenu),
            padBottomImeAndSystem = listOf(binding.searchGrid)
        )
        binding.searchEmptyTextPlaceholder.setTextColor(getProperTextColor())
        binding.searchFastscroller.updateColors(getProperPrimaryColor())
    }

    override fun onResume() {
        super.onResume()
        if (!canReusePresentation()) {
            invalidatePresentation()
            getAllMedia()
        }
        updateMenuColors()
    }

    private fun canReusePresentation(): Boolean {
        if (mSnapshotPolicy == null) return false
        val policyMatches = mSnapshotState == hiddenFilterState() && mSnapshotPolicy == presentationPolicy()
        return policyMatches && mSnapshotRevision == GalleryPresentationChangeWatch.revision
    }

    override fun onDestroy() {
        super.onDestroy()
        mChangeWatch?.close()
        mCurrAsyncTask?.stopFetching()
        mRequestGeneration++
        mQueryGeneration++
        mSearchWork.cancel()
        mLoadWork.cancel()
    }

    private fun invalidatePresentation() {
        mCurrAsyncTask?.stopFetching()
        mSearchWork.cancel()
        mLoadWork.cancel()
        mRequestGeneration++
        mQueryGeneration++
        mSnapshotState = hiddenFilterState()
        mSnapshotPolicy = null
        mAllMedia = arrayListOf()
        binding.searchGrid.beInvisible()
        getMediaAdapter()?.updateMedia(arrayListOf())
    }

    private fun setupOptionsMenu() {
        binding.searchMenu.requireToolbar().inflateMenu(R.menu.menu_search)
        binding.searchMenu.toggleHideOnScroll(true)
        binding.searchMenu.setupMenu()
        binding.searchMenu.toggleForceArrowBackIcon(true)
        binding.searchMenu.focusView()
        binding.searchMenu.updateHintText(getString(org.fossify.commons.R.string.search_files))

        binding.searchMenu.onNavigateBackClickListener = {
            if (binding.searchMenu.getCurrentQuery().isEmpty()) {
                finish()
            } else {
                binding.searchMenu.closeSearch()
            }
        }

        binding.searchMenu.onSearchTextChangedListener = { text ->
            mLastSearchedText = text
            textChanged(text)
        }

        binding.searchMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.toggle_filename -> toggleFilenameVisibility()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateMenuColors() {
        binding.searchMenu.updateColors()
    }

    private fun textChanged(text: String) {
        if (mSnapshotState != hiddenFilterState()) {
            invalidatePresentation()
            getAllMedia()
            return
        }
        val queryGeneration = ++mQueryGeneration
        val requestGeneration = mRequestGeneration
        val filterState = hiddenFilterState()
        val allMedia = ArrayList(mAllMedia)
        mSearchWork.submit(GalleryExecutors.search, GALLERY_QUERY_DEBOUNCE_MS) {
            try {
                val grouped = prepareGalleryPresentation(applicationContext, allMedia, SHOW_ALL, text) ?: return@submit
                runOnUiThread {
                    val isCurrentRequest = requestGeneration == mRequestGeneration &&
                        queryGeneration == mQueryGeneration
                    if (isDestroyed || !isCurrentRequest) return@runOnUiThread
                    if (filterState != hiddenFilterState()) {
                        invalidatePresentation()
                        getAllMedia()
                        return@runOnUiThread
                    }
                    if (text != mLastSearchedText || isDestroyed) return@runOnUiThread
                    val visible = grouped
                    if (visible.isEmpty()) {
                        binding.searchEmptyTextPlaceholder.text = getString(org.fossify.commons.R.string.no_items_found)
                        binding.searchEmptyTextPlaceholder.beVisible()
                    } else {
                        binding.searchEmptyTextPlaceholder.beGone()
                    }

                    handleGridSpacing(visible)
                    getMediaAdapter()?.updateMedia(visible)
                    binding.searchGrid.beVisible()
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun hiddenFilterState() = getHiddenMediaState()

    private fun setupAdapter() {
        val currAdapter = binding.searchGrid.adapter
        if (currAdapter == null) {
            val initial = if (mLastSearchedText.isEmpty()) mAllMedia else arrayListOf()
            MediaAdapter(this, initial, this, false, false, "", binding.searchGrid) {
                if (it is Medium) {
                    itemClicked(it.path)
                }
            }.apply {
                binding.searchGrid.adapter = this
            }
            setupLayoutManager()
            handleGridSpacing(initial)
            if (mLastSearchedText.isNotEmpty()) textChanged(mLastSearchedText)
        } else if (mLastSearchedText.isEmpty()) {
            (currAdapter as MediaAdapter).updateMedia(mAllMedia)
            handleGridSpacing(mAllMedia)
        } else {
            textChanged(mLastSearchedText)
        }

        setupScrollDirection()
        binding.searchGrid.beVisible()
    }

    private fun handleGridSpacing(media: ArrayList<ThumbnailItem>) {
        val viewType = config.getFolderViewType(SHOW_ALL)
        if (viewType == VIEW_TYPE_GRID) {
            val spanCount = config.mediaColumnCnt
            val spacing = config.thumbnailSpacing
            val current = if (binding.searchGrid.itemDecorationCount > 0) {
                binding.searchGrid.getItemDecorationAt(0) as? GridSpacingItemDecoration
            } else null
            if (current != null && matchesGridGeometry(current)) {
                current.items = media
            } else {
                current?.let { binding.searchGrid.removeItemDecoration(it) }
                binding.searchGrid.addItemDecoration(
                    GridSpacingItemDecoration(
                        spanCount, spacing, config.scrollHorizontally, config.fileRoundedCorners, media, true
                    )
                )
            }
        }
    }

    private fun matchesGridGeometry(decoration: GridSpacingItemDecoration): Boolean {
        val sizeMatches = decoration.spanCount == config.mediaColumnCnt && decoration.spacing == config.thumbnailSpacing
        val directionMatches = decoration.isScrollingHorizontally == config.scrollHorizontally
        return sizeMatches && directionMatches && decoration.addSideSpacing == config.fileRoundedCorners
    }

    private fun getMediaAdapter() = binding.searchGrid.adapter as? MediaAdapter

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        getMediaAdapter()?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun itemClicked(path: String) {
        if (!path.isVideoFast()) {
            openInViewPager(path)
            return
        }

        when (config.videoPlayerType) {
            VIDEO_PLAYER_SYSTEM -> openPath(path = path, forceChooser = false)
            VIDEO_PLAYER_APP -> if (config.gestureVideoPlayer) launchGesturePlayer(path) else openInViewPager(path)
            else -> openInViewPager(path) // unreachable by design
        }
    }

    private fun openInViewPager(path: String) {
        Intent(this, ViewPagerActivity::class.java).apply {
            putExtra(PATH, path)
            putExtra(SHOW_ALL, false)
            startActivity(this)
        }
    }

    private fun setupLayoutManager() {
        val viewType = config.getFolderViewType(SHOW_ALL)
        if (viewType == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.searchGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.searchGrid.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.searchGrid.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
        val layoutManager = binding.searchGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
    }

    private fun setupScrollDirection() {
        val viewType = config.getFolderViewType(SHOW_ALL)
        val scrollHorizontally = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
        binding.searchFastscroller.setScrollVertically(!scrollHorizontally)
    }

    private fun getAllMedia() {
        val requestGeneration = mRequestGeneration
        val filterState = hiddenFilterState()
        getCachedMedia("") { cached ->
            runOnUiThread {
                if (requestGeneration != mRequestGeneration || isDestroyed) return@runOnUiThread
                if (filterState != hiddenFilterState()) {
                    invalidatePresentation()
                    getAllMedia()
                    return@runOnUiThread
                }
                mLoadWork.submit(GalleryExecutors.media) {
                    val prepared = prepareGalleryPresentation(applicationContext, cached, SHOW_ALL) ?: return@submit
                    runOnUiThread publish@{
                        if (requestGeneration != mRequestGeneration || isDestroyed) return@publish
                        if (filterState != hiddenFilterState()) {
                            invalidatePresentation()
                            getAllMedia()
                            return@publish
                        }
                        mAllMedia = prepared
                        setupAdapter()
                        startAsyncTask(false)
                    }
                }
            }
        }
    }

    private fun startAsyncTask(updateItems: Boolean) {
        val requestGeneration = ++mRequestGeneration
        val requestedRevision = GalleryPresentationChangeWatch.revision
        val policy = presentationPolicy()
        mQueryGeneration++
        mSearchWork.cancel()
        val filterState = hiddenFilterState()
        mCurrAsyncTask?.stopFetching()
        mCurrAsyncTask = GetMediaAsynctask(applicationContext, "", showAll = true) {
            runOnUiThread {
                if (requestGeneration != mRequestGeneration || isDestroyed) return@runOnUiThread
                if (filterState != hiddenFilterState() || policy != presentationPolicy()) {
                    invalidatePresentation()
                    startAsyncTask(false)
                    return@runOnUiThread
                }
                mAllMedia = it
                mSnapshotRevision = requestedRevision
                mSnapshotPolicy = policy
                if (updateItems) {
                    textChanged(mLastSearchedText)
                } else {
                    setupAdapter()
                }
            }
        }

        mCurrAsyncTask!!.executeOnExecutor(GalleryExecutors.media)
    }

    override fun refreshItems() {
        invalidatePresentation()
        startAsyncTask(false)
    }

    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>, skipRecycleBin: Boolean) {
        val selected = fileDirItems.filterTo(ArrayList()) { !it.isDirectory && it.path.isMediaFile() }
        if (selected.isEmpty()) return
        deleteMediaWithProgress(selected, skipRecycleBin) { refreshItems() }
    }

    override fun selectedPaths(paths: ArrayList<String>) {}

    override fun updateMediaGridDecoration(media: ArrayList<ThumbnailItem>) {}
}

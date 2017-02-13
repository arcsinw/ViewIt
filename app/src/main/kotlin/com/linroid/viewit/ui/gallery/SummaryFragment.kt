package com.linroid.viewit.ui.gallery

import android.content.Context
import android.os.Bundle
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import butterknife.bindView
import com.linroid.viewit.R
import com.linroid.viewit.data.model.CloudFavorite
import com.linroid.viewit.data.model.Favorite
import com.linroid.viewit.data.model.Image
import com.linroid.viewit.data.model.ImageTree
import com.linroid.viewit.data.repo.cloud.CloudFavoriteRepo
import com.linroid.viewit.ui.gallery.provider.*
import com.linroid.viewit.ui.path.PathManagerActivity
import com.linroid.viewit.ui.viewer.ImageViewerActivity
import com.linroid.viewit.utils.PathUtils
import com.linroid.viewit.widget.divider.CategoryItemDecoration
import com.trello.rxlifecycle.android.FragmentEvent
import com.trello.rxlifecycle.kotlin.bindToLifecycle
import com.trello.rxlifecycle.kotlin.bindUntilEvent
import me.drakeet.multitype.MultiTypeAdapter
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.io.File
import java.util.*
import javax.inject.Inject

/**
 * @author linroid <linroid@gmail.com>
 * @since 31/01/2017
 */
class SummaryFragment : GalleryChildFragment() {
    val SPAN_COUNT = 3

    private val items = ArrayList<Any>()
    private var adapter = MultiTypeAdapter(items)

    private lateinit var cloudFavoriteCategory: Category<CloudFavorite>
    private lateinit var favoriteCategory: Category<Favorite>
    private lateinit var treeCategory: Category<ImageTree>
    private lateinit var imageCategory: Category<Image>

    @Inject lateinit internal var cloudFavoriteRepo: CloudFavoriteRepo

    private val recyclerView: RecyclerView by bindView(R.id.recyclerView)

    companion object {
        fun newInstance(): SummaryFragment {
            val args = Bundle()
            val fragment = SummaryFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun provideLayoutId(): Int = R.layout.fragment_summary

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is GalleryActivity) {
            context.graph().inject(this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter.register(Image::class.java, ImageViewProvider(activity, scanRepo, object : ImageViewProvider.ImageListener {
            override fun onViewImage(image: Image) {
                ImageViewerActivity.navTo(activity, scanRepo,
                        imageCategory.items!!,
                        imageCategory.items!!.indexOf(image))

            }
        }))
        adapter.register(ImageTree::class.java, ImageTreeViewProvider(activity, File.separator, appInfo, scanRepo))
        adapter.register(Category::class.java, CategoryViewProvider())
        adapter.register(Favorite::class.java, FavoriteViewProvider(activity, appInfo, scanRepo))
        adapter.register(CloudFavorite::class.java, CloudFavoriteViewProvider(activity, appInfo, scanRepo))
        cloudFavoriteCategory = Category(null, adapter, items, getString(R.string.label_category_recommend))
        favoriteCategory = Category(cloudFavoriteCategory, adapter, items, getString(R.string.label_category_favorite))
        treeCategory = Category(favoriteCategory, adapter, items, getString(R.string.label_category_tree))
        imageCategory = Category(treeCategory, adapter, items, getString(R.string.label_category_tree_images, 0))


        val gridLayoutManager = GridLayoutManager(getActivity(), SPAN_COUNT)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (items[position] is Category<*>) SPAN_COUNT else 1
            }
        }
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.setHasFixedSize(true)
        recyclerView.addItemDecoration(CategoryItemDecoration(recyclerView))

        scanRepo.registerTreeBuilder()
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(view)
                .subscribe {
                    refresh(it)
                }
    }

    private fun refresh(tree: ImageTree) {
        resetData()
        // tree
        val treeItems = ArrayList<ImageTree>()
        tree.children.forEach { subPath, imageTree ->
            treeItems.add(imageTree.nonEmptyChild())
        }
        treeCategory.apply {
            items = treeItems
            actionClickListener = View.OnClickListener { activity.viewImages(tree) }
            action = getString(R.string.label_category_action_all_images, tree.allImagesCount())
        }

        // images
        imageCategory.label = getString(R.string.label_category_action_all_images, tree.images.size)
        imageCategory.items = tree.images

        // cloudFavorites
        cloudFavoriteRepo.list(appInfo)
                .doOnNext { cloudFavorites ->
                    cloudFavorites.forEach {
                        it.tree = tree.find(PathUtils.formatToDevice(it.path, appInfo))
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .bindUntilEvent(this, FragmentEvent.DESTROY_VIEW)
                .subscribe({ recommendations ->
                    cloudFavoriteCategory.items = recommendations
                    recyclerView.smoothScrollToPosition(0)
                }, { error ->
                    Timber.e(error, "list cloudFavorites")
                })

        // favorites
        favoriteRepo.list(appInfo)
                .observeOn(AndroidSchedulers.mainThread())
                .bindUntilEvent(this, FragmentEvent.DESTROY_VIEW)
                .doOnNext { favorites ->
                    favorites.forEachIndexed { i, favorite ->
                        val path = PathUtils.formatToDevice(favorite.path, appInfo);
                        favorite.tree = tree.find(path)
                    }
                }
                .subscribe({ favorites ->
                    favoriteCategory.items = favorites
                    recyclerView.smoothScrollToPosition(0)
                }, { error ->
                    Timber.e(error, "list favorites")
                }, {

                })
    }

    private fun resetData() {
        favoriteCategory.items = null
        cloudFavoriteCategory.items = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.gallery_summary, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_gallery_setting -> {
                openPathManager()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openPathManager() {
        PathManagerActivity.navTo(activity, appInfo)
    }
}
package com.chalsmooth.roadclassifier2.map

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chalsmooth.roadclassifier2.R
import com.chalsmooth.roadclassifier2.model.LatLng
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mappls.sdk.maps.geometry.LatLng as MapplsLatLng

class BookmarksActivity : AppCompatActivity() {

    private lateinit var rvCollections: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var fabAddCollection: FloatingActionButton
    
    private var collectionsAdapter: CollectionsAdapter? = null
    private lateinit var bookmarkManager: BookmarkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)
        
        bookmarkManager = BookmarkManager.getInstance(this)

        initViews()
        setupRecyclerView()
        setupClickListeners()
        loadCollections()
    }

    override fun onResume() {
        super.onResume()
        loadCollections()
    }

    private fun initViews() {
        rvCollections = findViewById(R.id.rvCollections)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        fabAddCollection = findViewById(R.id.fabAddCollection)
        
        findViewById<View>(R.id.btnNavHome).setOnClickListener {
            finish()
            overridePendingTransition(0, 0)
        }
        
        findViewById<View>(R.id.btnNavContribute).setOnClickListener {
            startActivity(Intent(this, ContributeActivity::class.java))
            overridePendingTransition(0, 0)
        }
        
        // Bookmarks button is already active - highlight it
        val ivNavBookmarks = findViewById<ImageView>(R.id.ivNavBookmarks)
        val tvNavBookmarks = findViewById<TextView>(R.id.tvNavBookmarks)
        ivNavBookmarks.setColorFilter(resources.getColor(R.color.blue))
        tvNavBookmarks.setTextColor(resources.getColor(R.color.blue))
    }

    private fun setupRecyclerView() {
        collectionsAdapter = CollectionsAdapter(
            onCollectionClick = { collection ->
                openCollection(collection)
            },
            onCollectionLongClick = { collection ->
                showCollectionOptions(collection)
            },
            onBookmarkClick = { bookmark ->
                openBookmarkOnMap(bookmark)
            },
            onBookmarkLongClick = { bookmark ->
                showBookmarkOptions(bookmark)
            }
        )
        rvCollections.layoutManager = LinearLayoutManager(this)
        rvCollections.adapter = collectionsAdapter
    }

    private fun setupClickListeners() {
        fabAddCollection.setOnClickListener {
            showCreateCollectionDialog()
        }
    }

    private fun loadCollections() {
        val collections = bookmarkManager.getCollections()
        collectionsAdapter?.updateCollections(collections)
        tvEmptyState.visibility = if (collections.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openCollection(collection: BookmarkManager.Collection) {
        val intent = Intent(this, CollectionDetailActivity::class.java).apply {
            putExtra("collection_id", collection.id)
            putExtra("collection_name", collection.name)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun showCollectionOptions(collection: BookmarkManager.Collection) {
        if (collection.id == "default") {
            Toast.makeText(this, "Cannot modify default collection", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle(collection.name)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameCollectionDialog(collection)
                    1 -> showDeleteCollectionConfirmation(collection)
                }
            }
            .show()
    }

    private fun showRenameCollectionDialog(collection: BookmarkManager.Collection) {
        val input = android.widget.EditText(this).apply {
            setText(collection.name)
            hint = getString(R.string.collection_name)
            setPadding(40, 30, 40, 30)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Rename Collection")
            .setView(input)
            .setPositiveButton(R.string.create_collection) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val updated = BookmarkManager.Collection(collection.id, name, collection.color)
                    bookmarkManager.updateCollection(updated)
                    loadCollections()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteCollectionConfirmation(collection: BookmarkManager.Collection) {
        AlertDialog.Builder(this)
            .setTitle("Delete Collection")
            .setMessage("Delete \"${collection.name}\" and all its bookmarks?")
            .setPositiveButton("Delete") { _, _ ->
                bookmarkManager.deleteCollection(collection.id)
                loadCollections()
                Toast.makeText(this, "Collection deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCreateCollectionDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.collection_name)
            setPadding(40, 30, 40, 30)
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.new_collection)
            .setView(input)
            .setPositiveButton(R.string.create_collection) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    bookmarkManager.createCollection(name)
                    loadCollections()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openBookmarkOnMap(bookmark: BookmarkManager.Bookmark) {
        val intent = Intent(this, MapScreen::class.java).apply {
            putExtra("bookmark_lat", bookmark.latitude)
            putExtra("bookmark_lng", bookmark.longitude)
            putExtra("bookmark_name", bookmark.name)
            putExtra("bookmark_address", bookmark.address)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun showBookmarkOptions(bookmark: BookmarkManager.Bookmark) {
        AlertDialog.Builder(this)
            .setTitle(bookmark.name)
            .setItems(arrayOf("Open on Map", "Move to Collection", "Delete")) { _, which ->
                when (which) {
                    0 -> openBookmarkOnMap(bookmark)
                    1 -> showMoveBookmarkDialog(bookmark)
                    2 -> {
                        bookmarkManager.deleteBookmark(bookmark.id)
                        loadCollections()
                        Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showMoveBookmarkDialog(bookmark: BookmarkManager.Bookmark) {
        val collections = bookmarkManager.getCollections().filter { it.id != bookmark.collectionId }
        if (collections.isEmpty()) {
            Toast.makeText(this, "No other collections available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val items = collections.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Move to Collection")
            .setSingleChoiceItems(items, 0) { dialog, which ->
                val targetCollection = collections[which]
                val updatedBookmark = BookmarkManager.Bookmark(
                    id = bookmark.id,
                    name = bookmark.name,
                    address = bookmark.address,
                    latitude = bookmark.latitude,
                    longitude = bookmark.longitude,
                    collectionId = targetCollection.id,
                    timestamp = bookmark.timestamp
                )
                bookmarkManager.deleteBookmark(bookmark.id)
                val newBm = bookmarkManager.saveBookmark(
                    name = updatedBookmark.name,
                    address = updatedBookmark.address,
                    latitude = updatedBookmark.latitude,
                    longitude = updatedBookmark.longitude,
                    collectionId = targetCollection.id
                )
                if (newBm != null) {
                    loadCollections()
                    Toast.makeText(this, "Moved to ${targetCollection.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Already exists in that collection", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

class CollectionsAdapter(
    private val onCollectionClick: (BookmarkManager.Collection) -> Unit,
    private val onCollectionLongClick: (BookmarkManager.Collection) -> Unit,
    private val onBookmarkClick: (BookmarkManager.Bookmark) -> Unit,
    private val onBookmarkLongClick: (BookmarkManager.Bookmark) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var collections = emptyList<BookmarkManager.Collection>()

    companion object {
        private const val TYPE_COLLECTION = 0
        private const val TYPE_BOOKMARK = 1
    }

    inner class CollectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCollectionName: TextView = view.findViewById(R.id.tvCollectionName)
        val tvBookmarkCount: TextView = view.findViewById(R.id.tvBookmarkCount)
        val ivColorIndicator: View = view.findViewById(R.id.ivColorIndicator)
        val ivArrow: ImageView = view.findViewById(R.id.ivArrow)
    }

    inner class BookmarkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBookmarkName: TextView = view.findViewById(R.id.tvBookmarkName)
        val tvBookmarkAddress: TextView = view.findViewById(R.id.tvBookmarkAddress)
    }

    override fun getItemViewType(position: Int): Int {
        return TYPE_COLLECTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CollectionViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_collection, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is CollectionViewHolder) {
            val collection = collections[position]
            holder.tvCollectionName.text = collection.name
            val bm = BookmarkManager.getInstance(holder.itemView.context)
            val bookmarks = bm.getBookmarksByCollection(collection.id)
            holder.tvBookmarkCount.text = "${bookmarks.size} ${if (bookmarks.size == 1) "place" else "places"}"
            holder.ivColorIndicator.setBackgroundColor(collection.colorInt)
            
            holder.itemView.setOnClickListener { onCollectionClick(collection) }
            holder.itemView.setOnLongClickListener {
                onCollectionLongClick(collection)
                true
            }
        }
    }

    override fun getItemCount(): Int = collections.size

    fun updateCollections(newCollections: List<BookmarkManager.Collection>) {
        collections = newCollections
        notifyDataSetChanged()
    }
}
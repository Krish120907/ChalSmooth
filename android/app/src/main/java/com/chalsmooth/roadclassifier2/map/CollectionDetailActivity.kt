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
import com.mappls.sdk.maps.geometry.LatLng as MapplsLatLng

class CollectionDetailActivity : AppCompatActivity() {

    private lateinit var rvBookmarks: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var tvCollectionName: TextView
    
    private var bookmarksAdapter: BookmarksAdapter? = null
    private lateinit var bookmarkManager: BookmarkManager
    private var collectionId: String = ""
    private var collectionName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collection_detail)
        
        bookmarkManager = BookmarkManager.getInstance(this)

        collectionId = intent.getStringExtra("collection_id") ?: ""
        collectionName = intent.getStringExtra("collection_name") ?: ""

        initViews()
        setupRecyclerView()
        setupClickListeners()
        loadBookmarks()
    }

    override fun onResume() {
        super.onResume()
        loadBookmarks()
    }

    private fun initViews() {
        rvBookmarks = findViewById(R.id.rvBookmarks)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tvCollectionName = findViewById(R.id.tvCollectionName)
        tvCollectionName.text = collectionName
        
        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
            overridePendingTransition(0, 0)
        }
        
        findViewById<View>(R.id.btnNavHome).setOnClickListener {
            val intent = Intent(this, MapScreen::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        
        findViewById<View>(R.id.btnNavContribute).setOnClickListener {
            startActivity(Intent(this, ContributeActivity::class.java))
            overridePendingTransition(0, 0)
        }
        
        findViewById<View>(R.id.btnNavBookmarks).setOnClickListener {
            val intent = Intent(this, BookmarksActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }

    private fun setupRecyclerView() {
        bookmarksAdapter = BookmarksAdapter(
            onBookmarkClick = { bookmark ->
                openBookmarkOnMap(bookmark)
            },
            onBookmarkLongClick = { bookmark ->
                showBookmarkOptions(bookmark)
            }
        )
        rvBookmarks.layoutManager = LinearLayoutManager(this)
        rvBookmarks.adapter = bookmarksAdapter
    }

    private fun setupClickListeners() {
        // No FAB in detail view
    }

    private fun loadBookmarks() {
        val bookmarks = bookmarkManager.getBookmarksByCollection(collectionId)
        bookmarksAdapter?.updateBookmarks(bookmarks)
        tvEmptyState.visibility = if (bookmarks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openBookmarkOnMap(bookmark: BookmarkManager.Bookmark) {
        val intent = Intent(this, MapScreen::class.java).apply {
            putExtra("bookmark_lat", bookmark.latitude)
            putExtra("bookmark_lng", bookmark.longitude)
            putExtra("bookmark_name", bookmark.name)
            putExtra("bookmark_address", bookmark.address)
        }
        startActivity(intent)
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
                        loadBookmarks()
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
                bookmarkManager.deleteBookmark(bookmark.id)
                val newBm = bookmarkManager.saveBookmark(
                    name = bookmark.name,
                    address = bookmark.address,
                    latitude = bookmark.latitude,
                    longitude = bookmark.longitude,
                    collectionId = targetCollection.id
                )
                if (newBm != null) {
                    loadBookmarks()
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

class BookmarksAdapter(
    private val onBookmarkClick: (BookmarkManager.Bookmark) -> Unit,
    private val onBookmarkLongClick: (BookmarkManager.Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarksAdapter.BookmarkViewHolder>() {

    private var bookmarks = emptyList<BookmarkManager.Bookmark>()

    inner class BookmarkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBookmarkName: TextView = view.findViewById(R.id.tvBookmarkName)
        val tvBookmarkAddress: TextView = view.findViewById(R.id.tvBookmarkAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return BookmarkViewHolder(inflater.inflate(R.layout.item_bookmark, parent, false))
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        val bookmark = bookmarks[position]
        holder.tvBookmarkName.text = bookmark.name
        holder.tvBookmarkAddress.text = bookmark.address
        holder.itemView.setOnClickListener { onBookmarkClick(bookmark) }
        holder.itemView.setOnLongClickListener {
            onBookmarkLongClick(bookmark)
            true
        }
    }

    override fun getItemCount(): Int = bookmarks.size

    fun updateBookmarks(newBookmarks: List<BookmarkManager.Bookmark>) {
        bookmarks = newBookmarks
        notifyDataSetChanged()
    }
}
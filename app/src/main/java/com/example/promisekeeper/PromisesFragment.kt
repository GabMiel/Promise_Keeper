package com.example.promisekeeper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar

class PromisesFragment : Fragment() {
    private lateinit var adapter: PromiseAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var rvPromises: RecyclerView? = null
    private var rvCategories: RecyclerView? = null
    private var layoutEmpty: View? = null
    private var ivEmptyState: ImageView? = null
    private var tvEmptyState: TextView? = null
    private var cardSearch: View? = null
    private var etSearch: EditText? = null

    private var layoutSelectionBar: View? = null
    private var tvSelectionCount: TextView? = null
    
    private var allPromises = listOf<Promise>()
    private var selectedCategory: String = "All Categories"
    private var searchQuery: String = ""

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitSelectionMode()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_promise, container, false)
        
        rvPromises = view.findViewById(R.id.rvPromises)
        rvCategories = view.findViewById(R.id.rvCategories)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        ivEmptyState = view.findViewById(R.id.ivEmptyState)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        cardSearch = view.findViewById(R.id.cardSearch)
        etSearch = view.findViewById(R.id.etSearch)

        layoutSelectionBar = view.findViewById(R.id.layoutSelectionBar)
        tvSelectionCount = view.findViewById(R.id.tvSelectionCount)
        
        setupRecyclerViews()
        setupSearch(view)
        setupSelectionActions(view)
        setupSwipeToAction()
        startPromisesListener()
        
        return view
    }

    private fun setupRecyclerViews() {
        adapter = PromiseAdapter(
            onPromiseClick = { promise ->
                val bottomSheet = StatusUpdateBottomSheet.newInstance(promise.id)
                bottomSheet.show(childFragmentManager, "StatusUpdate")
            },
            onPromiseLongClick = { promise ->
                enterSelectionMode(promise)
            },
            onFooterClick = { status ->
                val historyFragment = HistoryFragment.newInstance(status.name)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, historyFragment)
                    .addToBackStack(null)
                    .commit()
            },
            onSelectionChanged = { count ->
                tvSelectionCount?.text = getString(R.string.selected_count, count)
                if (count == 0) exitSelectionMode()
            }
        )
        rvPromises?.layoutManager = LinearLayoutManager(context)
        rvPromises?.adapter = adapter

        val categories = listOf(
            CategoryAdapter.Category("All Categories", R.drawable.ic_other, true),
            CategoryAdapter.Category("Study", R.drawable.ic_study),
            CategoryAdapter.Category("Health", R.drawable.ic_health),
            CategoryAdapter.Category("Personal", R.drawable.ic_personal)
        )
        
        categoryAdapter = CategoryAdapter(categories) { category ->
            selectedCategory = category.name
            filterAndDisplayPromises()
        }
        rvCategories?.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvCategories?.adapter = categoryAdapter
    }

    private fun setupSearch(view: View) {
        view.findViewById<View>(R.id.ivSearch).setOnClickListener {
            cardSearch?.visibility = if (cardSearch?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (cardSearch?.visibility == View.VISIBLE) etSearch?.requestFocus()
        }

        view.findViewById<View>(R.id.ivCloseSearch).setOnClickListener {
            etSearch?.text?.clear()
            cardSearch?.visibility = View.GONE
        }

        etSearch?.addTextChangedListener { text ->
            searchQuery = text.toString()
            filterAndDisplayPromises()
        }
    }

    private fun setupSelectionActions(view: View) {
        view.findViewById<View>(R.id.ivCloseSelection).setOnClickListener {
            exitSelectionMode()
        }

        view.findViewById<View>(R.id.ivDeleteSelected).setOnClickListener {
            deleteSelectedPromises()
        }

        view.findViewById<View>(R.id.ivMarkKeptSelected).setOnClickListener {
            markSelectedAsKept()
        }
    }

    private fun enterSelectionMode(initialPromise: Promise) {
        layoutSelectionBar?.visibility = View.VISIBLE
        adapter.setSelectionMode(true)
        adapter.toggleSelection(initialPromise.id)
        backPressedCallback.isEnabled = true
    }

    private fun exitSelectionMode() {
        layoutSelectionBar?.visibility = View.GONE
        adapter.setSelectionMode(false)
        backPressedCallback.isEnabled = false
    }

    private fun deleteSelectedPromises() {
        val userId = auth.currentUser?.uid ?: return
        val selected = adapter.getSelectedPromises()
        if (selected.isEmpty()) return

        val batch = db.batch()
        selected.forEach { promise ->
            val ref = db.collection("users").document(userId).collection("promises").document(promise.id)
            batch.delete(ref)
        }

        batch.commit().addOnSuccessListener {
            exitSelectionMode()
            view?.let { Snackbar.make(it, "${selected.size} promises deleted", Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun markSelectedAsKept() {
        val userId = auth.currentUser?.uid ?: return
        val selected = adapter.getSelectedPromises()
        if (selected.isEmpty()) return

        val batch = db.batch()
        selected.forEach { promise ->
            val ref = db.collection("users").document(userId).collection("promises").document(promise.id)
            batch.update(ref, "status", PromiseStatus.KEPT.name)
        }

        batch.commit().addOnSuccessListener {
            exitSelectionMode()
            view?.let { Snackbar.make(it, "${selected.size} promises marked as kept", Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun startPromisesListener() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("users").document(userId).collection("promises")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                val rawPromises = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Promise::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                allPromises = rawPromises
                
                checkAutoExpiration(rawPromises)
                updateTabCounts()
                filterAndDisplayPromises()
            }
    }

    private fun checkAutoExpiration(promises: List<Promise>) {
        val userId = auth.currentUser?.uid ?: return
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        promises.forEach { promise ->
            if (promise.status == PromiseStatus.PENDING && promise.timestamp < startOfToday) {
                db.collection("users").document(userId).collection("promises").document(promise.id)
                    .update("status", PromiseStatus.BROKEN.name)
            }
        }
    }

    private fun updateTabCounts() {
        view?.findViewById<TextView>(R.id.tvKeptCount)?.text = allPromises.count { it.status == PromiseStatus.KEPT }.toString()
        view?.findViewById<TextView>(R.id.tvBrokenCount)?.text = allPromises.count { it.status == PromiseStatus.BROKEN }.toString()
        view?.findViewById<TextView>(R.id.tvPendingCount)?.text = allPromises.count { it.status == PromiseStatus.PENDING }.toString()
    }

    private fun filterAndDisplayPromises() {
        var filtered = allPromises
        
        if (selectedCategory != "All Categories") {
            filtered = filtered.filter { it.category == selectedCategory }
        }
        
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { it.description.contains(searchQuery, ignoreCase = true) }
        }
        
        val flattenedList = buildGroupedList(filtered)
        adapter.submitList(flattenedList)
        updateEmptyState(filtered.isEmpty())
    }

    private fun buildGroupedList(promises: List<Promise>): List<Any> {
        val result = mutableListOf<Any>()
        val kept = promises.filter { it.status == PromiseStatus.KEPT }
        val broken = promises.filter { it.status == PromiseStatus.BROKEN }
        val pending = promises.filter { it.status == PromiseStatus.PENDING }

        if (kept.isNotEmpty()) {
            result.add(PromiseAdapter.HeaderItem("KEPT PROMISES", kept.size, R.color.status_kept))
            result.addAll(kept.take(3))
            result.add(PromiseAdapter.FooterItem("View all kept promises", PromiseStatus.KEPT))
        }

        if (broken.isNotEmpty()) {
            result.add(PromiseAdapter.HeaderItem("BROKEN PROMISES", broken.size, R.color.status_broken))
            result.addAll(broken.take(2))
            result.add(PromiseAdapter.FooterItem("View all broken promises", PromiseStatus.BROKEN))
        }

        if (pending.isNotEmpty()) {
            result.add(PromiseAdapter.HeaderItem("PENDING PROMISES", pending.size, R.color.status_pending))
            result.addAll(pending.take(2))
            result.add(PromiseAdapter.FooterItem("View all pending promises", PromiseStatus.PENDING))
        }

        return result
    }

    private fun setupSwipeToAction() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (adapter.isSelectionMode()) {
                    adapter.notifyItemChanged(viewHolder.adapterPosition)
                    return
                }

                val position = viewHolder.adapterPosition
                val item = adapter.currentList[position]
                if (item is Promise) {
                    if (direction == ItemTouchHelper.LEFT) deletePromise(item)
                    else {
                        val editFragment = AddPromiseFragment.newInstance(item.id)
                        parentFragmentManager.beginTransaction().replace(R.id.nav_host_fragment, editFragment).addToBackStack(null).commit()
                    }
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rvPromises)
    }

    private fun deletePromise(promise: Promise) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("promises").document(promise.id).delete()
            .addOnSuccessListener {
                view?.let { Snackbar.make(it, "Promise deleted", Snackbar.LENGTH_LONG).setAction("Undo") {
                    db.collection("users").document(userId).collection("promises").document(promise.id).set(promise)
                }.show() }
            }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            layoutEmpty?.visibility = View.VISIBLE
            rvPromises?.visibility = View.GONE
            
            val isFiltered = selectedCategory != "All Categories" || searchQuery.isNotBlank()
            
            if (allPromises.isEmpty()) {
                tvEmptyState?.text = getString(R.string.no_promises_yet)
                ivEmptyState?.layoutParams?.width = (120 * resources.displayMetrics.density).toInt()
                ivEmptyState?.layoutParams?.height = (120 * resources.displayMetrics.density).toInt()
            } else if (isFiltered) {
                val message = if (searchQuery.isNotBlank()) {
                    getString(R.string.no_search_results, searchQuery)
                } else {
                    getString(R.string.no_category_promises, selectedCategory.lowercase())
                }
                tvEmptyState?.text = message
                ivEmptyState?.layoutParams?.width = (80 * resources.displayMetrics.density).toInt()
                ivEmptyState?.layoutParams?.height = (80 * resources.displayMetrics.density).toInt()
            }
        } else {
            layoutEmpty?.visibility = View.GONE
            rvPromises?.visibility = View.VISIBLE
        }
    }
}

package com.example.promisekeeper

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Calendar

class PromisesFragment : Fragment() {
    private lateinit var adapter: PromiseAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var suggestionAdapter: SuggestionAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var rvPromises: RecyclerView? = null
    private var rvCategories: RecyclerView? = null
    private var rvSuggestions: RecyclerView? = null
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
    private var selectedStatus: PromiseStatus = PromiseStatus.KEPT
    private var snapshotListener: ListenerRegistration? = null

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
        rvSuggestions = view.findViewById(R.id.rvSuggestions)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        ivEmptyState = view.findViewById(R.id.ivEmptyState)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        cardSearch = view.findViewById(R.id.cardSearch)
        etSearch = view.findViewById(R.id.etSearch)

        layoutSelectionBar = view.findViewById(R.id.layoutSelectionBar)
        tvSelectionCount = view.findViewById(R.id.tvSelectionCount)
        
        setupRecyclerViews()
        setupTabs(view)
        setupSearch(view)
        setupSelectionActions(view)
        setupSwipeToAction()
        startPromisesListener()
        
        return view
    }

    private fun setupRecyclerViews() {
        adapter = PromiseAdapter(
            onPromiseClick = { promise, _ ->
                if (isAdded) {
                    val bottomSheet = PromiseEntryBottomSheet.newInstance(promise.id)
                    bottomSheet.show(childFragmentManager, "PromiseEntry")
                }
            },
            onPromiseLongClick = { promise, _ ->
                enterSelectionMode(promise)
            },
            onSelectionChanged = { count ->
                tvSelectionCount?.text = getString(R.string.selected_count, count)
                if (count == 0) exitSelectionMode()
            }
        )
        rvPromises?.layoutManager = LinearLayoutManager(context)
        rvPromises?.adapter = adapter
        rvPromises?.itemAnimator = null // Disable to prevent conflicts with TransitionManager

        val categories = listOf(
            CategoryAdapter.Category("All Categories", R.drawable.ic_diamond, false),
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

        suggestionAdapter = SuggestionAdapter { suggestion ->
            etSearch?.setText(suggestion)
            etSearch?.setSelection(suggestion.length)
            hideSuggestions()
            searchQuery = suggestion
            filterAndDisplayPromises()
            hideKeyboard()
        }
        rvSuggestions?.layoutManager = LinearLayoutManager(context)
        rvSuggestions?.adapter = suggestionAdapter
    }

    private fun setupTabs(view: View) {
        view.findViewById<View>(R.id.tabKept).setOnClickListener {
            selectedStatus = PromiseStatus.KEPT
            updateTabsUI(view)
            filterAndDisplayPromises()
        }
        view.findViewById<View>(R.id.tabBroken).setOnClickListener {
            selectedStatus = PromiseStatus.BROKEN
            updateTabsUI(view)
            filterAndDisplayPromises()
        }
        view.findViewById<View>(R.id.tabPending).setOnClickListener {
            selectedStatus = PromiseStatus.PENDING
            updateTabsUI(view)
            filterAndDisplayPromises()
        }
        updateTabsUI(view, false)
    }

    private fun updateTabsUI(view: View, animate: Boolean = true) {
        if (!isAdded) return
        val tabIndicator = view.findViewById<View>(R.id.tabIndicator)
        val parentLayout = tabIndicator.parent as? ViewGroup

        if (animate && parentLayout != null) {
            TransitionManager.beginDelayedTransition(parentLayout, AutoTransition().apply {
                duration = 250
                interpolator = DecelerateInterpolator()
            })
        }

        val params = tabIndicator.layoutParams as ConstraintLayout.LayoutParams
        val (bias, color) = when (selectedStatus) {
            PromiseStatus.KEPT -> 0f to R.color.status_kept
            PromiseStatus.BROKEN -> 0.5f to R.color.status_broken
            PromiseStatus.PENDING -> 1f to R.color.status_pending
        }
        
        params.horizontalBias = bias
        tabIndicator.layoutParams = params
        tabIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), color))
        
        val tvKept = (view.findViewById<ViewGroup>(R.id.tabKept)).getChildAt(0) as TextView
        tvKept.setTextColor(ContextCompat.getColor(requireContext(), if (selectedStatus == PromiseStatus.KEPT) R.color.status_kept else R.color.text_gray))
        
        val tvBroken = (view.findViewById<ViewGroup>(R.id.tabBroken)).getChildAt(0) as TextView
        tvBroken.setTextColor(ContextCompat.getColor(requireContext(), if (selectedStatus == PromiseStatus.BROKEN) R.color.status_broken else R.color.text_gray))
        
        val tvPending = (view.findViewById<ViewGroup>(R.id.tabPending)).getChildAt(0) as TextView
        tvPending.setTextColor(ContextCompat.getColor(requireContext(), if (selectedStatus == PromiseStatus.PENDING) R.color.status_pending else R.color.text_gray))
    }

    private fun setupSearch(view: View) {
        view.findViewById<View>(R.id.ivSearch).setOnClickListener {
            val searchBar = cardSearch ?: return@setOnClickListener
            val parent = searchBar.parent as? ViewGroup
            
            if (parent != null) {
                val transition = TransitionSet().apply {
                    addTransition(Slide(Gravity.TOP).addTarget(searchBar))
                    addTransition(Fade().addTarget(searchBar))
                    addTransition(ChangeBounds())
                    duration = 300
                    interpolator = DecelerateInterpolator()
                }
                TransitionManager.beginDelayedTransition(parent, transition)
            }
            
            if (searchBar.visibility == View.VISIBLE) {
                searchBar.visibility = View.GONE
                hideSuggestions()
                hideKeyboard()
            } else {
                searchBar.visibility = View.VISIBLE
                etSearch?.requestFocus()
                showKeyboard()
            }
        }

        view.findViewById<View>(R.id.ivCloseSearch).setOnClickListener {
            val searchBar = cardSearch ?: return@setOnClickListener
            val parent = searchBar.parent as? ViewGroup
            
            if (parent != null) {
                TransitionManager.beginDelayedTransition(parent, AutoTransition().setDuration(250))
            }
            etSearch?.text?.clear()
            searchBar.visibility = View.GONE
            hideSuggestions()
            hideKeyboard()
        }

        etSearch?.addTextChangedListener { text ->
            searchQuery = text.toString()
            updateSuggestions(searchQuery)
            filterAndDisplayPromises()
        }

        etSearch?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideSuggestions()
                hideKeyboard()
                true
            } else false
        }
    }

    private fun updateSuggestions(query: String) {
        if (!isAdded || query.isBlank()) {
            hideSuggestions()
            return
        }

        val suggestions = allPromises
            .filter { it.description.contains(query, ignoreCase = true) }
            .map { it.description }
            .distinct()
            .take(5)

        if (suggestions.isEmpty()) {
            hideSuggestions()
        } else {
            suggestionAdapter.submitList(suggestions)
            rvSuggestions?.visibility = View.VISIBLE
        }
    }

    private fun hideSuggestions() {
        rvSuggestions?.visibility = View.GONE
    }

    private fun showKeyboard() {
        etSearch?.post {
            if (isAdded) {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun hideKeyboard() {
        if (isAdded) {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etSearch?.windowToken, 0)
        }
    }

    private fun setupSelectionActions(view: View) {
        view.findViewById<View>(R.id.ivCloseSelection).setOnClickListener { exitSelectionMode() }
        view.findViewById<View>(R.id.ivDeleteSelected).setOnClickListener { deleteSelectedPromises() }
        view.findViewById<View>(R.id.ivMarkKeptSelected).setOnClickListener { markSelectedAsKept() }
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
            if (isAdded) {
                exitSelectionMode()
                view?.let { Snackbar.make(it, "${selected.size} promises deleted", Snackbar.LENGTH_LONG).show() }
            }
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
            if (isAdded) {
                exitSelectionMode()
                view?.let { Snackbar.make(it, "${selected.size} promises marked as kept", Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    private fun startPromisesListener() {
        val userId = auth.currentUser?.uid ?: return
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        val endOfToday = startOfToday + 86400000

        snapshotListener?.remove()
        snapshotListener = db.collection("users").document(userId).collection("promises")
            .whereGreaterThanOrEqualTo("timestamp", startOfToday)
            .whereLessThan("timestamp", endOfToday)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null || !isAdded) return@addSnapshotListener
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
        if (!isAdded) return
        view?.findViewById<TextView>(R.id.tvKeptCount)?.text = allPromises.count { it.status == PromiseStatus.KEPT }.toString()
        view?.findViewById<TextView>(R.id.tvBrokenCount)?.text = allPromises.count { it.status == PromiseStatus.BROKEN }.toString()
        view?.findViewById<TextView>(R.id.tvPendingCount)?.text = allPromises.count { it.status == PromiseStatus.PENDING }.toString()
    }

    private fun filterAndDisplayPromises() {
        if (!isAdded) return
        var filtered = allPromises.filter { it.status == selectedStatus }
        if (selectedCategory != "All Categories") {
            filtered = filtered.filter { it.category == selectedCategory }
        }
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { it.description.contains(searchQuery, ignoreCase = true) }
        }
        
        view?.let {
            TransitionManager.beginDelayedTransition(it as ViewGroup, AutoTransition().apply { 
                duration = 200
                excludeTarget(rvPromises!!, true)
            })
        }
        
        adapter.submitList(filtered.map { it as Any })
        updateEmptyState(filtered.isEmpty())
    }

    private fun setupSwipeToAction() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (!isAdded || adapter.isSelectionMode()) {
                    adapter.notifyItemChanged(viewHolder.adapterPosition)
                    return
                }
                val position = viewHolder.adapterPosition
                val item = adapter.currentList[position]
                if (item is Promise) {
                    if (direction == ItemTouchHelper.LEFT) {
                        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        showDeleteConfirmation(item)
                    } else {
                        // Swipe RIGHT to mark as KEPT immediately
                        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        markPromiseAsKept(item)
                    }
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(rvPromises)
    }

    private fun markPromiseAsKept(promise: Promise) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("promises").document(promise.id)
            .update("status", PromiseStatus.KEPT.name)
            .addOnSuccessListener {
                if (isAdded) {
                    view?.let { Snackbar.make(it, "Promise marked as kept!", Snackbar.LENGTH_SHORT).show() }
                }
            }
            .addOnFailureListener {
                if (isAdded) {
                    val position = adapter.currentList.indexOf(promise)
                    if (position != -1) adapter.notifyItemChanged(position)
                    Toast.makeText(context, "Failed to update promise", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showDeleteConfirmation(promise: Promise) {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Promise")
            .setMessage(R.string.delete_confirmation_msg)
            .setPositiveButton("Delete") { _, _ -> deletePromise(promise) }
            .setNegativeButton("Cancel") { _, _ ->
                val position = adapter.currentList.indexOf(promise)
                if (position != -1) adapter.notifyItemChanged(position)
            }
            .setOnCancelListener {
                val position = adapter.currentList.indexOf(promise)
                if (position != -1) adapter.notifyItemChanged(position)
            }
            .show()
    }

    private fun deletePromise(promise: Promise) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("promises").document(promise.id).delete()
            .addOnSuccessListener {
                if (isAdded) {
                    view?.let { 
                        Snackbar.make(it, "Promise deleted", Snackbar.LENGTH_LONG)
                            .setAction("Undo") {
                                if (isAdded) {
                                    db.collection("users").document(userId).collection("promises").document(promise.id).set(promise)
                                }
                            }.show() 
                    }
                }
            }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (!isAdded) return
        if (isEmpty) {
            layoutEmpty?.visibility = View.VISIBLE
            rvPromises?.visibility = View.GONE
            
            val isSearching = searchQuery.isNotBlank()
            
            if (isSearching) {
                ivEmptyState?.setImageResource(android.R.drawable.ic_menu_search)
                ivEmptyState?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_gray))
                ivEmptyState?.alpha = 0.2f
                tvEmptyState?.text = getString(R.string.no_search_results, searchQuery)
            } else {
                val (iconRes, textRes, colorRes) = when (selectedStatus) {
                    PromiseStatus.KEPT -> Triple(R.drawable.ic_promise_kept, R.string.no_kept_promises, R.color.status_kept)
                    PromiseStatus.BROKEN -> Triple(R.drawable.ic_promise_broken, R.string.no_broken_promises, R.color.status_broken)
                    PromiseStatus.PENDING -> Triple(R.drawable.ic_promise_pending, R.string.no_pending_promises, R.color.status_pending)
                }
                
                ivEmptyState?.setImageResource(iconRes)
                ivEmptyState?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))
                ivEmptyState?.alpha = 0.3f
                
                if (allPromises.isEmpty()) {
                    tvEmptyState?.text = getString(R.string.no_promises_yet)
                } else if (selectedCategory != "All Categories") {
                    tvEmptyState?.text = getString(R.string.no_category_promises, selectedCategory.lowercase())
                } else {
                    tvEmptyState?.text = getString(textRes)
                }
            }
        } else {
            layoutEmpty?.visibility = View.GONE
            rvPromises?.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        snapshotListener?.remove()
        snapshotListener = null
        rvPromises = null
        rvCategories = null
        rvSuggestions = null
        layoutEmpty = null
        ivEmptyState = null
        tvEmptyState = null
        cardSearch = null
        etSearch = null
        layoutSelectionBar = null
        tvSelectionCount = null
    }

    private inner class SuggestionAdapter(private val onSuggestionClick: (String) -> Unit) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {
        private var items = listOf<String>()

        fun submitList(newItems: List<String>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_suggestion, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvSuggestion.text = item
            holder.itemView.setOnClickListener { onSuggestionClick(item) }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvSuggestion: TextView = view.findViewById(R.id.tvSuggestion)
        }
    }
}

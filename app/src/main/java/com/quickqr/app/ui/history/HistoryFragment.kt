package com.quickqr.app.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.quickqr.app.data.AppDatabase
import com.quickqr.app.data.ScanEntity
import com.quickqr.app.databinding.FragmentHistoryBinding
import com.quickqr.app.ui.result.ResultActivity
import com.quickqr.app.util.ContentParser
import com.quickqr.app.util.ContentTypeLabel
import com.quickqr.app.util.ParsedContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HistoryAdapter

    private val searchQuery = MutableStateFlow("")
    private val favoritesOnly = MutableStateFlow(false)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HistoryAdapter(
            onItemClick = { scan -> openResult(scan) },
            onToggleFavorite = { scan -> toggleFavorite(scan) },
            onCopy = { scan -> copyToClipboard(scan) },
            onShare = { scan -> shareScan(scan) },
            onOpenUrl = { scan -> openUrl(scan) },
            onDelete = { scan -> confirmDeleteOne(scan) }
        )
        binding.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHistory.adapter = adapter

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    deleteScan(adapter.currentList[position])
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerHistory)

        binding.btnClearAll.setOnClickListener { confirmClearAll() }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery.value = newText.orEmpty()
                return true
            }
        })

        binding.chipFavoritesOnly.setOnCheckedChangeListener { _, isChecked ->
            favoritesOnly.value = isChecked
        }

        val dao = AppDatabase.getInstance(requireContext()).scanDao()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(dao.getAll(), searchQuery, favoritesOnly) { list, query, favOnly ->
                    list.filter { scan ->
                        (!favOnly || scan.isFavorite) && matchesQuery(scan, query)
                    }
                }.collect { filtered ->
                    adapter.submitList(filtered)
                    binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                    binding.textEmptyState.setText(
                        if (favoritesOnly.value) com.quickqr.app.R.string.empty_favorites
                        else com.quickqr.app.R.string.empty_history
                    )
                    binding.btnClearAll.isEnabled = filtered.isNotEmpty()
                }
            }
        }
    }

    private fun matchesQuery(scan: ScanEntity, query: String): Boolean {
        if (query.isBlank()) return true
        val parsed = ContentParser.parse(scan.content)
        val preview = ContentTypeLabel.previewOf(parsed)
        return scan.content.contains(query, ignoreCase = true) || preview.contains(query, ignoreCase = true)
    }

    private fun openResult(scan: ScanEntity) {
        val intent = Intent(requireContext(), ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_CONTENT, scan.content)
            putExtra(ResultActivity.EXTRA_FORMAT, scan.format)
            putExtra(ResultActivity.EXTRA_SKIP_SAVE, true)
        }
        startActivity(intent)
    }

    private fun toggleFavorite(scan: ScanEntity) {
        val dao = AppDatabase.getInstance(requireContext()).scanDao()
        viewLifecycleOwner.lifecycleScope.launch { dao.setFavorite(scan.id, !scan.isFavorite) }
    }

    private fun copyToClipboard(scan: ScanEntity) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QuickQR", scan.content))
        Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareScan(scan: ScanEntity) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, scan.content)
        }
        startActivity(Intent.createChooser(intent, null))
    }

    private fun openUrl(scan: ScanEntity) {
        val parsed = ContentParser.parse(scan.content)
        if (parsed is ParsedContent.Url) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(parsed.url)))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Couldn't open link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteScan(scan: ScanEntity) {
        val dao = AppDatabase.getInstance(requireContext()).scanDao()
        viewLifecycleOwner.lifecycleScope.launch { dao.delete(scan) }
    }

    private fun confirmDeleteOne(scan: ScanEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(com.quickqr.app.R.string.delete_scan_title)
            .setMessage(com.quickqr.app.R.string.delete_scan_message)
            .setPositiveButton(com.quickqr.app.R.string.delete) { _, _ -> deleteScan(scan) }
            .setNegativeButton(com.quickqr.app.R.string.cancel, null)
            .show()
    }

    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear history")
            .setMessage("This will remove all non-favorited scanned codes from your history.")
            .setPositiveButton("Clear") { _, _ ->
                val dao = AppDatabase.getInstance(requireContext()).scanDao()
                viewLifecycleOwner.lifecycleScope.launch { dao.deleteAllExceptFavorites() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

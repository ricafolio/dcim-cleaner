package com.dcimcleaner.ui.settings

import android.os.Bundle
import android.os.Environment
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dcimcleaner.MainActivity
import com.dcimcleaner.R
import com.dcimcleaner.data.repository.PhotoRepository
import com.dcimcleaner.databinding.FragmentSettingsBinding
import com.dcimcleaner.worker.IndexWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var repo: PhotoRepository
    private lateinit var folderAdapter: FolderToggleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = PhotoRepository(requireContext())

        setupFolderList()
        setupReindex()
        observeIndexProgress()
    }

    private fun setupFolderList() {
        val dcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val prefs = requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val ignoredSet = prefs.getStringSet("ignored_folders", emptySet()) ?: emptySet()

        lifecycleScope.launch {
            val folders = withContext(Dispatchers.IO) {
                dcimPath.listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { it.name }
                    ?.sorted()
                    ?: emptyList()
            }

            val items = folders.map { name ->
                FolderItem(name, ignoredSet.contains(name))
            }

            folderAdapter = FolderToggleAdapter(items) { folderName, ignored ->
                val current = prefs.getStringSet("ignored_folders", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (ignored) current.add(folderName) else current.remove(folderName)
                prefs.edit().putStringSet("ignored_folders", current).apply()
            }

            binding.recyclerFolders.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerFolders.adapter = folderAdapter

            if (folders.isEmpty()) {
                binding.tvNoFolders.visibility = View.VISIBLE
                binding.recyclerFolders.visibility = View.GONE
            }
        }
    }

    private fun setupReindex() {
        binding.btnReindex.setOnClickListener {
            lifecycleScope.launch {
                repo.clearIndex()
            }
            // Enqueue directly without going through MainActivity's progress bar
            IndexWorker.enqueue(requireContext())
            android.widget.Toast.makeText(
                requireContext(),
                "Re-indexing with updated folder settings…",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun observeIndexProgress() {
        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(IndexWorker.WORK_NAME)
            .observe(viewLifecycleOwner) { infos ->
                val info = infos?.firstOrNull() ?: return@observe
                val progress = info.progress.getInt(IndexWorker.PROGRESS_KEY, 0)
                binding.progressReindex.progress = progress

                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        binding.progressReindex.visibility = View.VISIBLE
                        binding.btnReindex.isEnabled = false
                        binding.tvReindexStatus.text = "Indexing... $progress%"
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        binding.progressReindex.visibility = View.GONE
                        binding.btnReindex.isEnabled = true
                        binding.tvReindexStatus.text = "Index up to date ✓"
                    }
                    WorkInfo.State.FAILED -> {
                        binding.progressReindex.visibility = View.GONE
                        binding.btnReindex.isEnabled = true
                        binding.tvReindexStatus.text = "Index failed. Try again."
                    }
                    else -> {
                        binding.progressReindex.visibility = View.GONE
                        binding.btnReindex.isEnabled = true
                        binding.tvReindexStatus.text = ""
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

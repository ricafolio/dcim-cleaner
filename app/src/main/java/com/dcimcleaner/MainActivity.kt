package com.dcimcleaner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dcimcleaner.data.repository.PhotoRepository
import com.dcimcleaner.databinding.ActivityMainBinding
import com.dcimcleaner.worker.IndexWorker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfig: AppBarConfiguration
    private lateinit var repo: PhotoRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startIndexIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar is inside the included app_bar_main layout
        setSupportActionBar(binding.appBarMain.toolbar)

        repo = PhotoRepository(this)

        val navController = findNavController(R.id.nav_host_fragment)
        appBarConfig = AppBarConfiguration(setOf(R.id.nav_images, R.id.nav_analyzer), binding.drawerLayout)
        setupActionBarWithNavController(navController, appBarConfig)
        binding.navView.setupWithNavController(navController)

        checkPermissionsAndIndex()
    }

    private fun checkPermissionsAndIndex() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startIndexIfNeeded()
        else permissionLauncher.launch(perms)
    }

    private fun startIndexIfNeeded() {
        lifecycleScope.launch {
            if (!repo.isIndexBuilt()) launchIndexWorker()
        }
    }

    fun launchIndexWorker() {
        val progressBar = binding.appBarMain.progressBar
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        IndexWorker.enqueue(this)

        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(IndexWorker.WORK_NAME)
            .observe(this) { infos ->
                val info = infos?.firstOrNull() ?: return@observe
                val progress = info.progress.getInt(IndexWorker.PROGRESS_KEY, 0)
                progressBar.progress = progress

                if (info.state == WorkInfo.State.SUCCEEDED || info.state == WorkInfo.State.FAILED) {
                    progressBar.visibility = View.GONE
                    supportFragmentManager.fragments.forEach { frag ->
                        frag.childFragmentManager.fragments.forEach { child ->
                            if (child is IndexCompleteListener) child.onIndexComplete()
                        }
                    }
                }
            }
    }

    override fun onSupportNavigateUp() =
        findNavController(R.id.nav_host_fragment).navigateUp(appBarConfig) || super.onSupportNavigateUp()
}

interface IndexCompleteListener {
    fun onIndexComplete()
}

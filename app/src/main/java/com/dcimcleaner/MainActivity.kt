package com.dcimcleaner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dcimcleaner.data.repository.PhotoRepository
import com.dcimcleaner.data.repository.SessionPrefs
import com.dcimcleaner.data.repository.TrashRepository
import com.dcimcleaner.databinding.ActivityMainBinding
import com.dcimcleaner.worker.IndexWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfig: AppBarConfiguration
    private lateinit var repo: PhotoRepository
    private var trashBadgeView: TextView? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            checkManageMediaPermission()
            startIndexIfNeeded()
        }
    }

    private val manageMediaLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)
        repo = PhotoRepository(this)

        val navController = findNavController(R.id.nav_host_fragment)
        appBarConfig = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_images, R.id.nav_analyzer,
                  R.id.nav_trash, R.id.nav_settings, R.id.nav_help),
            binding.drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfig)

        supportActionBar?.setDisplayShowTitleEnabled(false)
        val toolbarTitle = binding.appBarMain.toolbar.findViewById<TextView>(R.id.toolbar_title)
        findNavController(R.id.nav_host_fragment).addOnDestinationChangedListener { _, destination, _ ->
            toolbarTitle.text = destination.label
        }

        binding.navView.setupWithNavController(navController)

        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawers()
            when (item.itemId) {
                R.id.nav_home -> {
                    navController.navigate(R.id.nav_home, null,
                        androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_home, true)
                            .setLaunchSingleTop(true)
                            .build())
                    true
                }
                R.id.nav_images -> {
                    val session = SessionPrefs(this)
                    val lastDate = session.lastVisitedDate
                    val lastType = session.lastVisitedType
                    val args = if (lastDate.isNotEmpty()) {
                        if (lastType == "month") bundleOf("load_month" to lastDate)
                        else bundleOf("load_day" to lastDate)
                    } else null
                    navController.navigate(R.id.nav_images, args,
                        androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_home, false)
                            .setLaunchSingleTop(false)
                            .build())
                    true
                }
                else -> androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, navController)
            }
        }

        when (intent?.action) {
            "WIDGET_RANDOM_DAY" -> navController.navigate(R.id.nav_images, bundleOf("pick_random" to "day"))
            "WIDGET_CLEANUP_OPEN" -> {
                val month = intent.getStringExtra("load_month")
                val day = intent.getStringExtra("load_day")
                val args = when {
                    month != null -> bundleOf("load_month" to month)
                    day != null -> bundleOf("load_day" to day)
                    else -> null
                }
                if (args != null) navController.navigate(R.id.nav_images, args)
            }
        }

        checkPermissionsAndIndex()
        refreshTrashBadge()
    }

    override fun onResume() {
        super.onResume()
        refreshTrashBadge()
    }

    private fun refreshTrashBadge() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { TrashRepository(this@MainActivity).getTrashedCount() }

            // Toolbar badge
            trashBadgeView?.let { badge ->
                if (count > 0) {
                    badge.visibility = View.VISIBLE
                    badge.text = if (count > 99) "99+" else count.toString()
                } else {
                    badge.visibility = View.GONE
                }
            }

            // Sidebar nav badge via actionLayout
            val trashItem = binding.navView.menu.findItem(R.id.nav_trash)
            val actionView = trashItem?.actionView
            val navBadge = actionView?.findViewById<TextView>(R.id.tv_nav_trash_badge)
            if (count > 0) {
                navBadge?.visibility = View.VISIBLE
                navBadge?.text = if (count > 99) "99+" else count.toString()
            } else {
                navBadge?.visibility = View.GONE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)

        // Wire up the custom trash action view badge
        val trashItem = menu.findItem(R.id.action_trash)
        val actionView = trashItem?.actionView
        trashBadgeView = actionView?.findViewById(R.id.tv_trash_badge)
        actionView?.setOnClickListener {
            findNavController(R.id.nav_host_fragment).navigate(R.id.nav_trash)
        }

        findNavController(R.id.nav_host_fragment).addOnDestinationChangedListener { _, destination, _ ->
            val isHome = destination.id == R.id.nav_home
            menu.findItem(R.id.action_home)?.isVisible = !isHome
            menu.findItem(R.id.action_trash)?.isVisible = !isHome
        }

        refreshTrashBadge()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_home -> {
                findNavController(R.id.nav_host_fragment).navigate(
                    R.id.nav_home, null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.nav_home, true)
                        .setLaunchSingleTop(true)
                        .build()
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkPermissionsAndIndex() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) { checkManageMediaPermission(); startIndexIfNeeded() }
        else permissionLauncher.launch(perms)
    }

    private fun checkManageMediaPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !MediaStore.canManageMedia(this)) {
            manageMediaLauncher.launch(
                Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
            )
        }
    }

    private fun startIndexIfNeeded() {
        lifecycleScope.launch { if (!repo.isIndexBuilt()) launchIndexWorker() }
    }

    fun launchIndexWorker() {
        val progressBar = binding.appBarMain.progressBar
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        val toolbarSpinner = binding.appBarMain.toolbar.findViewById<View>(R.id.toolbar_spinner)
        toolbarSpinner?.visibility = View.VISIBLE

        IndexWorker.enqueue(this)
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(IndexWorker.WORK_NAME)
            .observe(this) { infos ->
                val info = infos?.firstOrNull() ?: return@observe
                progressBar.progress = info.progress.getInt(IndexWorker.PROGRESS_KEY, 0)
                if (info.state == WorkInfo.State.SUCCEEDED || info.state == WorkInfo.State.FAILED) {
                    progressBar.visibility = View.GONE
                    toolbarSpinner?.visibility = View.GONE
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

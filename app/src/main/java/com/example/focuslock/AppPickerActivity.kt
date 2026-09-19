package com.example.focuslock

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class AppPickerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var tvTitle: TextView
    private lateinit var tvSelectCount: TextView
    private lateinit var btnSelectAll: Button
    private lateinit var btnDeselectAll: Button
    private lateinit var btnConfirm: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var adapter: AppAdapter
    private val preselectedPackages = mutableSetOf<String>()
    private var isWhitelistMode = true
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        isWhitelistMode = intent.getBooleanExtra(EXTRA_IS_WHITELIST, true)
        val passedList = intent.getStringArrayListExtra(EXTRA_SELECTED_APPS)
        if (passedList != null) {
            preselectedPackages.addAll(passedList)
        }

        tvTitle = findViewById(R.id.tvPickerTitle)
        recyclerView = findViewById(R.id.recyclerViewApps)
        searchView = findViewById(R.id.searchView)
        tvSelectCount = findViewById(R.id.tvSelectCount)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDeselectAll = findViewById(R.id.btnDeselectAll)
        btnConfirm = findViewById(R.id.btnConfirmSelection)
        progressBar = findViewById(R.id.progressBar)

        tvTitle.text = if (isWhitelistMode) "Choose Allowed Apps" else "Choose Apps to Block"
        btnConfirm.text = if (isWhitelistMode) "Save Allowed Apps" else "Save Blocked Apps"

        adapter = AppAdapter(emptyList()) { count ->
            tvSelectCount.text = "$count selected"
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                adapter.filter(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText.orEmpty())
                return true
            }
        })

        btnSelectAll.setOnClickListener {
            adapter.selectAll()
        }

        btnDeselectAll.setOnClickListener {
            adapter.deselectAll()
        }

        btnConfirm.setOnClickListener {
            val selected = adapter.getSelectedPackages()
            val resultIntent = Intent().apply {
                putStringArrayListExtra(EXTRA_SELECTED_APPS, selected)
                putExtra(EXTRA_IS_WHITELIST, isWhitelistMode)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

        loadApps()
    }

    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        backgroundExecutor.execute {
            val pm = packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val appList = mutableListOf<AppInfo>()

            for (app in installedApps) {
                val pkgName = app.packageName

                // Skip our own app
                if (pkgName == packageName) continue

                // Only include apps that have a launcher activity
                val launchIntent = pm.getLaunchIntentForPackage(pkgName) ?: continue

                // Exclude system dialer / telecom from picker (always allowed by default)
                if (pkgName.contains("dialer") || pkgName.contains("telecom") || pkgName.contains("emergency")) {
                    continue
                }

                val label = try {
                    pm.getApplicationLabel(app).toString()
                } catch (e: Exception) {
                    pkgName
                }

                val icon = try {
                    pm.getApplicationIcon(app)
                } catch (e: Exception) {
                    null
                }

                val isPreselected = preselectedPackages.contains(pkgName)
                appList.add(AppInfo(label, pkgName, icon, isPreselected))
            }

            appList.sortBy { it.appName.lowercase() }

            runOnUiThread {
                progressBar.visibility = View.GONE
                adapter.updateData(appList)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }

    companion object {
        const val EXTRA_SELECTED_APPS = "extra_selected_apps"
        const val EXTRA_IS_WHITELIST = "extra_is_whitelist"
    }
}

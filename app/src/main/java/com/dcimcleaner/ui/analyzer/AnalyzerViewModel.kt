package com.dcimcleaner.ui.analyzer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.map
import com.dcimcleaner.data.repository.PhotoRepository

class AnalyzerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PhotoRepository(app)

    val monthStats = repo.monthStats
    val dayStats = repo.dayStats

    val yearStats = monthStats.map { list ->
        list.groupBy { it.date.substring(0, 4) }
            .map { (year, months) ->
                Pair(year, months.sumOf { it.fileCount }.toFloat())
            }
            .sortedByDescending { it.first }
    }
}

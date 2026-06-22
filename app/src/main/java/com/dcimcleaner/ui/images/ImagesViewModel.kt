package com.dcimcleaner.ui.images

import android.app.Application
import android.content.IntentSender
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.dcimcleaner.data.model.PhotoEntry
import com.dcimcleaner.data.repository.PhotoRepository
import com.dcimcleaner.data.repository.SessionPrefs
import com.dcimcleaner.data.repository.TrashResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ImagesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PhotoRepository(app)
    private var trashToast: Toast? = null
    val session = SessionPrefs(app)

    val photos = MutableLiveData<List<PhotoEntry>>()
    val currentDate = MutableLiveData<String>()
    val currentDateType = MutableLiveData<String>()
    val isCompactGrid = MutableLiveData(false)
    val trashModeEnabled = MutableLiveData(false)
    val hasPrevious = MutableLiveData(false)
    val spanCount = MutableLiveData<Int>(3)

    private fun formatDisplayDate(key: String, type: String): String {
        return try {
            if (type == "month") {
                val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
                SimpleDateFormat("MMMM yyyy", Locale.US).format(sdf.parse(key)!!)
            } else {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                SimpleDateFormat("MMMM d, yyyy", Locale.US).format(sdf.parse(key)!!)
            }
        } catch (e: Exception) { key }
    }

    private fun setDate(type: String, key: String) {
        val prev = currentDate.value
        val prevType = currentDateType.value
        if (!prev.isNullOrEmpty() && !prevType.isNullOrEmpty()) {
            session.pushHistory(prevType, prev)
        }
        currentDate.value = formatDisplayDate(key, type)
        currentDateType.value = type
        session.lastVisitedDate = key
        session.lastVisitedType = type
        hasPrevious.value = session.hasHistory()
    }

    val noEligibleDate = MutableLiveData<Boolean>()
    val cycleRestarted = MutableLiveData<String>() // "month" or "day" — fired when no-repeat pool was exhausted and reset

    fun pickRandomMonth() = viewModelScope.launch {
        val year = session.filterYear
        val noRepeat = session.filterNoRepeatMonths
        val minPhotos = session.filterMinPhotos
        var visited = session.getVisitedMonths()

        var (key, list) = if (year.isNotEmpty() || noRepeat || minPhotos > 0) {
            repo.getRandomMonthPhotosFiltered(year, noRepeat, visited, minPhotos)
        } else {
            repo.getRandomMonthPhotos()
        }

        // No-repeat exhausted the pool — reset visited months and retry once
        if (key.isEmpty() && noRepeat && visited.isNotEmpty()) {
            session.clearVisited("month")
            val retry = repo.getRandomMonthPhotosFiltered(year, noRepeat, emptySet(), minPhotos)
            key = retry.first
            list = retry.second
            if (key.isNotEmpty()) {
                cycleRestarted.value = "month"
            }
        }

        if (key.isEmpty()) {
            noEligibleDate.value = true
            return@launch
        }
        session.markVisited("month", key)
        setDate("month", key)
        photos.value = list
    }

    fun pickRandomDay() = viewModelScope.launch {
        val year = session.filterYear
        val noRepeat = session.filterNoRepeatDays
        val minPhotos = session.filterMinPhotos
        var visited = session.getVisitedDays()

        var (key, list) = if (year.isNotEmpty() || noRepeat || minPhotos > 0) {
            repo.getRandomDayPhotosFiltered(year, noRepeat, visited, minPhotos)
        } else {
            repo.getRandomDayPhotos()
        }

        // No-repeat exhausted the pool — reset visited days and retry once
        if (key.isEmpty() && noRepeat && visited.isNotEmpty()) {
            session.clearVisited("day")
            val retry = repo.getRandomDayPhotosFiltered(year, noRepeat, emptySet(), minPhotos)
            key = retry.first
            list = retry.second
            if (key.isNotEmpty()) {
                cycleRestarted.value = "day"
            }
        }

        if (key.isEmpty()) {
            noEligibleDate.value = true
            return@launch
        }
        session.markVisited("day", key)
        setDate("day", key)
        photos.value = list
    }

    fun loadByMonth(month: String) = viewModelScope.launch {
        setDate("month", month)
        photos.value = repo.getPhotosByMonth(month)
    }

    fun loadByDay(day: String) = viewModelScope.launch {
        setDate("day", day)
        photos.value = repo.getPhotosByDay(day)
    }

    fun goToPrevious() = viewModelScope.launch {
        val (type, date) = session.popHistory() ?: return@launch
        currentDate.value = formatDisplayDate(date, type)
        currentDateType.value = type
        photos.value = if (type == "month") repo.getPhotosByMonth(date) else repo.getPhotosByDay(date)
        hasPrevious.value = session.hasHistory()
    }

    fun toggleGrid() {
        spanCount.value = when (spanCount.value) {
            2 -> 3
            3 -> 5
            else -> 2 // Cycles back to 2
        }
        // If you have a preference repository, save it here:
        // prefs.setSpanCount(spanCount.value!!)
    }

    fun trashPhoto(entry: PhotoEntry, onNeedsIntent: (IntentSender) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = repo.moveToTrash(entry)) {
                is TrashResult.Success -> {
                    session.addTrashed(entry.sizeMb)
                    removeFromList(entry.uri)
                    trashToast?.cancel()
                    trashToast = Toast.makeText(getApplication(), "Moved to trash: ${entry.fileName}", Toast.LENGTH_SHORT)
                    trashToast?.show()
                    onDone()
                }
                is TrashResult.NeedsIntent -> onNeedsIntent(result.intentSender)
                is TrashResult.Failed -> onDone()
            }
        }
    }

    // Called after system dialog confirmed — stats + DB only, grid already removed optimistically
    fun recordTrashAndRemove(entry: PhotoEntry) {
        viewModelScope.launch {
            session.addTrashed(entry.sizeMb)
            repo.deleteFromIndex(entry.uri)
        }
    }

    fun removeFromIndex(uri: String) = viewModelScope.launch {
        repo.deleteFromIndex(uri)
        photos.value = photos.value?.filter { it.uri != uri }
    }

    // Optimistic removal — updates UI instantly without waiting for system dialog
    fun removeFromListOptimistic(uri: String) {
        photos.value = photos.value?.filter { it.uri != uri }
    }

    private suspend fun removeFromList(uri: String) {
        repo.deleteFromIndex(uri)
        photos.value = photos.value?.filter { it.uri != uri }
    }

    // Re-fetch photos for the current date — call after restore from trash
    fun reloadCurrentDate() = viewModelScope.launch {
        val date = session.lastVisitedDate
        val type = session.lastVisitedType
        if (date.isEmpty()) return@launch
        photos.value = if (type == "month") repo.getPhotosByMonth(date)
                       else repo.getPhotosByDay(date)
    }
}

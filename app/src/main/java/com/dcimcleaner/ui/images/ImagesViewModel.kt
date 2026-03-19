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
    val session = SessionPrefs(app)

    val photos = MutableLiveData<List<PhotoEntry>>()
    val currentDate = MutableLiveData<String>()
    val currentDateType = MutableLiveData<String>()
    val isCompactGrid = MutableLiveData(false)
    val trashModeEnabled = MutableLiveData(false)
    val hasPrevious = MutableLiveData(false)

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

    fun pickRandomMonth() = viewModelScope.launch {
        val (key, list) = repo.getRandomMonthPhotos()
        if (key.isEmpty()) return@launch
        setDate("month", key)
        photos.value = list
    }

    fun pickRandomDay() = viewModelScope.launch {
        val (key, list) = repo.getRandomDayPhotos()
        if (key.isEmpty()) return@launch
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

    fun toggleGrid() { isCompactGrid.value = !(isCompactGrid.value ?: false) }

    fun trashPhoto(entry: PhotoEntry, onNeedsIntent: (IntentSender) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = repo.moveToTrash(entry)) {
                is TrashResult.Success -> {
                    session.addTrashed(entry.sizeMb)
                    removeFromList(entry.uri)
                    Toast.makeText(getApplication(), "Moved to trash: ${entry.fileName}", Toast.LENGTH_SHORT).show()
                    onDone()
                }
                is TrashResult.NeedsIntent -> onNeedsIntent(result.intentSender)
                is TrashResult.Failed -> onDone()
            }
        }
    }

    fun recordTrashAndRemove(entry: PhotoEntry) {
        viewModelScope.launch {
            session.addTrashed(entry.sizeMb)
            removeFromList(entry.uri)
            Toast.makeText(getApplication(), "Moved to trash: ${entry.fileName}", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeFromIndex(uri: String) = viewModelScope.launch {
        repo.deleteFromIndex(uri)
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

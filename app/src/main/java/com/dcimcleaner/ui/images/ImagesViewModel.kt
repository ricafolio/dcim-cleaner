package com.dcimcleaner.ui.images

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.dcimcleaner.data.model.PhotoEntry
import com.dcimcleaner.data.repository.PhotoRepository
import com.dcimcleaner.data.repository.TrashResult
import kotlinx.coroutines.launch

class ImagesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PhotoRepository(app)

    val photos = MutableLiveData<List<PhotoEntry>>()
    val currentDate = MutableLiveData<String>()
    val currentDateType = MutableLiveData<String>() // "month" or "day"
    val isCompactGrid = MutableLiveData(false)
    val trashModeEnabled = MutableLiveData(false)

    fun pickRandomMonth() {
        viewModelScope.launch {
            val (key, list) = repo.getRandomMonthPhotos()
            currentDate.value = key
            currentDateType.value = "month"
            photos.value = list
        }
    }

    fun pickRandomDay() {
        viewModelScope.launch {
            val (key, list) = repo.getRandomDayPhotos()
            currentDate.value = key
            currentDateType.value = "day"
            photos.value = list
        }
    }

    fun loadByMonth(month: String) {
        viewModelScope.launch {
            currentDate.value = month
            currentDateType.value = "month"
            photos.value = repo.getPhotosByMonth(month)
        }
    }

    fun loadByDay(day: String) {
        viewModelScope.launch {
            currentDate.value = day
            currentDateType.value = "day"
            photos.value = repo.getPhotosByDay(day)
        }
    }

    fun toggleGrid() { isCompactGrid.value = !(isCompactGrid.value ?: false) }
    fun toggleTrashMode() { trashModeEnabled.value = !(trashModeEnabled.value ?: false) }

    fun trashPhoto(entry: PhotoEntry, onNeedsIntent: (IntentSender) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = repo.moveToTrash(entry)) {
                is TrashResult.Success -> {
                    removeFromIndex(entry.uri)
                    onDone()
                }
                is TrashResult.NeedsIntent -> onNeedsIntent(result.intentSender)
                is TrashResult.Failed -> onDone()
            }
        }
    }

    fun removeFromIndex(uri: String) {
        viewModelScope.launch {
            repo.deleteFromIndex(uri)
            photos.value = photos.value?.filter { it.uri != uri }
        }
    }
}

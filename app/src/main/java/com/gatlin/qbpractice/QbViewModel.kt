package com.gatlin.qbpractice

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class Mode(val label: String) { Player("Player"), Moderator("Moderator") }

data class UiState(
    val mode: Mode = Mode.Player,
    val categories: Set<String> = QbApi.CATEGORIES.toSet(),
    val difficulties: Set<Int> = QbApi.DIFFICULTIES.keys,
    val current: Tossup? = null,
    val revealed: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val count: Int = 0,
)

class QbViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("qb", 0)
    private val queue = ArrayDeque<Tossup>()
    private var job: Job? = null
    private var dirty = false

    var ui by mutableStateOf(loadPrefs())
        private set

    init { fetch(showLoading = true) }

    private fun loadPrefs(): UiState {
        val cats = prefs.getStringSet("cats", null)?.filter { it in QbApi.CATEGORIES }?.toSet()
        val diffs = prefs.getStringSet("diffs", null)?.mapNotNull { it.toIntOrNull() }?.toSet()
        return UiState(
            mode = runCatching { Mode.valueOf(prefs.getString("mode", "Player")!!) }.getOrDefault(Mode.Player),
            categories = if (cats.isNullOrEmpty()) QbApi.CATEGORIES.toSet() else cats,
            difficulties = if (diffs.isNullOrEmpty()) QbApi.DIFFICULTIES.keys else diffs,
        )
    }

    fun setMode(m: Mode) {
        prefs.edit().putString("mode", m.name).apply()
        ui = ui.copy(mode = m)
    }

    fun toggleCategory(c: String) {
        val next = if (c in ui.categories) ui.categories - c else ui.categories + c
        if (next.isEmpty()) return // keep at least one subject
        prefs.edit().putStringSet("cats", next).apply()
        ui = ui.copy(categories = next); dirty = true
    }

    fun toggleDifficulty(d: Int) {
        val next = if (d in ui.difficulties) ui.difficulties - d else ui.difficulties + d
        if (next.isEmpty()) return // keep at least one difficulty
        prefs.edit().putStringSet("diffs", next.map { it.toString() }.toSet()).apply()
        ui = ui.copy(difficulties = next); dirty = true
    }

    fun selectAllCategories() {
        prefs.edit().putStringSet("cats", QbApi.CATEGORIES.toSet()).apply()
        ui = ui.copy(categories = QbApi.CATEGORIES.toSet()); dirty = true
    }

    fun selectAllDifficulties() {
        prefs.edit().putStringSet("diffs", QbApi.DIFFICULTIES.keys.map { it.toString() }.toSet()).apply()
        ui = ui.copy(difficulties = QbApi.DIFFICULTIES.keys); dirty = true
    }

    /** Call when the settings sheet closes; reloads only if a filter changed. */
    fun applyFilters() {
        if (!dirty) return
        dirty = false
        queue.clear()
        fetch(showLoading = true)
    }

    fun reveal() { ui = ui.copy(revealed = true) }

    fun next() {
        ui = ui.copy(revealed = false, error = null)
        if (queue.isNotEmpty()) {
            ui = ui.copy(current = queue.removeFirst(), count = ui.count + 1)
            if (queue.size < 3) fetch(showLoading = false)
        } else {
            fetch(showLoading = true)
        }
    }

    fun retry() = fetch(showLoading = true)

    private fun fetch(showLoading: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            if (showLoading) ui = ui.copy(loading = true, current = null, error = null, revealed = false)
            try {
                val list = QbApi.randomTossups(ui.difficulties, ui.categories)
                queue.addAll(list)
                if (ui.current == null) {
                    if (queue.isEmpty()) {
                        ui = ui.copy(loading = false, error = "No questions match these filters.")
                    } else {
                        ui = ui.copy(current = queue.removeFirst(), loading = false, count = ui.count + 1)
                    }
                } else {
                    ui = ui.copy(loading = false)
                }
            } catch (e: Exception) {
                if (showLoading) ui = ui.copy(loading = false, error = e.message ?: "Network error")
            }
        }
    }
}

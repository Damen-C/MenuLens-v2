package com.menulens.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.menulens.app.model.MenuItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.revealedHistoryDataStore by preferencesDataStore(name = "revealed_history")

data class RevealedDishHistoryEntry(
    val id: String,
    val itemId: String,
    val jpText: String,
    val priceText: String?,
    val enTitle: String,
    val enDescription: String,
    val tags: List<String>,
    val imagePath: String?,
    val revealedAtMillis: Long
)

class RevealedHistoryStore(private val context: Context) {
    private val entriesKey = stringPreferencesKey("entries_json")
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<RevealedDishHistoryEntry>>(
            Types.newParameterizedType(List::class.java, RevealedDishHistoryEntry::class.java)
        )

    val entries: Flow<List<RevealedDishHistoryEntry>> = context.revealedHistoryDataStore.data.map { prefs ->
        prefs[entriesKey]?.let { json ->
            runCatching { adapter.fromJson(json).orEmpty() }.getOrElse { emptyList() }
        }.orEmpty().sortedByDescending { it.revealedAtMillis }
    }

    suspend fun saveRevealedDish(item: MenuItem, imagePath: String? = null) {
        updateEntries { current ->
            val id = historyIdFor(item)
            val existing = current.firstOrNull { it.id == id }
            val next = RevealedDishHistoryEntry(
                id = id,
                itemId = item.itemId,
                jpText = item.jpText,
                priceText = item.priceText,
                enTitle = item.preview.enTitle,
                enDescription = item.preview.enDescription,
                tags = item.preview.tags,
                imagePath = imagePath ?: existing?.imagePath,
                revealedAtMillis = System.currentTimeMillis()
            )
            listOf(next) + current.filterNot { it.id == id }
        }
    }

    suspend fun updateImagePath(item: MenuItem, imagePath: String) {
        updateEntries { current ->
            val id = historyIdFor(item)
            current.map {
                if (it.id == id || it.itemId == item.itemId) {
                    it.copy(itemId = item.itemId, imagePath = imagePath)
                } else {
                    it
                }
            }
        }
    }

    suspend fun clear() {
        context.revealedHistoryDataStore.edit { prefs ->
            prefs[entriesKey] = adapter.toJson(emptyList())
        }
    }

    private suspend fun updateEntries(transform: (List<RevealedDishHistoryEntry>) -> List<RevealedDishHistoryEntry>) {
        context.revealedHistoryDataStore.edit { prefs ->
            val current = prefs[entriesKey]?.let { json ->
                runCatching { adapter.fromJson(json).orEmpty() }.getOrElse { emptyList() }
            }.orEmpty()
            prefs[entriesKey] = adapter.toJson(transform(current).take(MAX_HISTORY_ITEMS))
        }
    }

    private fun historyIdFor(item: MenuItem): String {
        val source = listOf(item.jpText, item.priceText.orEmpty(), item.preview.enTitle)
            .joinToString("|")
            .lowercase()
            .trim()
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_HISTORY_ITEMS = 50
    }
}

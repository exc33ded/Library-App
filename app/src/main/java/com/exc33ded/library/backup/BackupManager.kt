package com.exc33ded.library.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.exc33ded.library.data.db.AppDatabase
import com.exc33ded.library.profile.ProfileRepository
import com.exc33ded.library.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AppDatabase,
    private val profileRepo: ProfileRepository,
    private val settingsRepo: SettingsRepository,
) {
    /** Writes the whole library to [uri] as a zip; returns the number of books (incl. soft-deleted). */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val books = db.bookDao().allIncludingDeleted()
        val cats = db.categoryDao().allIncludingDeleted()
        val links = db.categoryDao().allLinksIncludingDeleted()
        val profile = profileRepo.profile.first()
        val covers = mutableMapOf<String, File>()
        val bookDtos = books.map { book ->
            val file = book.coverPath?.let(::File)?.takeIf { it.isFile }
            if (file != null) {
                val name = "covers/${book.id}.${file.extension.ifEmpty { "jpg" }}"
                covers[name] = file
                book.toDto(coverFile = name)
            } else {
                book.toDto(coverFile = null)
            }
        }
        val backup = BackupFile(
            exportedAt = System.currentTimeMillis(),
            books = bookDtos,
            categories = cats.map { it.toDto() },
            bookCategories = links.map { it.toDto() },
            profile = ProfileDto(profile.name, profile.bio, profile.yearlyGoal),
        )
        val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Couldn't open the destination file")
        BackupZip.write(out, backup, covers)
        settingsRepo.update { it.copy(lastExportAt = System.currentTimeMillis()) }
        books.size
    }

    /** Merges the backup at [uri] into the library (newer updatedAt wins). */
    suspend fun import(uri: Uri): MergeSummary = withContext(Dispatchers.IO) {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Couldn't open the backup file")
        val backup = BackupZip.read(input, File(ctx.filesDir, "covers"))
        val summary = db.withTransaction {
            val bookDao = db.bookDao()
            val catDao = db.categoryDao()
            val existingBooks = bookDao.allIncludingDeleted().associateBy { it.id }
            val existingCats = catDao.allIncludingDeleted().associate { it.id to it.updatedAt }
            val existingLinks = catDao.allLinksIncludingDeleted().associate { it.id to it.updatedAt }

            val books = mergeNewer(backup.books, existingBooks.mapValues { it.value.updatedAt }, { it.id }, { it.updatedAt })
            books.forEach { row ->
                val restored = row.coverFile?.let { File(ctx.filesDir, it) }?.takeIf { it.isFile }?.absolutePath
                bookDao.upsert(row.toEntity(coverPath = restored ?: existingBooks[row.id]?.coverPath))
            }
            val cats = mergeNewer(backup.categories, existingCats, { it.id }, { it.updatedAt })
            cats.forEach { catDao.upsert(it.toEntity()) }
            val links = mergeNewer(backup.bookCategories, existingLinks, { it.id }, { it.updatedAt })
            links.forEach { catDao.upsertLink(it.toEntity()) }

            val merged = books.size + cats.size + links.size
            val updated = books.count { it.id in existingBooks } +
                cats.count { it.id in existingCats } + links.count { it.id in existingLinks }
            val total = backup.books.size + backup.categories.size + backup.bookCategories.size
            MergeSummary(added = merged - updated, updated = updated, unchanged = total - merged)
        }
        val p = backup.profile
        if (p != null && profileRepo.profile.first().name.isBlank()) {
            profileRepo.update { it.copy(name = p.name, bio = p.bio, yearlyGoal = p.yearlyGoal) }
        }
        summary
    }
}

package com.fsck.k9.ui.messagelist

import android.database.Cursor
import com.fsck.k9.helper.EmptyCursor
import com.fsck.k9.helper.MergeCursorWithUniqueId

class CursorsAccumulator constructor(
    private val accountUids: Array<String>
) {
    private val cursorAccumulaor: Array<Cursor?> = Array(accountUids.size) { null }

    private val notNullCursors = cursorAccumulaor.filterNotNull().toTypedArray()

    val isMerged: Boolean get() = notNullCursors.size > 1

    fun add(cursor: Cursor) {
        cursorAccumulaor[notNullCursors.size] = cursor
    }

    fun all(comparator: Comparator<Cursor>): Cursor {
        val notNullCursors = cursorAccumulaor.filterNotNull().toTypedArray()

        return when {
            notNullCursors.isEmpty() -> EmptyCursor()
            notNullCursors.size == 1 -> notNullCursors.first()
            else -> MergeCursorWithUniqueId(notNullCursors, comparator)
        }
    }
}

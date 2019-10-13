package com.fsck.k9.ui.messagelist

import androidx.annotation.ColorInt

data class MessageListItem(
        val id: Long,
        val displayName: String,
        val subject: String,
        val displayDate: String,
        val sigil: String,
        val threadCount: Int,
        val read: Boolean,
        val answered: Boolean,
        val forwarded: Boolean,
        val selected: Boolean,
        @ColorInt val chipColor: Int,
        val flagged: Boolean,
        val hasAttachments: Boolean,
        val preview: String
)
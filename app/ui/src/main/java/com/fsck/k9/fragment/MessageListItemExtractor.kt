package com.fsck.k9.fragment

import android.content.res.Resources
import android.database.Cursor
import com.fsck.k9.Account
import com.fsck.k9.helper.MessageHelper
import com.fsck.k9.mail.Address
import com.fsck.k9.mailstore.DatabasePreviewType
import com.fsck.k9.ui.R

class MessageListItemExtractor(
        private val account: Account,
        private val cursor: Cursor,
        private val messageHelper: MessageHelper,
        private val res: Resources
) {

    private val ccMe: Boolean get() = messageHelper.toMe(account, ccAddresses)
    private val fromMe: Boolean get() = messageHelper.toMe(account, fromAddresses)
    private val toMe: Boolean get() = messageHelper.toMe(account, toAddresses)

    val ccAddresses: Array<Address>
        get() = Address.unpack(cursor.getString(MLFProjectionInfo.CC_LIST_COLUMN))

    val counterPartyAddresses: Address?
    get() {
        if (fromMe) {
            if (toAddresses.isNotEmpty()) {
                return toAddresses[0]
            } else if (ccAddresses.isNotEmpty()) {
                return ccAddresses[0]
            }
        } else if (fromAddresses.isNotEmpty()) {
            return fromAddresses[0]
        }
        return null
    }

    val displayName: CharSequence
        get() = messageHelper.getDisplayName(account, fromAddresses, toAddresses)

    val date: Long get() = cursor.getLong(MLFProjectionInfo.DATE_COLUMN)

    val flagged: Boolean get() = cursor.getInt(MLFProjectionInfo.FLAGGED_COLUMN) == 1

    val fromAddresses: Array<Address>
        get() = Address.unpack(cursor.getString(MLFProjectionInfo.SENDER_LIST_COLUMN))

    val hasAttachments: Boolean get() = cursor.getInt(MLFProjectionInfo.ATTACHMENT_COUNT_COLUMN) > 0

    val preview: String
        get() {
            val previewTypeString = cursor.getString(MLFProjectionInfo.PREVIEW_TYPE_COLUMN)
            val previewType = DatabasePreviewType.fromDatabaseValue(previewTypeString)

            return when (previewType) {
                DatabasePreviewType.NONE, DatabasePreviewType.ERROR -> {
                    ""
                }
                DatabasePreviewType.ENCRYPTED -> {
                    res.getString(R.string.preview_encrypted)
                }
                DatabasePreviewType.TEXT -> {
                    cursor.getString(MLFProjectionInfo.PREVIEW_COLUMN)
                }
                null -> throw AssertionError("Unknown preview type: $previewType")
            }
        }

    val sigil: String
        get() {
            return when {
                toMe -> res.getString(R.string.messagelist_sent_to_me_sigil)
                ccMe -> res.getString(R.string.messagelist_sent_cc_me_sigil)
                else -> ""
            }
        }

    val threadCount: Int get() = cursor.getInt(MLFProjectionInfo.THREAD_COUNT_COLUMN)

    val toAddresses: Array<Address>
        get() = Address.unpack(cursor.getString(MLFProjectionInfo.TO_LIST_COLUMN))

    fun subject(threadCount: Int): String {
        return MlfUtils.buildSubject(cursor.getString(MLFProjectionInfo.SUBJECT_COLUMN),
                res.getString(R.string.general_no_subject), threadCount)
    }
}
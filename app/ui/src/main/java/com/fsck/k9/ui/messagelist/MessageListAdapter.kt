package com.fsck.k9.ui.messagelist


import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.format.DateUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import android.widget.TextView
import com.fsck.k9.Account
import com.fsck.k9.K9
import com.fsck.k9.Preferences
import com.fsck.k9.contacts.ContactPictureLoader
import com.fsck.k9.controller.MessageReference
import com.fsck.k9.fragment.MessageViewHolder
import com.fsck.k9.helper.MessageHelper
import com.fsck.k9.mail.Address
import com.fsck.k9.mailstore.DatabasePreviewType
import com.fsck.k9.ui.R
import com.fsck.k9.ui.messagelist.MLFProjectionInfo.*
import kotlinx.android.synthetic.main.message_list_item.view.*
import kotlin.math.max


class MessageListAdapter constructor(
        context: Context,
        theme: Resources.Theme,
        private val toggleMessageSelectWithAdapterPosition: (Int) -> Unit,
        private val toggleMessageFlagWithAdapterPosition: (Int) -> Unit,
        private val res: Resources,
        private val layoutInflater: LayoutInflater,
        private val messageHelper: MessageHelper,
        private val accountRetriever: AccountRetriever,
        private val contactPictureLoader: ContactPictureLoader,
        private val showThreadedList: Boolean
) : CursorAdapter(context, null, 0) {

    var activeMessage: MessageReference? = null
    var selected: MutableSet<Long> = mutableSetOf()
    var uniqueIdColumn: Int = 0

    private val senderAboveSubject: Boolean get() = K9.isMessageListSenderAboveSubject
    private val checkboxes: Boolean get() = K9.isShowMessageListCheckboxes
    private val stars: Boolean get() = K9.isShowMessageListStars
    private val previewLines: Int get() = K9.messageListPreviewLines
    private val showContactPicture: Boolean get() = K9.isShowContactPicture
    private val fontSizes = K9.fontSizes
    private val listItemThemeProps = ListItemThemeProps(theme, res)

    /**
     * Create a span section for the sender, and assign the correct font size and weight
     */
    private val senderSpan: AbsoluteSizeSpan
        get() = AbsoluteSizeSpan(
                if (senderAboveSubject) fontSizes.messageListSubject else fontSizes.messageListSender,
                true
        )

    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        val view = layoutInflater.inflate(R.layout.message_list_item, parent, false)

        val compact = (previewLines == 0 && showContactPicture.not())
        val holder = MessageViewHolder(
                view,
                compact,
                senderAboveSubject,
                toggleMessageSelectWithAdapterPosition,
                toggleMessageFlagWithAdapterPosition,
                fontSizes
        )

        if (showContactPicture.not()) {
            holder.contactBadge.visibility = View.GONE
        }

        fontSizes.setViewTextSize(holder.date, fontSizes.messageListDate)

        // 1 preview line is needed even if it is set to 0, because subject is part of the same text view
        holder.preview.setLines(max(previewLines, 1))
        fontSizes.setViewTextSize(holder.preview, fontSizes.messageListPreview)
        fontSizes.setViewTextSize(holder.threadCount, fontSizes.messageListSubject) // thread count is next to subject
        view.selected_checkbox_wrapper.visibility = if (checkboxes) View.VISIBLE else View.GONE

        holder.flagged.visibility = if (stars) View.VISIBLE else View.GONE
        holder.flagged.setOnClickListener(holder)
        holder.selected.setOnClickListener(holder)

        view.tag = holder

        return view
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        val account = accountRetriever(cursor)

        val fromList = cursor.getString(SENDER_LIST_COLUMN)
        val toList = cursor.getString(TO_LIST_COLUMN)
        val ccList = cursor.getString(CC_LIST_COLUMN)
        val fromAddrs = Address.unpack(fromList)
        val toAddrs = Address.unpack(toList)
        val ccAddrs = Address.unpack(ccList)

        val fromMe = fromAddrs.contains(account)
        val toMe = toAddrs.contains(account)
        val ccMe = ccAddrs.contains(account)

        val displayName = messageHelper.getDisplayName(account, fromAddrs, toAddrs)
        val displayDate = DateUtils.getRelativeTimeSpanString(context, cursor.getLong(DATE_COLUMN))

        val counterpartyAddress = fetchCounterPartyAddress(fromMe, toAddrs, ccAddrs, fromAddrs)

        val threadCount = if (showThreadedList) cursor.getInt(THREAD_COUNT_COLUMN) else 0

        val subject = MlfUtils.buildSubject(
                cursor.getString(SUBJECT_COLUMN),
                res.getString(R.string.general_no_subject),
                threadCount
        )

        val read = cursor.getInt(READ_COLUMN) == 1
        val flagged = cursor.getInt(FLAGGED_COLUMN) == 1
        val answered = cursor.getInt(ANSWERED_COLUMN) == 1
        val forwarded = cursor.getInt(FORWARDED_COLUMN) == 1

        val hasAttachments = cursor.getInt(ATTACHMENT_COUNT_COLUMN) > 0

        val holder = view.tag as MessageViewHolder

        val maybeBoldTypeface = if (read) Typeface.NORMAL else Typeface.BOLD

        val uniqueId = cursor.getLong(uniqueIdColumn)
        val selected = selected.contains(uniqueId)

        holder.chip.setBackgroundColor(account.chipColor)
        if (checkboxes) {
            holder.selected.isChecked = selected
        }
        if (stars) {
            holder.flagged.isChecked = flagged
        }
        holder.position = cursor.position
        updateContactBadge(holder, counterpartyAddress)

        setBackgroundColor(view, selected, read)
        if (activeMessage != null) {
            changeBackgroundColorIfActiveMessage(cursor, account, view)
        }
        updateWithThreadCount(holder, threadCount)
        val beforePreviewText = if (senderAboveSubject) subject else displayName
        val sigil = recipientSigil(toMe, ccMe)
        holder.preview.setText(
                generatePreview(sigil, beforePreviewText, cursor),
                TextView.BufferType.SPANNABLE
        )

        formatPreviewText(holder.preview, beforePreviewText, sigil)

        holder.from?.apply {
            typeface = Typeface.create(typeface, maybeBoldTypeface)
            if (senderAboveSubject) {
                text = displayName
            } else {
                text = SpannableStringBuilder(sigil).append(displayName)
            }
        }
        holder.subject?.apply {
            typeface = Typeface.create(typeface, maybeBoldTypeface)
            text = subject
        }
        holder.date.text = displayDate
        holder.attachment.visibility = if (hasAttachments) View.VISIBLE else View.GONE

        val statusHolder = buildStatusHolder(forwarded, answered)
        if (statusHolder != null) {
            holder.status.setImageDrawable(statusHolder)
            holder.status.visibility = View.VISIBLE
        } else {
            holder.status.visibility = View.GONE
        }
    }

    private inline fun Array<Address>.contains(account: Account): Boolean {
        return this.any { account.isAnIdentity(it) }
    }

    private fun generatePreview(sigil: String, beforePreviewText: CharSequence?, cursor: Cursor): String {
        val messageStringBuilder = SpannableStringBuilder(sigil)
                .append(beforePreviewText)
        if (previewLines > 0) {
            val preview = getPreview(cursor)
            messageStringBuilder.append(" ").append(preview)
        }
        return messageStringBuilder.toString()
    }

    private fun recipientSigil(toMe: Boolean, ccMe: Boolean): String {
        return when {
            toMe -> res.getString(R.string.messagelist_sent_to_me_sigil)
            ccMe -> res.getString(R.string.messagelist_sent_cc_me_sigil)
            else -> ""
        }
    }

    private fun formatPreviewText(preview: TextView, beforePreviewText: CharSequence, sigil: String) {
        val previewText = preview.text as Spannable
        previewText.setSpan(senderSpan, 0, beforePreviewText.length + sigil.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Set span (color) for preview message
        previewText.setSpan(ForegroundColorSpan(listItemThemeProps.previewTextColor), beforePreviewText.length + sigil.length,
                previewText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun fetchCounterPartyAddress(
            fromMe: Boolean,
            toAddrs: Array<Address>,
            ccAddrs: Array<Address>,
            fromAddrs: Array<Address>
    ): Address? {
        if (fromMe) {
            return toAddrs.firstOrNull() ?: ccAddrs.firstOrNull()
        } else if (fromAddrs.isNotEmpty()) {
            return fromAddrs[0]
        }
        return null
    }

    private fun updateContactBadge(holder: MessageViewHolder, counterpartyAddress: Address?) {
        if (counterpartyAddress != null) {
            holder.contactBadge.setContact(counterpartyAddress)
            /*
            * At least in Android 2.2 a different background + padding is used when no
            * email address is available. ListView reuses the views but ContactBadge
            * doesn't reset the padding, so we do it ourselves.
            */
            holder.contactBadge.setPadding(0, 0, 0, 0)
            contactPictureLoader.setContactPicture(holder.contactBadge, counterpartyAddress)
        } else {
            holder.contactBadge.assignContactUri(null)
            holder.contactBadge.setImageResource(R.drawable.ic_contact_picture)
        }
    }

    private fun changeBackgroundColorIfActiveMessage(cursor: Cursor, account: Account, view: View) {
        val uid = cursor.getString(UID_COLUMN)
        val folderServerId = cursor.getString(FOLDER_SERVER_ID_COLUMN)

        if (account.uuid == activeMessage?.accountUuid &&
                folderServerId == activeMessage?.folderServerId &&
                uid == activeMessage?.uid) {
            view.setBackgroundColor(listItemThemeProps.activeItemBackgroundColor)
        }
    }

    private fun buildStatusHolder(forwarded: Boolean, answered: Boolean): Drawable? {
        if (forwarded && answered) {
            return listItemThemeProps.forwardedAnsweredIcon
        } else if (answered) {
            return listItemThemeProps.answeredIcon
        } else if (forwarded) {
            return listItemThemeProps.forwardedIcon
        }
        return null
    }

    private fun setBackgroundColor(view: View, selected: Boolean, read: Boolean) {
        if (selected || K9.isUseBackgroundAsUnreadIndicator) {
            val color: Int = when {
                selected -> listItemThemeProps.selectedItemBackgroundColor
                read -> listItemThemeProps.readItemBackgroundColor
                else -> listItemThemeProps.unreadItemBackgroundColor
            }
            view.setBackgroundColor(color)
        } else {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun updateWithThreadCount(holder: MessageViewHolder, threadCount: Int) {
        if (threadCount > 1) {
            holder.threadCount.text = "%d".format(threadCount)
            holder.threadCount.visibility = View.VISIBLE
        } else {
            holder.threadCount.visibility = View.GONE
        }
    }

    private fun getPreview(cursor: Cursor): String {
        val previewTypeString = cursor.getString(PREVIEW_TYPE_COLUMN)
        val previewType = DatabasePreviewType.fromDatabaseValue(previewTypeString)

        when {
            previewType == DatabasePreviewType.NONE || previewType == DatabasePreviewType.ERROR -> {
                return ""
            }
            previewType == DatabasePreviewType.ENCRYPTED -> {
                return res.getString(R.string.preview_encrypted)
            }
            previewType == DatabasePreviewType.TEXT -> {
                return cursor.getString(PREVIEW_COLUMN)
            }
        }

        throw AssertionError("Unknown preview type: $previewType")
    }
}

// FIXME move to a separate package once decoupled further.
class AccountRetriever constructor(
        private val preferences: Preferences
) : (Cursor) -> Account {
    override fun invoke(cursor: Cursor): Account {
        val accountUuid = cursor.getString(ACCOUNT_UUID_COLUMN)
        return preferences.getAccount(accountUuid)
    }
}
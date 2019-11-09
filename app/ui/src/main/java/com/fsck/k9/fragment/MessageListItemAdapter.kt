package com.fsck.k9.fragment


import android.content.res.Resources
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
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import com.fsck.k9.FontSizes
import com.fsck.k9.contacts.ContactPictureLoader
import com.fsck.k9.controller.MessageReference
import com.fsck.k9.mail.Address
import com.fsck.k9.ui.ContactBadge
import com.fsck.k9.ui.R
import com.fsck.k9.ui.messagelist.MessageListAppearance
import com.fsck.k9.ui.messagelist.MessageListItem
import kotlin.math.max

class MessageListItemAdapter internal constructor(
        theme: Resources.Theme,
        res: Resources,
        private val layoutInflater: LayoutInflater,
        private val contactsPictureLoader: ContactPictureLoader,
        private val listItemListener: MessageListItemActionListener,
        private val appearance: MessageListAppearance
) : BaseAdapter() {

    var data: List<MessageListItem> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItem(position: Int): MessageListItem = data[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = data.size


    private val forwardedIcon: Drawable
    private val answeredIcon: Drawable
    private val forwardedAnsweredIcon: Drawable
    private val previewTextColor: Int
    private val activeItemBackgroundColor: Int
    private val selectedItemBackgroundColor: Int
    private val readItemBackgroundColor: Int
    private val unreadItemBackgroundColor: Int

    init {

        val attributes = intArrayOf(
                R.attr.messageListAnswered,
                R.attr.messageListForwarded,
                R.attr.messageListAnsweredForwarded,
                R.attr.messageListPreviewTextColor,
                R.attr.messageListActiveItemBackgroundColor,
                R.attr.messageListSelectedBackgroundColor,
                R.attr.messageListReadItemBackgroundColor,
                R.attr.messageListUnreadItemBackgroundColor
        )

        val array = theme.obtainStyledAttributes(attributes)

        answeredIcon = res.getDrawable(array.getResourceId(0, R.drawable.ic_messagelist_answered_dark))
        forwardedIcon = res.getDrawable(array.getResourceId(1, R.drawable.ic_messagelist_forwarded_dark))
        forwardedAnsweredIcon = res.getDrawable(array.getResourceId(2, R.drawable.ic_messagelist_answered_forwarded_dark))
        previewTextColor = array.getColor(3, Color.BLACK)
        activeItemBackgroundColor = array.getColor(4, Color.BLACK)
        selectedItemBackgroundColor = array.getColor(5, Color.BLACK)
        readItemBackgroundColor = array.getColor(6, Color.BLACK)
        unreadItemBackgroundColor = array.getColor(7, Color.BLACK)

        array.recycle()
    }

    var activeMessage: MessageReference? = null

    var selected: Set<Long> = emptySet()

    private inline val subjectViewFontSize: Int
        get() = if (appearance.senderAboveSubject) {
            appearance.fontSizes.messageListSender
        } else {
            appearance.fontSizes.messageListSubject
        }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: layoutInflater.inflate(R.layout.message_list_item, parent, false)
        val holder = (convertView?.tag as? MessageViewHolder)
                ?: MessageViewHolder(view, listItemListener)

        holder.contactBadge.isVisible = appearance.showContactPicture
        holder.chip.isVisible = appearance.showAccountChip

        appearance.fontSizes.setViewTextSize(holder.subject, subjectViewFontSize)

        appearance.fontSizes.setViewTextSize(holder.date, appearance.fontSizes.messageListDate)

        // 1 preview line is needed even if it is set to 0, because subject is part of the same text view
        holder.preview.setLines(max(appearance.previewLines, 1))
        appearance.fontSizes.setViewTextSize(holder.preview, appearance.fontSizes.messageListPreview)
        appearance.fontSizes.setViewTextSize(holder.threadCount, appearance.fontSizes.messageListSubject) // thread count is next to subject

        holder.flagged.isVisible = appearance.stars
        holder.flagged.setOnClickListener(holder)
        bindView(view, position)

        view.tag = holder

        return view
    }

    private fun bindView(view: View, position: Int) {
        val listItem = data[position]
        val holder = view.tag as MessageViewHolder

        val displayName = listItem.displayName
        val displayDate = DateUtils.getRelativeTimeSpanString(view.context, listItem.date)
        val threadCount = if (appearance.showingThreadedList) listItem.threadCount else 0
        val subject = listItem.subject
        val read = listItem.read
        val answered = listItem.answered
        val forwarded = listItem.forwarded
        val maybeBoldTypeface = if (read) Typeface.NORMAL else Typeface.BOLD
        val selected = selected.contains(listItem.selectionIdentifier)

        if (appearance.showAccountChip) {
            val accountChipDrawable = holder.chip.drawable.mutate()
            DrawableCompat.setTint(accountChipDrawable, listItem.chipColor)
            holder.chip.setImageDrawable(accountChipDrawable)
        }

        holder.flagged.isChecked = appearance.stars && listItem.flagged

        holder.position = position
        if (holder.contactBadge.isVisible) {
            val counterpartyAddress = listItem.counterPartyAddresses
            updateContactBadge(holder.contactBadge, counterpartyAddress)
        }

        setBackgroundColor(view, selected, read, isActiveMessage(listItem, activeMessage))

        holder.threadCount.isVisible = threadCount > 1
        if (holder.threadCount.isVisible) {
            holder.threadCount.text = "%d".format(threadCount)
        }

        setupPreview(holder, listItem)

        holder.subject.typeface = Typeface.create(holder.subject.typeface, maybeBoldTypeface)
        if (appearance.senderAboveSubject) {
            holder.subject.text = displayName
        } else {
            holder.subject.text = subject
        }

        holder.date.text = displayDate
        holder.attachment.isVisible = listItem.hasAttachments

        holder.status.isVisible = answered || forwarded
        if (holder.status.isVisible) {
            holder.status.setImageDrawable(getStatusDrawable(forwarded, answered))
        }
    }

    private fun setupPreview(holder: MessageViewHolder, listItem: MessageListItem) {
        val sigil = listItem.sigil
        val previewTextStart = if (appearance.senderAboveSubject) listItem.subject else listItem.displayName
        val previewTextBuilder = SpannableStringBuilder(sigil)
                .append(previewTextStart)
        if (appearance.previewLines > 0) {
            val preview = listItem.preview
            previewTextBuilder.append(" ").append(preview)
        }
        holder.preview.setText(previewTextBuilder, TextView.BufferType.SPANNABLE)

        formatPreviewText(holder.preview, sigil.length + previewTextStart.length)
    }

    private fun formatPreviewText(
            preview: TextView,
            lenghthToSetup: Int
    ) {
        val previewText = preview.text as Spannable

        setPrevieFontSize(previewText, lenghthToSetup)

        // Set span (color) for preview message
        previewText.setSpan(
                ForegroundColorSpan(previewTextColor),
                lenghthToSetup,
                previewText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun setPrevieFontSize(text: Spannable, length: Int) {
        val fontSize = if (appearance.senderAboveSubject) {
            appearance.fontSizes.messageListSubject
        } else {
            appearance.fontSizes.messageListSender
        }

        if (fontSize != FontSizes.FONT_DEFAULT) {
            val span = AbsoluteSizeSpan(fontSize, true)
            text.setSpan(span, 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun isActiveMessage(item: MessageListItem, activeMessage: MessageReference?): Boolean {
        if (activeMessage == null) return false

        val uid = item.messageUid
        val folderServerId = item.folderServerId

        val activeAccountUuid = activeMessage.accountUuid
        val activeFolderServerId = activeMessage.folderServerId
        val activeUid = activeMessage.uid
        return item.account.uuid == activeAccountUuid
                && folderServerId == activeFolderServerId
                && uid == activeUid
    }

    private fun updateContactBadge(contactBadge: ContactBadge, counterpartyAddress: Address?) {
        if (counterpartyAddress != null) {
            contactBadge.setContact(counterpartyAddress)
            /*
                     * At least in Android 2.2 a different background + padding is used when no
                     * email address is available. ListView reuses the views but ContactBadge
                     * doesn't reset the padding, so we do it ourselves.
                     */
            contactBadge.setPadding(0, 0, 0, 0)
            contactsPictureLoader.setContactPicture(contactBadge, counterpartyAddress)
        } else {
            contactBadge.assignContactUri(null)
            contactBadge.setImageResource(R.drawable.ic_contact_picture)
        }
    }

    private fun getStatusDrawable(forwarded: Boolean, answered: Boolean): Drawable? {
        if (forwarded && answered) {
            return forwardedAnsweredIcon
        } else if (answered) {
            return answeredIcon
        } else if (forwarded) {
            return forwardedIcon
        }
        return null
    }

    private fun setBackgroundColor(view: View, selected: Boolean, read: Boolean,
                                   isActive: Boolean) {
        if (selected || appearance.backGroundAsReadIndicator) {
            val color: Int = when {
                selected -> selectedItemBackgroundColor
                read -> readItemBackgroundColor
                else -> unreadItemBackgroundColor
            }

            view.setBackgroundColor(color)
        } else if (isActive) {
            view.setBackgroundColor(activeItemBackgroundColor)
        } else {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    fun clearData() {
        data = emptyList()
    }
}
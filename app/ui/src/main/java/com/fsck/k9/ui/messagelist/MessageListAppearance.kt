package com.fsck.k9.ui.messagelist

import com.fsck.k9.K9

data class MessageListAppearance(
        val checkboxes: Boolean = K9.isShowMessageListCheckboxes,
        val previewLines: Int = K9.messageListPreviewLines,
        val stars: Boolean = K9.isShowMessageListStars,
        val senderAboveSubject: Boolean = K9.isMessageListSenderAboveSubject,
        val showContactPicture: Boolean = K9.isShowContactPicture
)
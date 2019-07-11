package com.fsck.k9.ui.messagelist

import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import com.fsck.k9.ui.R

class ListItemThemeProps(
        theme: Resources.Theme,
        res: Resources
) {

    companion object {
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
    }

    val forwardedIcon: Drawable
    val answeredIcon: Drawable
    val forwardedAnsweredIcon: Drawable
    val previewTextColor: Int
    val activeItemBackgroundColor: Int
    val selectedItemBackgroundColor: Int
    val readItemBackgroundColor: Int
    val unreadItemBackgroundColor: Int

    // TODO Check with themeing and properly default colors
    init {
        val array = theme.obtainStyledAttributes(attributes)

        answeredIcon = res.getDrawable(array.getResourceId(
                0,
                R.drawable.ic_messagelist_answered_dark
        ))
        forwardedIcon = res.getDrawable(array.getResourceId(
                1,
                R.drawable.ic_messagelist_forwarded_dark
        ))
        forwardedAnsweredIcon = res.getDrawable(array.getResourceId(
                2,
                R.drawable.ic_messagelist_answered_forwarded_dark
        ))
        previewTextColor = array.getColor(
                3,
                Color.BLACK
        )
        activeItemBackgroundColor = array.getColor(
                4,
                Color.BLACK
        )
        selectedItemBackgroundColor = array.getColor(
                5,
                Color.BLACK
        )
        readItemBackgroundColor = array.getColor(
                6,
                Color.BLACK
        )
        unreadItemBackgroundColor = array.getColor(
                7,
                Color.BLACK
        )
        array.recycle()
    }


}
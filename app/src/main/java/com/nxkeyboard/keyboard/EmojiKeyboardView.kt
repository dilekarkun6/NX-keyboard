package com.nxkeyboard.keyboard

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.emoji2.widget.EmojiTextView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nxkeyboard.theme.ThemeManager
import com.nxkeyboard.utils.HapticHelper
import com.nxkeyboard.utils.RecentEmojiManager

class EmojiKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private var onEmojiSelected: ((String) -> Unit)? = null
    private var onBackspace: (() -> Unit)? = null
    private var onCloseEmoji: (() -> Unit)? = null
    private var themeManager: ThemeManager? = null

    private val tabsScroll = HorizontalScrollView(context)
    private val tabsContainer = LinearLayout(context)
    private val grid = RecyclerView(context)
    private val bottomBar = LinearLayout(context)
    private val backspaceButton = Button(context)
    private val abcButton = Button(context)
    private var currentCategory = EmojiData.Category.SMILEYS

    init {
        orientation = VERTICAL
        setupTabs()
        setupGrid()
        setupBottomBar()
    }

    fun configure(
        themeManager: ThemeManager,
        onEmojiSelected: (String) -> Unit,
        onBackspace: () -> Unit,
        onCloseEmoji: () -> Unit
    ) {
        this.themeManager = themeManager
        this.onEmojiSelected = onEmojiSelected
        this.onBackspace = onBackspace
        this.onCloseEmoji = onCloseEmoji
        applyTheme()
        showCategory(EmojiData.Category.SMILEYS)
        com.nxkeyboard.EmojiCompatState.addListener(emojiReadyListener)
    }

    private val emojiReadyListener: () -> Unit = {
        post { (grid.adapter as? EmojiAdapter)?.notifyDataSetChanged() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        com.nxkeyboard.EmojiCompatState.removeListener(emojiReadyListener)
    }

    private fun setupTabs() {
        tabsContainer.orientation = HORIZONTAL
        tabsScroll.addView(
            tabsContainer,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
        addView(
            tabsScroll,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(40))
        )

        for (category in EmojiData.Category.entries) {
            val tab = EmojiTextView(context).apply {
                text = category.icon
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                setOnClickListener {
                    HapticHelper.keyPress(this)
                    showCategory(category)
                }
            }
            tabsContainer.addView(tab)
        }
    }

    private fun setupGrid() {
        grid.layoutManager = GridLayoutManager(context, 8)
        grid.adapter = EmojiAdapter(emptyList()) { emoji ->
            onEmojiSelected?.invoke(emoji)
            RecentEmojiManager.add(context, emoji)
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun setupBottomBar() {
        bottomBar.orientation = HORIZONTAL
        bottomBar.setPadding(dp(8), dp(4), dp(8), dp(4))

        abcButton.text = "ABC"
        abcButton.setOnClickListener {
            HapticHelper.keyPress(this)
            onCloseEmoji?.invoke()
        }
        bottomBar.addView(
            abcButton,
            LayoutParams(0, dp(40), 1f)
        )

        backspaceButton.text = "⌫"
        backspaceButton.setOnClickListener {
            HapticHelper.keyPress(this)
            onBackspace?.invoke()
        }
        bottomBar.addView(
            backspaceButton,
            LayoutParams(0, dp(40), 1f)
        )

        addView(
            bottomBar,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
    }

    private fun showCategory(category: EmojiData.Category) {
        currentCategory = category
        val emojis = if (category == EmojiData.Category.RECENT) {
            RecentEmojiManager.getAll(context)
        } else {
            EmojiData.forCategory(category)
        }
        (grid.adapter as? EmojiAdapter)?.update(emojis)
    }

    fun applyTheme() {
        val dark = themeManager?.isDarkActive() ?: false
        val bg = if (dark) Color.parseColor("#121212") else Color.parseColor("#F5F5F5")
        val text = if (dark) Color.WHITE else Color.parseColor("#212121")
        setBackgroundColor(bg)
        tabsScroll.setBackgroundColor(bg)
        grid.setBackgroundColor(bg)
        bottomBar.setBackgroundColor(bg)
        abcButton.setTextColor(text)
        backspaceButton.setTextColor(text)
        for (i in 0 until tabsContainer.childCount) {
            (tabsContainer.getChildAt(i) as? TextView)?.setTextColor(text)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private class EmojiAdapter(
        private var items: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<EmojiAdapter.VH>() {

        class VH(view: EmojiTextView) : RecyclerView.ViewHolder(view) {
            val text: EmojiTextView = view
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val tv = EmojiTextView(parent.context).apply {
                textSize = 22f
                gravity = Gravity.CENTER
                val pad = (8 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            }
            return VH(tv)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val emoji = items[position]
            holder.text.text = emoji
            holder.text.setOnClickListener { onClick(emoji) }
        }

        fun update(newItems: List<String>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}

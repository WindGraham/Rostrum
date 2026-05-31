package com.rostrum.core.network

import android.content.Context
import android.graphics.Typeface
import android.text.Spanned
import android.widget.TextView
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.lang.ref.WeakReference

/**
 * Markdown 渲染器
 * 
 * 使用 Markwon 库进行 Markdown 渲染
 * 支持：
 * - 标准 Markdown 语法
 * - 删除线 ~~text~~
 * - 表格
 * - 自动链接识别
 * 
 * @author OmniMaster
 * @license Apache-2.0
 */
object MarkdownRenderer {
    
    private var markwonRef: WeakReference<Markwon>? = null
    private var lastContextHash: Int = 0
    
    /**
     * 获取或创建 Markwon 实例
     * 使用弱引用缓存，避免内存泄漏
     */
    fun getInstance(context: Context): Markwon {
        val contextHash = context.applicationContext.hashCode()
        val cached = markwonRef?.get()
        
        if (cached != null && lastContextHash == contextHash) {
            return cached
        }
        
        val markwon = Markwon.builder(context.applicationContext)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context.applicationContext))
            .usePlugin(LinkifyPlugin.create())
            .build()
        
        markwonRef = WeakReference(markwon)
        lastContextHash = contextHash
        
        return markwon
    }
    
    /**
     * 创建自定义主题的 Markwon 实例
     */
    fun createCustom(
        context: Context,
        codeBlockBackgroundColor: Int? = null,
        codeTextColor: Int? = null,
        linkColor: Int? = null,
        codeTypeface: Typeface? = null
    ): Markwon {
        return Markwon.builder(context.applicationContext)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context.applicationContext))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    codeBlockBackgroundColor?.let { builder.codeBlockBackgroundColor(it) }
                    codeTextColor?.let { builder.codeTextColor(it) }
                    linkColor?.let { builder.linkColor(it) }
                    codeTypeface?.let { builder.codeTypeface(it) }
                }
            })
            .build()
    }
    
    /**
     * 渲染 Markdown 文本
     * 
     * @param context 上下文
     * @param markdown Markdown 源文本
     * @return 渲染后的 Spanned 对象
     */
    fun render(context: Context, markdown: String): Spanned {
        return getInstance(context).toMarkdown(markdown)
    }
    
    /**
     * 渲染 Markdown 并设置到 TextView
     * 
     * @param textView 目标 TextView
     * @param markdown Markdown 源文本
     */
    fun renderTo(textView: TextView, markdown: String) {
        getInstance(textView.context).setMarkdown(textView, markdown)
    }
    
    /**
     * 异步渲染 Markdown（适用于大文本）
     * 
     * @param context 上下文
     * @param markdown Markdown 源文本
     * @param onRendered 渲染完成回调
     */
    fun renderAsync(context: Context, markdown: String, onRendered: (Spanned) -> Unit) {
        Thread {
            val result = render(context, markdown)
            onRendered(result)
        }.start()
    }
    
    /**
     * 检查文本是否包含 Markdown 语法
     */
    fun containsMarkdown(text: String): Boolean {
        val markdownPatterns = listOf(
            Regex("""^\s*#{1,6}\s""", RegexOption.MULTILINE),  // 标题
            Regex("""\*\*[^*]+\*\*"""),  // 粗体
            Regex("""\*[^*]+\*"""),  // 斜体
            Regex("""~~[^~]+~~"""),  // 删除线
            Regex("""`[^`]+`"""),  // 行内代码
            Regex("""```[\s\S]*?```"""),  // 代码块
            Regex("""^\s*[-*+]\s""", RegexOption.MULTILINE),  // 无序列表
            Regex("""^\s*\d+\.\s""", RegexOption.MULTILINE),  // 有序列表
            Regex("""\[([^\]]+)\]\(([^)]+)\)"""),  // 链接
            Regex("""!\[([^\]]*)\]\(([^)]+)\)"""),  // 图片
            Regex("""^\s*>\s""", RegexOption.MULTILINE),  // 引用
            Regex("""\|[^|]+\|""")  // 表格
        )
        
        return markdownPatterns.any { it.containsMatchIn(text) }
    }
    
    /**
     * 清除缓存的 Markwon 实例
     */
    fun clearCache() {
        markwonRef?.clear()
        markwonRef = null
        lastContextHash = 0
    }
}


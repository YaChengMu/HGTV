// SPDX-License-Identifier: GPL-3.0-only
package com.hgtv.star

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.widget.*
import android.text.TextUtils
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.hgtv.star.core.*
import java.util.concurrent.Executors
import com.hgtv.star.tv.ButtonSize
import com.hgtv.star.tv.ButtonTone
import com.hgtv.star.player.DanmakuSlider
import com.hgtv.star.tv.TvButton
import com.hgtv.star.tv.TvStyle
import com.hgtv.star.tv.TvTheme
import com.hgtv.star.list.showCatalog

// 本文件是从 MainActivity.kt 拆出来的「设置页：外观主题、播放偏好、账号与关于」这一块。
// 这些成员仍然属于 MainActivity，只是以扩展成员（MainActivity.xxx）的形式放在这里，
// 逻辑与拆之前逐行一致；改这里等于改 Activity 本体。

internal fun MainActivity.settings(parent: LinearLayout) {
    // 页面名「设置」已经进了顶部导航栏，这里不再重复一行标题，只留一句说明。
    parent.addView(text("偏好自动保存，下一部剧继续使用。",14f,TvStyle.muted).apply { setPadding(0,dp(10),0,dp(20)) })
    val groups=row().apply { gravity=Gravity.TOP; clipChildren=false; clipToPadding=false }
    val playback=column().apply { clipChildren=false; clipToPadding=false }
    val device=column().apply { clipChildren=false; clipToPadding=false }
    val groupBand=dp(TvStyle.SOFT_BAND_LARGE)
    /** 一个设置分组：大号凸起面板，内容四周留出柔光带。 */
    fun groupPanel(group: LinearLayout) {
        group.setPadding(dp(24)+groupBand,dp(20)+groupBand,dp(24)+groupBand,dp(20)+groupBand)
        group.background=softPanel(TvStyle.surfaceHigh,TvStyle.RADIUS_DIALOG,band=TvStyle.SOFT_BAND_LARGE,depth=1.05f)
    }
    listOf(playback,device).forEachIndexed { i,group ->
        groupPanel(group)
        groups.addView(group,lp(0,-2).apply { weight=1f; if(i==0) rightMargin=dp(TvStyle.GAP_GROUP) })
    }
    parent.addView(groups)
    fun heading(group: LinearLayout,title: String,subtitle: String) {
        group.addView(text(title,18f,TvStyle.text).apply { setTypeface(null,Typeface.BOLD) },lp(-2,-2))
        group.addView(text(subtitle,14f,TvStyle.muted).apply { setPadding(0,dp(TvStyle.GAP_TIGHT),0,dp(TvStyle.GAP_BLOCK)) })
    }
    /**
     * 设置项就是整行左对齐的常规按钮：高度、圆角、焦点反馈与全站按钮完全一致，只是文字靠左。
     * 行距由 GAP_INLINE 统一给出，不再靠每一项自己写 bottomMargin。
     */
    fun setting(group: LinearLayout,label: String,action: ()->Unit): TvButton {
        val view=TvButton(this,label,ButtonSize.NORMAL,ButtonTone.SECONDARY,alignStart=true,action=action)
        group.addView(view,lp(-1,-2).apply { if(group.childCount>1) topMargin=dp(TvStyle.GAP_INLINE) })
        return view
    }
    /**
     * 把「弹幕显示」的子项（弹幕速度 / 弹幕大小 / 弹幕透明度）右移一档：
     * 这三项只对弹幕生效，缩进之后一眼能看出它们归属上面那一行。
     */
    fun indentSetting(view: View) {
        val params=view.layoutParams as? LinearLayout.LayoutParams ?: return
        params.leftMargin=dp(TvStyle.GAP_BLOCK)
        view.layoutParams=params
    }
    /**
     * 滑动条设置项：左侧项目名与上面几行同宽，右侧是可滑的控件。
     *
     * 返回（滑块，整行容器）：调用方要能按需把**整行**收起来（弹幕隐藏时），
     * 只藏滑块会在左边留一条空标签。
     *
     * 与播放设置面板里那两条共用同一个 [DanmakuSlider]，改的是同一份本机偏好，
     * 因此设置页调好之后进播放界面立刻就是新的字号 / 透明度。
     */
    fun sliderSetting(group: LinearLayout,label: String,min: Float,max: Float,step: Float,value: Float,onValue: (Float)->Unit): Pair<DanmakuSlider,View> {
        val line=row().apply { clipChildren=false; clipToPadding=false }
        // 标签字号与上面那些整行设置项一致（[TvStyle.BUTTON_TEXT_NORMAL]），不再单独放大一档。
        line.addView(
            text(label,TvStyle.BUTTON_TEXT_NORMAL,TvStyle.text).apply {
                gravity=Gravity.CENTER_VERTICAL; maxLines=1; ellipsize=TextUtils.TruncateAt.END
            },
            lp(dp(110),-2)
        )
        val slider=DanmakuSlider(this,label,min,max,step,value,onValue)
        line.addView(slider,lp(0,-2).apply { weight=1f })
        group.addView(line,lp(-1,-2).apply { if(group.childCount>1) topMargin=dp(TvStyle.GAP_INLINE) })
        return slider to line
    }
    heading(playback,"播放偏好","所有剧集共用，播放中也可调整")
    heading(device,"片单与设备","保管记录，保持应用更新")
    // 这些设置项都是全站统一的常规按钮，声明成 TvButton 才能参与下面三列网格的焦点串联。
    lateinit var qualityButton: TvButton
    qualityButton=setting(playback,"默认清晰度  ·  ${library.maxQuality}P") { showQualityPicker(qualityButton,false) }
    lateinit var defaultSpeed: TvButton
    defaultSpeed=setting(playback,"默认倍速  ·  ${PlaybackSpeed.label(library.playbackSpeed)}") { showSpeedPicker(defaultSpeed,false) }
    lateinit var frame: TvButton
    frame=setting(playback,"画面模式  ·  ${library.frameMode.label}") { showFramePicker(frame,false) }
    lateinit var autoNextButton: TvButton
    autoNextButton=setting(playback,"自动连播  ·  ${if(library.autoNext) "开启" else "关闭"}") {
        library.autoNext=!library.autoNext
        autoNextButton.text="自动连播  ·  ${if(library.autoNext) "开启" else "关闭"}"
    }
    // 弹幕显示方式，以及它下面的三项（移动速度 / 字号缩放 / 不透明度）。
    // 顺序是「自动连播 → 弹幕显示 → 弹幕速度 → 弹幕大小 → 弹幕透明度」：后三项都是弹幕显示
    // 这一项的子项，因此紧跟在它下面、并按 [indentSetting] 缩进一档，和上面的平级设置项区分开。
    lateinit var danmakuSetting: TvButton
    // 回调里要用的 syncDanmakuStyleRows 在下面才定义（它要引用三行子项），
    // 因此这里先建好这一行，等子项齐了再补上回调。
    danmakuSetting=setting(playback,danmakuLabel("弹幕显示  ·  ")) {}
    // 弹幕移动速度：三档（慢速 / 标准 / 快速）。它与下面两条外观项一样是「弹幕显示」的子项
    //（隐藏档下一起收起），因此也按 [indentSetting] 缩进一档，排在字号前面。
    lateinit var danmakuSpeedSetting: TvButton
    danmakuSpeedSetting=setting(playback,"弹幕速度  ·  ${library.danmakuSpeed.label}") {
        showDanmakuSpeedPicker(danmakuSpeedSetting,false)
    }
    indentSetting(danmakuSpeedSetting)
    // 两条滑块连行一起收起来：`DanmakuSlider` 自己只占右半边，行还在的话左边会留一条空标签。
    // 用 GONE 而不是 INVISIBLE——收起来之后这些行不该再占位置，焦点链也一并跳过它们。
    lateinit var danmakuScaleLine: View
    lateinit var danmakuScaleRow: DanmakuSlider
    val scaleLine=sliderSetting(playback,"弹幕大小",DanmakuStyle.SCALE_MIN,DanmakuStyle.SCALE_MAX,DanmakuStyle.SCALE_STEP,library.danmakuScale) { value ->
        library.danmakuScale=value; danmakuView?.textScale=value
    }
    danmakuScaleRow=scaleLine.first
    danmakuScaleLine=scaleLine.second
    lateinit var danmakuAlphaLine: View
    lateinit var danmakuAlphaRow: DanmakuSlider
    val alphaLine=sliderSetting(playback,"弹幕透明度",DanmakuStyle.ALPHA_MIN,DanmakuStyle.ALPHA_MAX,DanmakuStyle.ALPHA_STEP,library.danmakuAlpha) { value ->
        library.danmakuAlpha=value; danmakuView?.textAlpha=value
    }
    danmakuAlphaRow=alphaLine.first
    danmakuAlphaLine=alphaLine.second
    indentSetting(danmakuScaleLine); indentSetting(danmakuAlphaLine)
    /**
     * 隐藏弹幕时收起这三项（速度 / 字号 / 透明度），选回可见档时再放出来。
     *
     * 判定只有一份（[DanmakuMode.showsStyle]），设置页与播放设置面板共用同一条规则。
     *
     * 这里要同时做三件事，缺一件都会出问题：
     * 1. 三行连行一起 `GONE`（只藏滑块会在左边留一条空标签，速度那一行整行也不该再占位）；
     * 2. 把「弹幕显示 → 弹幕速度 → 弹幕大小 → 弹幕透明度」这条上下路径按当前档位重新指一遍——
     *    隐藏时「弹幕显示」的下键直接去主题网格，可见时再指回那三项，
     *    否则切回可见档之后这几行收不到上下键（链是单向写死的，不会自己长回来）；
     * 3. 主题网格首行的上行出口跟着变：隐藏时回「弹幕显示」，可见时回「弹幕透明度」。
     * 焦点若正停在被收起的那一行上，退回「弹幕显示」。
     */
    fun syncDanmakuStyleRows() {
        val shown=library.danmakuMode.showsStyle
        listOf<View>(danmakuSpeedSetting,danmakuScaleLine,danmakuAlphaLine).forEach { line ->
            line.visibility=if(shown) View.VISIBLE else View.GONE
        }
        val themeHead=themeRows[TvTheme.entries.first()] ?: danmakuAlphaRow
        if(shown) {
            danmakuSetting.nextFocusDownId=danmakuSpeedSetting.id
            danmakuSpeedSetting.nextFocusUpId=danmakuSetting.id
            danmakuSpeedSetting.nextFocusDownId=danmakuScaleRow.id
            danmakuScaleRow.nextFocusUpId=danmakuSpeedSetting.id
            danmakuScaleRow.nextFocusDownId=danmakuAlphaRow.id
            danmakuAlphaRow.nextFocusUpId=danmakuScaleRow.id
            danmakuAlphaRow.nextFocusDownId=themeHead.id
        } else {
            danmakuSetting.nextFocusDownId=themeHead.id
            danmakuSpeedSetting.nextFocusUpId=danmakuSetting.id
            danmakuSpeedSetting.nextFocusDownId=danmakuSetting.id
            danmakuScaleRow.nextFocusUpId=danmakuSetting.id
            danmakuScaleRow.nextFocusDownId=danmakuSetting.id
            danmakuAlphaRow.nextFocusUpId=danmakuSetting.id
            danmakuAlphaRow.nextFocusDownId=danmakuSetting.id
        }
        // 主题网格首行左半边（前 THEME_GRID_COLUMNS/2 列）的上行出口。
        val upTo=if(shown) danmakuAlphaRow.id else danmakuSetting.id
        TvTheme.entries.take(THEME_GRID_COLUMNS/2).mapNotNull { themeRows[it] }.forEach { it.nextFocusUpId=upTo }
        if(!shown && (danmakuSpeedSetting.hasFocus() || danmakuScaleLine.hasFocus() || danmakuAlphaRow.hasFocus())) danmakuSetting.requestFocus()
    }
    danmakuSetting.setOnClickListener { showDanmakuPicker(danmakuSetting,false) { syncDanmakuStyleRows() } }
    lateinit var backup: TvButton
    backup=setting(device,"手机备份与恢复") {
        runCatching { LibraryBackup.encode(library.snapshot()) }.onSuccess { encoded ->
            tvTools.libraryTransfer(encoded,backup) { data,restoreSettings ->
                favoriteMonitor.stop(); library.restore(data,restoreSettings)
                tabState.clear(); tabFocus.clear(); catalogFocus=""; page=1
                showCatalog(); favoriteMonitor.check()
                showHint("记录已合并恢复")
            }
        }.onFailure { tvTools.info("无法生成备份","本机记录暂时无法导出，请重试。",backup) }
    }
    lateinit var hidden: TvButton
    hidden=setting(device,"恢复隐藏的剧集  ·  ${library.hidden().size} 部") {
        library.unhideAll(); hidden.text="恢复隐藏的剧集  ·  0 部"
    }
    // 可选：填上 App 抓到的口令后，搜索直接使用 App 搜索接口；留空时退回官网搜索。
    lateinit var session: TvButton
    fun sessionLabel()="搜索登录态  ·  ${if(library.searchToken.isBlank()) "未设置" else "已设置"}"
    session=setting(device,sessionLabel()) {
        tvTools.searchSession(session,library.searchToken,{ token ->
            library.searchToken=token; applySessionToken(); session.text=sessionLabel()
        },{
            library.searchToken=""; applySessionToken(); session.text=sessionLabel()
        })
    }
    // 「版本与更新」：打开更新弹窗，可以手动检查、下载并交给系统安装。
    // 有新版本时进入应用会主动提示一次；选过「暂不更新」后不再主动提示，
    // 直到装上任何新版本（见 AppUpdater.declined）。
    lateinit var versionRow: TvButton
    versionRow=setting(device,"版本与更新  ·  ${BuildConfig.VERSION_NAME}") {
        appUpdater.show(versionRow)
    }
    lateinit var license: TvButton
    license=setting(device,"开源许可") {
        tvTools.info("开源许可",licenseText(),license)
    }
    // —— 主题外观：整宽分组 + 一行 THEME_GRID_COLUMNS 个芯片 ——
    // 14 套材质排成 4 行，比「一行一个主题」省下三分之二的高度；
    // 芯片就是全站统一的按钮（紧凑档），选中态是「按进去」的凹槽，凹下去的那一枚就是当前主题。
    val appearance=column().apply { clipChildren=false; clipToPadding=false }
    groupPanel(appearance)
    parent.addView(appearance,lp(-1,-2).apply { topMargin=dp(TvStyle.GAP_GROUP) })
    heading(appearance,"主题外观","${TvTheme.entries.size} 套新拟态材质，选中即生效")
    val themeGrid=column().apply { clipChildren=false; clipToPadding=false }
    appearance.addView(themeGrid,lp(-1,-2))
    themeRows.clear()
    TvTheme.entries.chunked(THEME_GRID_COLUMNS).forEachIndexed { rowIndex, line ->
        val row=row().apply { if(rowIndex>0) setPadding(0,dp(TvStyle.GAP_INLINE),0,0) }
        line.forEachIndexed { index, option ->
            val chip=TvButton(this,option.label,ButtonSize.COMPACT,ButtonTone.SECONDARY,action={ switchTheme(option) })
            chip.setSelectedState(option==TvStyle.theme)
            chip.contentDescription=option.label+"，"+option.scene+if(option==TvStyle.theme) "，当前主题" else ""
            row.addView(chip,lp(0,-2).apply { weight=1f; if(index>0) leftMargin=dp(TvStyle.GAP_INLINE) })
            themeRows[option]=chip
        }
        // 最后一行不满时用空位占住列宽，芯片不会被拉成半屏宽。
        repeat(THEME_GRID_COLUMNS-line.size) { row.addView(Space(this),lp(0,1).apply { weight=1f }) }
        themeGrid.addView(row,lp(-1,-2))
    }
    appearance.addView(
        text("当前：${TvStyle.theme.label} · ${TvStyle.theme.scene}",13f,TvStyle.textDim)
            .apply { setPadding(0,dp(TvStyle.GAP_BLOCK),0,0) },
        lp(-1,-2)
    )
    // —— 焦点串联 ——
    // 上面两列：列内上下、列间左右按行号对齐；行数不足时停在原地，焦点不会跑到框外。
    // 播放偏好那一列**始终**把两条外观项算进去：它们只是隐藏档下不可见，
    // 但「弹幕显示 → 弹幕大小 → 弹幕透明度」这条上下路径必须先在链上，
    // 否则在弹窗里切回可见档之后这两行还是选不中（早先按档位裁剪列内容的写法就栽在这里）。
    // 隐藏档下由 [syncDanmakuStyleRows] 把「弹幕显示」的下行出口直接指到主题网格。
    val playbackItems=mutableListOf<View>(qualityButton,defaultSpeed,frame,autoNextButton,
        danmakuSetting,danmakuSpeedSetting,danmakuScaleRow,danmakuAlphaRow)
    val columns=listOf(playbackItems,listOf<View>(backup,hidden,session,versionRow,license))
    columns.forEach { items -> items.forEachIndexed { index,view ->
        view.nextFocusUpId=items.getOrNull(index-1)?.id ?: (navEntry()?.id ?: View.NO_ID)
        view.nextFocusDownId=items.getOrNull(index+1)?.id ?: view.id
    } }
    columns.forEachIndexed { column,items -> items.forEachIndexed { index,view ->
        view.nextFocusLeftId=(columns.getOrNull(column-1)?.getOrNull(index) ?: view).id
        view.nextFocusRightId=(columns.getOrNull(column+1)?.getOrNull(index) ?: view).id
    } }
    // 主题网格：行内左右相连、行间上下相连，两端停住；第一行往上回到上面两列的最后一行
    // （左半边回到「播放偏好」的末项，右半边回到「片单与设备」的末项）。
    val themeLines=TvTheme.entries.mapNotNull { themeRows[it] }.chunked(THEME_GRID_COLUMNS)
    themeLines.forEachIndexed { rowIndex, line ->
        line.forEachIndexed { index, chip ->
            chip.nextFocusLeftId=line.getOrNull(index-1)?.id ?: chip.id
            chip.nextFocusRightId=line.getOrNull(index+1)?.id ?: chip.id
            // 第一行的上行出口交给 [syncDanmakuStyleRows] 按当前弹幕档位指过去
            // （左半边到「弹幕显示」或「弹幕透明度」，右半边到「开源许可」）。
            chip.nextFocusUpId=themeLines.getOrNull(rowIndex-1)?.getOrNull(index)?.id
                ?: if(index<THEME_GRID_COLUMNS/2) danmakuSetting.id else license.id
            chip.nextFocusDownId=themeLines.getOrNull(rowIndex+1)?.getOrNull(index)?.id ?: chip.id
        }
    }
    // 左半边的下行出口由 syncDanmakuStyleRows 按当前弹幕档位指过去；右半边固定回「开源许可」。
    license.nextFocusDownId=(themeRows[TvTheme.entries[THEME_GRID_COLUMNS/2]] ?: license).id
    syncDanmakuStyleRows()
    navIcons().forEach { it.nextFocusDownId=qualityButton.id }
    parent.addView(text("红果TV  /  ${BuildConfig.VERSION_NAME}    ·    收藏与进度保存在本机",12f,muted).apply { setPadding(dp(12),dp(20),0,dp(10)) })
    navEntry()?.requestFocus()
}
/**
 * 页面内提示（替代 Toast）。
 *
 * 系统 Toast 是独立于界面的一层浮窗，样式与字号不受本应用控制，电视上还会盖在画面正中；
 * 需要给一句「刚刚发生了什么」时改用它——页头下面一行淡字，不参与焦点、几秒后自己消失。
 */
internal fun MainActivity.showHint(message: String) {
    catalogHint=message
    catalogHintView?.let { view -> view.text=message; view.visibility=View.VISIBLE }
    main.removeCallbacks(hideHint)
    main.postDelayed(hideHint,4000)
}
/**
 * 「开源许可」弹窗正文。
 *
 * 信息按当前仓库写：派生来源、源码仓库、GPL-3.0-only 的许可范围、第三方来源
 * （对照 `NOTICE.md`），以及本机记录的边界。发布地址指向本仓库的 Releases，
 * 不再引用已经不存在的公开镜像仓库。
 *
 * **派生来源这一条与 `NOTICE.md` 第 1 节一一对应**：fork 关系、GPL-3.0 派生、
 * 自 2026-10-06 起在上游基础上修改、上游著作权归其作者。改 `NOTICE.md` 时同步改这里。
 */
internal fun MainActivity.licenseText(): String = listOf(
    "红果TV ${BuildConfig.VERSION_NAME} · GPL-3.0-only",
    "",
    "源码与许可证：github.com/pan8664716/HGTV",
    "  许可证全文见 LICENSE，第三方声明见 NOTICE.md",
    "",
    "派生自 N3urda/hongguoTV（GPL-3.0），自 2026-10-06 起在上游基础上修改；",
    "  上游著作权归其作者，逐项改动见 NOTICE.md 与 docs/CHANGELOG.md。",
    "",
    "内容协议与加密处理移植自 drpys（commit 22261ad，GPL-3.0）。",
    "",
    "第三方组件（完整清单见 NOTICE.md）：",
    "  Kotlin / AndroidX Media3 / OkHttp / ZXing：Apache-2.0",
    "  Bouncy Castle / jsoup / Microsoft Fluent Emoji：MIT",
    "  desugar_jdk_libs：GPL-2.0 with Classpath Exception",
    "",
    "本机记录（收藏 / 观看进度 / 设置）只保存在这台设备上，不上传。"
).joinToString("\n")
/**
 * 搜索：**不分内容类型**（顶部那两个「漫剧 / 短剧」开关已经去掉）。
 * 漫剧与短剧 / 真人剧的搜索结果合并去重后一起显示，由 [ContentRepository.search] 负责。
 */
/**
 * 搜索页的搜索控件：**整条都在导航栏里**（搜索框 + 搜索 / 历史）。
 *
 * 用户从别的页面按「搜索」图标进来就是要打字，输入框再占页面第一条会白花一屏的高度；
 * 放进导航栏之后页面第一行就是提示词 / 热搜词，输入框与它上下相邻，方向键一步就到。
 *
 * 输入框吃掉导航栏中间的弹性空间（`weight=1`），因此它总是这一行里最宽的一件；
 * 右边两个按钮按内容宽度排，圆形入口跟在最后。
 */

package com.rooftop.healthlog.ui.components

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 一次性 UI 反馈消息（Snackbar） */
data class FeedbackMessage(
    val text: String,
    /** 可选操作标签 —— 用于撤销删除等，点击后触发 onAction */
    val actionLabel: String? = null,
    /** 触发 action 时的回调；需要注意：在另一个协程中调用，不要直接捕获 Composable 引用 */
    val onAction: (() -> Unit)? = null,
    /** 是否使用较长的展示时长 */
    val long: Boolean = false
)

/** 应用范围的 Snackbar 反馈总线，任何层都可 emit 消息 */
object UiFeedbackBus {
    private val _messages = MutableSharedFlow<FeedbackMessage>(
        replay = 0, extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<FeedbackMessage> = _messages.asSharedFlow()

    fun show(text: String, long: Boolean = false) {
        _messages.tryEmit(FeedbackMessage(text = text, long = long))
    }

    fun showAction(text: String, action: String, onAction: () -> Unit) {
        _messages.tryEmit(FeedbackMessage(text = text, actionLabel = action, onAction = onAction))
    }
}

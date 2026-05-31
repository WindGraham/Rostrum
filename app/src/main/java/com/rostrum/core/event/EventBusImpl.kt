package com.rostrum.core.event

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * 事件总线实现
 * 
 * 支持发布-订阅模式的事件分发，支持优先级和请求-响应模式
 */
class EventBusImpl : EventBus {
    
    companion object {
        private const val TAG = "EventBusImpl"
        
        @Volatile
        private var instance: EventBusImpl? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(): EventBusImpl {
            return instance ?: synchronized(this) {
                instance ?: EventBusImpl().also { instance = it }
            }
        }
    }
    
    // 订阅者列表，按事件类型分组
    private val subscribers = ConcurrentHashMap<KClass<*>, CopyOnWriteArrayList<PrioritizedSubscriber<*>>>()
    
    // 请求-响应等待队列
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<Event?>>()
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * 带优先级的订阅者包装
     */
    private data class PrioritizedSubscriber<T : Event>(
        val subscriber: EventSubscriber<T>,
        val priority: Int
    )
    
    /**
     * 发布事件
     */
    override fun <T : Event> publish(event: T) {
        Log.d(TAG, "Publishing event: ${event::class.simpleName}, id=${event.id}")
        
        val eventClass = event::class
        val subscriberList = subscribers[eventClass]
        
        if (subscriberList.isNullOrEmpty()) {
            Log.d(TAG, "No subscribers for event: ${eventClass.simpleName}")
            return
        }
        
        // 按优先级排序（优先级高的先执行）
        val sortedSubscribers = subscriberList.sortedByDescending { it.priority }
        
        // 异步分发事件
        scope.launch {
            sortedSubscribers.forEach { prioritizedSubscriber ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val subscriber = prioritizedSubscriber.subscriber as EventSubscriber<T>
                    subscriber.onEvent(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error dispatching event to subscriber", e)
                }
            }
        }
        
        // 检查是否有等待该事件类型的请求
        checkPendingRequests(event)
    }
    
    /**
     * 同步发布事件（阻塞当前线程直到所有订阅者处理完成）
     */
    suspend fun publishSync(event: Event) {
        Log.d(TAG, "Publishing event (sync): ${event::class.simpleName}, id=${event.id}")
        
        val eventClass = event::class
        val subscriberList = subscribers[eventClass]
        
        if (subscriberList.isNullOrEmpty()) {
            Log.d(TAG, "No subscribers for event: ${eventClass.simpleName}")
            return
        }
        
        // 按优先级排序
        val sortedSubscribers = subscriberList.sortedByDescending { it.priority }
        
        // 同步分发事件
        sortedSubscribers.forEach { prioritizedSubscriber ->
            try {
                @Suppress("UNCHECKED_CAST")
                val subscriber = prioritizedSubscriber.subscriber as EventSubscriber<Event>
                subscriber.onEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching event to subscriber", e)
            }
        }
        
        // 检查是否有等待该事件类型的请求
        checkPendingRequests(event)
    }
    
    /**
     * 订阅事件
     */
    override fun <T : Event> subscribe(
        eventType: KClass<T>,
        subscriber: EventSubscriber<T>,
        priority: Int
    ) {
        Log.d(TAG, "Subscribing to event: ${eventType.simpleName}, priority=$priority")
        
        val subscriberList = subscribers.getOrPut(eventType) { CopyOnWriteArrayList() }
        val prioritizedSubscriber = PrioritizedSubscriber(subscriber, priority)
        
        // 检查是否已订阅
        val exists = subscriberList.any { it.subscriber === subscriber }
        if (!exists) {
            subscriberList.add(prioritizedSubscriber)
            Log.d(TAG, "Subscribed to ${eventType.simpleName}, total subscribers: ${subscriberList.size}")
        } else {
            Log.d(TAG, "Subscriber already exists for ${eventType.simpleName}")
        }
    }
    
    /**
     * 取消订阅
     */
    override fun <T : Event> unsubscribe(
        eventType: KClass<T>,
        subscriber: EventSubscriber<T>
    ) {
        Log.d(TAG, "Unsubscribing from event: ${eventType.simpleName}")
        
        val subscriberList = subscribers[eventType] ?: return
        subscriberList.removeIf { it.subscriber === subscriber }
        
        Log.d(TAG, "Unsubscribed from ${eventType.simpleName}, remaining subscribers: ${subscriberList.size}")
    }
    
    /**
     * 请求-响应模式
     * 
     * 发送请求事件，等待响应事件
     */
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Event, R : Event> request(
        request: T,
        timeout: Duration
    ): R? {
        Log.d(TAG, "Sending request: ${request::class.simpleName}, id=${request.id}, timeout=${timeout}")
        
        val deferred = CompletableDeferred<Event?>()
        pendingRequests[request.id] = deferred
        
        try {
            // 发布请求事件
            publish(request)
            
            // 等待响应（带超时）
            return withTimeoutOrNull(timeout.inWholeMilliseconds) {
                deferred.await()
            } as? R
        } finally {
            pendingRequests.remove(request.id)
        }
    }
    
    /**
     * 响应请求
     * 
     * 用于请求-响应模式，响应一个请求事件
     */
    fun respond(requestId: String, response: Event) {
        Log.d(TAG, "Responding to request: $requestId")
        
        pendingRequests[requestId]?.complete(response)
    }
    
    /**
     * 检查等待的请求
     */
    private fun checkPendingRequests(event: Event) {
        // 检查是否有等待该事件类型的请求
        // 响应事件通常包含关联的请求ID
        // 这里简单实现：如果事件ID匹配等待的请求ID，则完成该请求
        pendingRequests[event.id]?.complete(event)
    }
    
    /**
     * 获取某事件类型的订阅者数量
     */
    fun getSubscriberCount(eventType: KClass<*>): Int {
        return subscribers[eventType]?.size ?: 0
    }
    
    /**
     * 获取所有事件类型
     */
    fun getEventTypes(): Set<KClass<*>> {
        return subscribers.keys.toSet()
    }
    
    /**
     * 清除所有订阅者
     */
    fun clear() {
        Log.d(TAG, "Clearing all subscribers")
        subscribers.clear()
        pendingRequests.clear()
    }
    
    /**
     * 关闭事件总线
     */
    fun shutdown() {
        Log.d(TAG, "Shutting down EventBus")
        clear()
        scope.cancel()
    }
}

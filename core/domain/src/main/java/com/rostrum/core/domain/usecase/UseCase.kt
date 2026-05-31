package com.rostrum.core.domain.usecase

/**
 * UseCase 基础接口
 * 
 * 定义用例的标准契约，遵循 Clean Architecture 原则。
 * 
 * @param P 参数类型
 * @param R 返回类型
 */
interface UseCase<in P, out R> {
    /**
     * 执行用例
     * @param params 输入参数
     * @return 执行结果
     */
    suspend operator fun invoke(params: P): R
}

/**
 * 无参数 UseCase
 */
interface NoParamsUseCase<out R> {
    suspend operator fun invoke(): R
}

/**
 * Flow UseCase - 返回 Flow 的用例
 */
interface FlowUseCase<in P, out R> {
    operator fun invoke(params: P): kotlinx.coroutines.flow.Flow<R>
}

/**
 * Result 包装类
 * 统一的结果返回类型
 */
sealed class UseCaseResult<out T> {
    data class Success<T>(val data: T) : UseCaseResult<T>()
    data class Error(val exception: Throwable) : UseCaseResult<Nothing>()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    
    fun getOrNull(): T? = (this as? Success)?.data
    fun exceptionOrNull(): Throwable? = (this as? Error)?.exception
    
    inline fun <R> map(transform: (T) -> R): UseCaseResult<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> this
        }
    }
    
    inline fun onSuccess(action: (T) -> Unit): UseCaseResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (Throwable) -> Unit): UseCaseResult<T> {
        if (this is Error) action(exception)
        return this
    }
}

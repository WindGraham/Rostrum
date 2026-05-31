package com.rostrum.domain.usecase

import com.rostrum.core.domain.usecase.UseCaseResult
import org.junit.Assert.*
import org.junit.Test

/**
 * UseCaseResult 单元测试
 */
class UseCaseResultTest {
    
    @Test
    fun `Success result isSuccess returns true`() {
        val result = UseCaseResult.Success("data")
        
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
    }
    
    @Test
    fun `Error result isError returns true`() {
        val result = UseCaseResult.Error(RuntimeException("test error"))
        
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
    }
    
    @Test
    fun `Success getOrNull returns data`() {
        val result = UseCaseResult.Success("test data")
        
        assertEquals("test data", result.getOrNull())
    }
    
    @Test
    fun `Error getOrNull returns null`() {
        val result = UseCaseResult.Error(RuntimeException("error"))
        
        assertNull(result.getOrNull())
    }
    
    @Test
    fun `Success exceptionOrNull returns null`() {
        val result = UseCaseResult.Success("data")
        
        assertNull(result.exceptionOrNull())
    }
    
    @Test
    fun `Error exceptionOrNull returns exception`() {
        val exception = RuntimeException("test error")
        val result = UseCaseResult.Error(exception)
        
        assertSame(exception, result.exceptionOrNull())
    }
    
    @Test
    fun `map transforms success data`() {
        val result = UseCaseResult.Success(10)
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped.isSuccess)
        assertEquals(20, mapped.getOrNull())
    }
    
    @Test
    fun `map preserves error`() {
        val exception = RuntimeException("error")
        val result: UseCaseResult<Int> = UseCaseResult.Error(exception)
        val mapped = result.map { it * 2 }
        
        assertTrue(mapped.isError)
        assertSame(exception, mapped.exceptionOrNull())
    }
    
    @Test
    fun `onSuccess executes action for success`() {
        var executed = false
        val result = UseCaseResult.Success("data")
        
        result.onSuccess { executed = true }
        
        assertTrue(executed)
    }
    
    @Test
    fun `onSuccess does not execute for error`() {
        var executed = false
        val result = UseCaseResult.Error(RuntimeException())
        
        result.onSuccess { executed = true }
        
        assertFalse(executed)
    }
    
    @Test
    fun `onError executes action for error`() {
        var executed = false
        val result = UseCaseResult.Error(RuntimeException())
        
        result.onError { executed = true }
        
        assertTrue(executed)
    }
    
    @Test
    fun `onError does not execute for success`() {
        var executed = false
        val result = UseCaseResult.Success("data")
        
        result.onError { executed = true }
        
        assertFalse(executed)
    }
    
    @Test
    fun `chaining onSuccess and onError works`() {
        var successCalled = false
        var errorCalled = false
        
        UseCaseResult.Success("data")
            .onSuccess { successCalled = true }
            .onError { errorCalled = true }
        
        assertTrue(successCalled)
        assertFalse(errorCalled)
        
        successCalled = false
        errorCalled = false
        
        UseCaseResult.Error(RuntimeException())
            .onSuccess { successCalled = true }
            .onError { errorCalled = true }
        
        assertFalse(successCalled)
        assertTrue(errorCalled)
    }
}

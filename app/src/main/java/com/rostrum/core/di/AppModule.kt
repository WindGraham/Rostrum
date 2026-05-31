package com.rostrum.core.di

import android.content.Context
import com.rostrum.core.config.ConfigManagerImpl
import com.rostrum.core.event.EventBusImpl
import com.rostrum.core.mcp.MCPProtocol
import com.rostrum.core.mcp.MCPProtocolManager
import com.rostrum.core.data.repository.BookmarkRepositoryImpl
import com.rostrum.core.data.repository.PluginRepositoryImpl
import com.rostrum.core.domain.plugin.repository.PluginRepository
import com.rostrum.core.domain.repository.BookmarkRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 应用模块
 * 
 * 提供应用级别的单例依赖
 * 
 * 迁移自 ServiceLocator，提供：
 * - 类型安全的依赖注入
 * - 编译时依赖验证
 * - 更好的测试支持
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供事件总线
     */
    @Provides
    @Singleton
    fun provideEventBus(): EventBusImpl {
        return EventBusImpl.getInstance()
    }
    
    /**
     * 提供配置管理器
     */
    @Provides
    @Singleton
    fun provideConfigManager(@ApplicationContext context: Context): ConfigManagerImpl {
        return ConfigManagerImpl.getInstance(context)
    }
    
    /**
     * 提供 MCP 协议管理器
     */
    @Provides
    @Singleton
    fun provideMCPProtocol(): MCPProtocol {
        return MCPProtocolManager.getInstance()
    }
    
    /**
     * 提供书签仓储
     */
    @Provides
    @Singleton
    fun provideBookmarkRepository(@ApplicationContext context: Context): BookmarkRepository {
        return BookmarkRepositoryImpl(context)
    }
    
    /**
     * 提供插件仓储
     */
    @Provides
    @Singleton
    fun providePluginRepository(@ApplicationContext context: Context): PluginRepository {
        return PluginRepositoryImpl(context)
    }
    
    /**
     * 提供 ShellManager Factory
     * 
     * 由于 ShellManager 需要 CoroutineScope（通常来自 viewModelScope），
     * 使用 Factory 模式在 ViewModel 中创建实例
     */
    @Provides
    @Singleton
    fun provideShellManagerFactory(@ApplicationContext context: Context): ShellManagerFactory {
        return ShellManagerFactory(context)
    }
}

/**
 * ShellManager 工厂类
 * 
 * 用于在 ViewModel 中创建 ShellManager 实例
 */
class ShellManagerFactory(private val context: Context) {
    fun create(scope: kotlinx.coroutines.CoroutineScope): com.rostrum.core.shell.ShellManager {
        return com.rostrum.core.shell.ShellManager(context, scope)
    }
}

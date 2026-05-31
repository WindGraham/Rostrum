package com.rostrum.core.di

import android.content.Context
import com.rostrum.terminal.LinuxServiceManager
import com.rostrum.terminal.LinuxServiceManagerImpl
import com.rostrum.terminal.PRootEnvironment
import com.rostrum.terminal.PRootEnvironmentImpl
import com.rostrum.terminal.PortAllocator
import com.rostrum.terminal.PtySessionManager
import com.rostrum.terminal.PtySessionManagerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TerminalModule {

    @Provides
    @Singleton
    fun providePRootEnvironment(@ApplicationContext context: Context): PRootEnvironment {
        return PRootEnvironmentImpl(context)
    }

    @Provides
    @Singleton
    fun providePtySessionManager(): PtySessionManager {
        return PtySessionManagerImpl()
    }

    @Provides
    @Singleton
    fun providePortAllocator(): PortAllocator {
        return PortAllocator()
    }

    @Provides
    @Singleton
    fun provideLinuxServiceManager(
        prootEnvironment: PRootEnvironment,
        ptySessionManager: PtySessionManager,
        portAllocator: PortAllocator
    ): LinuxServiceManager {
        return LinuxServiceManagerImpl(prootEnvironment, ptySessionManager, portAllocator)
    }
}

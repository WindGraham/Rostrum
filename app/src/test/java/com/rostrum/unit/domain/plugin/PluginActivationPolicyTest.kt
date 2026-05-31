package com.rostrum.unit.domain.plugin

import com.rostrum.core.domain.plugin.model.*
import com.rostrum.core.domain.plugin.policy.DefaultPluginActivationPolicy
import com.rostrum.core.domain.plugin.policy.LazyPluginActivationPolicy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 插件激活策略单元测试
 */
class PluginActivationPolicyTest {
    
    private lateinit var defaultPolicy: DefaultPluginActivationPolicy
    private lateinit var lazyPolicy: LazyPluginActivationPolicy
    
    @Before
    fun setup() {
        defaultPolicy = DefaultPluginActivationPolicy()
        lazyPolicy = LazyPluginActivationPolicy()
    }
    
    // ============ 测试数据 ============
    
    private fun createPlugin(
        id: String = "test-plugin",
        type: PluginType = PluginType.EXTERNAL,
        state: PluginState = PluginState.INACTIVE,
        enabled: Boolean = true,
        activationEvents: List<ActivationEvent> = listOf(
            ActivationEvent(ActivationEventType.ON_STARTUP)
        )
    ) = Plugin(
        id = id,
        name = "Test Plugin",
        version = "1.0.0",
        description = "A test plugin",
        type = type,
        state = state,
        enabled = enabled,
        activationEvents = activationEvents
    )
    
    // ============ DefaultPluginActivationPolicy 测试 ============
    
    @Test
    fun `shouldActivate returns true for matching startup event`() {
        val plugin = createPlugin()
        val event = ActivationEvent(ActivationEventType.ON_STARTUP)
        
        assertTrue(defaultPolicy.shouldActivate(plugin, event))
    }
    
    @Test
    fun `shouldActivate returns false for disabled plugin`() {
        val plugin = createPlugin(enabled = false)
        val event = ActivationEvent(ActivationEventType.ON_STARTUP)
        
        assertFalse(defaultPolicy.shouldActivate(plugin, event))
    }
    
    @Test
    fun `shouldActivate returns false for already active plugin`() {
        val plugin = createPlugin(state = PluginState.ACTIVE)
        val event = ActivationEvent(ActivationEventType.ON_STARTUP)
        
        assertFalse(defaultPolicy.shouldActivate(plugin, event))
    }
    
    @Test
    fun `shouldActivate returns false for non-matching event`() {
        val plugin = createPlugin(
            activationEvents = listOf(ActivationEvent(ActivationEventType.ON_COMMAND, "myCommand"))
        )
        val event = ActivationEvent(ActivationEventType.ON_STARTUP)
        
        assertFalse(defaultPolicy.shouldActivate(plugin, event))
    }
    
    @Test
    fun `shouldActivate matches ON_LANGUAGE with wildcard`() {
        val plugin = createPlugin(
            activationEvents = listOf(ActivationEvent(ActivationEventType.ON_LANGUAGE, "*"))
        )
        val event = ActivationEvent(ActivationEventType.ON_LANGUAGE, "kotlin")
        
        assertTrue(defaultPolicy.shouldActivate(plugin, event))
    }
    
    @Test
    fun `shouldActivate matches ON_LANGUAGE with exact match`() {
        val plugin = createPlugin(
            activationEvents = listOf(ActivationEvent(ActivationEventType.ON_LANGUAGE, "kotlin"))
        )
        val event = ActivationEvent(ActivationEventType.ON_LANGUAGE, "kotlin")
        
        assertTrue(defaultPolicy.shouldActivate(plugin, event))
    }
    
    @Test
    fun `canActivate returns null for enabled plugin`() {
        val plugin = createPlugin()
        assertNull(defaultPolicy.canActivate(plugin))
    }
    
    @Test
    fun `canActivate returns error for disabled plugin`() {
        val plugin = createPlugin(enabled = false)
        assertNotNull(defaultPolicy.canActivate(plugin))
    }
    
    @Test
    fun `canActivate returns error for error state plugin`() {
        val plugin = createPlugin(state = PluginState.ERROR)
        assertNotNull(defaultPolicy.canActivate(plugin))
    }
    
    @Test
    fun `getActivationPriority returns 0 for builtin plugin`() {
        val plugin = createPlugin(type = PluginType.BUILTIN)
        assertEquals(0, defaultPolicy.getActivationPriority(plugin))
    }
    
    @Test
    fun `getActivationPriority returns higher value for external plugin`() {
        val plugin = createPlugin(type = PluginType.EXTERNAL)
        assertTrue(defaultPolicy.getActivationPriority(plugin) > 0)
    }
    
    // ============ LazyPluginActivationPolicy 测试 ============
    
    @Test
    fun `lazy policy activates only builtin plugins on startup`() {
        val builtinPlugin = createPlugin(type = PluginType.BUILTIN)
        val externalPlugin = createPlugin(id = "external", type = PluginType.EXTERNAL)
        val event = ActivationEvent(ActivationEventType.ON_STARTUP)
        
        assertTrue(lazyPolicy.shouldActivate(builtinPlugin, event))
        assertFalse(lazyPolicy.shouldActivate(externalPlugin, event))
    }
    
    @Test
    fun `lazy policy activates external plugins on demand`() {
        val plugin = createPlugin(
            type = PluginType.EXTERNAL,
            activationEvents = listOf(ActivationEvent(ActivationEventType.ON_COMMAND, "test"))
        )
        val event = ActivationEvent(ActivationEventType.ON_COMMAND, "test")
        
        assertTrue(lazyPolicy.shouldActivate(plugin, event))
    }
}

package com.bank.trading.simexchange.callback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CallbackRegistry 单元测试。
 * <p>
 * 验证 Webhook 回调注册表的注册、去重、size、clear 等行为。
 * 推送行为（notifyOrderUpdate/notifyTrade）在 MatchingEngineTest 中已通过 Stub 验证，
 * 这里聚焦于注册管理逻辑。
 */
class CallbackRegistryTest {

    /** 被测回调注册表 */
    private CallbackRegistry callbackRegistry;

    @BeforeEach
    void setUp() {
        // 构造真实 CallbackRegistry，match-delay-ms=0
        callbackRegistry = new CallbackRegistry(new RestTemplate(), 0);
    }

    @Test
    @DisplayName("注册回调地址后 size 增加")
    void register_increasesSize() {
        assertEquals(0, callbackRegistry.size());

        callbackRegistry.register("http://localhost:8086/execution/callback");

        assertEquals(1, callbackRegistry.size());
    }

    @Test
    @DisplayName("重复注册同一地址会被去重")
    void register_duplicateUrl_deduplicated() {
        String url = "http://localhost:8086/execution/callback";

        callbackRegistry.register(url);
        callbackRegistry.register(url);

        assertEquals(1, callbackRegistry.size());
    }

    @Test
    @DisplayName("注册多个不同地址 size 正确累加")
    void register_multipleUrls_sizeAccumulates() {
        callbackRegistry.register("http://host1:8086/execution/callback");
        callbackRegistry.register("http://host2:8086/execution/callback");
        callbackRegistry.register("http://host3:8086/execution/callback");

        assertEquals(3, callbackRegistry.size());
    }

    @Test
    @DisplayName("clear 清空所有注册地址")
    void clear_removesAllUrls() {
        callbackRegistry.register("http://host1:8086/execution/callback");
        callbackRegistry.register("http://host2:8086/execution/callback");

        callbackRegistry.clear();

        assertEquals(0, callbackRegistry.size());
    }

    @Test
    @DisplayName("getMatchDelayMs 返回构造时传入的延迟值")
    void getMatchDelayMs_returnsConfiguredValue() {
        CallbackRegistry registry = new CallbackRegistry(new RestTemplate(), 150);
        assertEquals(150, registry.getMatchDelayMs());
    }

    @Test
    @DisplayName("notifyOrderUpdate 在无注册地址时不抛异常")
    void notifyOrderUpdate_noRegistrations_doesNotThrow() {
        // 未注册任何回调地址，推送应安全跳过
        assertDoesNotThrow(() -> callbackRegistry.notifyOrderUpdate(new Object()));
    }

    @Test
    @DisplayName("notifyTrade 在无注册地址时不抛异常")
    void notifyTrade_noRegistrations_doesNotThrow() {
        assertDoesNotThrow(() -> callbackRegistry.notifyTrade(new Object()));
    }

    @Test
    @DisplayName("notifyOrderUpdate 在回调地址不可达时不抛异常（best effort 语义）")
    void notifyOrderUpdate_unreachableUrl_doesNotThrow() {
        // 注册一个不可达的地址，推送应捕获异常不抛出
        callbackRegistry.register("http://192.0.2.1:9999/execution/callback");

        assertDoesNotThrow(() -> callbackRegistry.notifyOrderUpdate(new Object()));
    }

    @Test
    @DisplayName("notifyTrade 在回调地址不可达时不抛异常（best effort 语义）")
    void notifyTrade_unreachableUrl_doesNotThrow() {
        callbackRegistry.register("http://192.0.2.1:9999/execution/callback");

        assertDoesNotThrow(() -> callbackRegistry.notifyTrade(new Object()));
    }
}

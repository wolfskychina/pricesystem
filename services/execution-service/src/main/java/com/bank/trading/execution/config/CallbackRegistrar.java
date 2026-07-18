package com.bank.trading.execution.config;

import com.bank.trading.execution.client.ExchangeSessionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 服务启动时自动向交易所注册 Webhook 回调地址。
 * <p>
 * 模拟真实做市商系统启动时调用 CTP {@code RegisterSpi} 注册回调处理器的过程。
 * 注册失败不阻断启动，可后续通过定时任务或手动接口重试。
 */
@Component
public class CallbackRegistrar implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CallbackRegistrar.class);

    private final ExchangeSessionClient exchangeSessionClient;

    @Autowired
    public CallbackRegistrar(ExchangeSessionClient exchangeSessionClient) {
        this.exchangeSessionClient = exchangeSessionClient;
    }

    @Override
    public void run(String... args) {
        try {
            exchangeSessionClient.registerCallback();
        } catch (Exception e) {
            log.warn("Initial callback registration failed, will retry later: {}", e.getMessage());
        }
    }
}

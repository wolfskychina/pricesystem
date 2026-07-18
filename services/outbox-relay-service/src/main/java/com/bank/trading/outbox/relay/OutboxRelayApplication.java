package com.bank.trading.outbox.relay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Outbox Relay 独立进程启动类。
 * <p>
 * 本服务作为独立进程运行，职责单一：轮询 outbox 表中 status=PENDING 的消息，
 * 发送到 Kafka，并更新消息状态为 SENT 或 FAILED。
 * <p>
 * 与业务服务解耦的设计优势：
 * <ul>
 *   <li>业务服务只负责在事务内写入 outbox 表（{@code OutboxService.saveEvent}），不关心消息投递</li>
 *   <li>Relay 进程独立部署、独立扩缩容，不影响业务服务的资源</li>
 *   <li>Relay 进程崩溃后重启即可恢复，outbox 表中的 PENDING 消息不会丢失</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class OutboxRelayApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutboxRelayApplication.class, args);
    }
}

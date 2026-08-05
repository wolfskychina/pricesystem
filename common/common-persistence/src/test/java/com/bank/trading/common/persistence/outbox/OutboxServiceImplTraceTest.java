package com.bank.trading.common.persistence.outbox;

import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.common.core.idgen.IdGenerator;
import com.bank.trading.common.core.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OutboxServiceImpl} 链路追踪集成测试，验证 traceId 从 MDC 流入 outbox 表的写入逻辑。
 * <p>
 * 不使用 Mockito（JDK 25 + Byte Buddy 兼容问题），改用手写桩 OutboxMapper 记录入参。
 */
@DisplayName("OutboxServiceImpl traceId 写入测试")
class OutboxServiceImplTraceTest {

    private RecordingOutboxMapper outboxMapper;
    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        outboxMapper = new RecordingOutboxMapper();
        // 使用真实 IdGenerator（datacenterId=0, workerId=0）
        IdGenerator idGenerator = new IdGenerator(0, 0);
        outboxService = new OutboxServiceImpl(outboxMapper, idGenerator);
    }

    @AfterEach
    void clearMdc() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("saveEvent 应从 MDC 提取 traceId 写入 OutboxMessage")
    void saveEvent_shouldWriteTraceIdFromMdc() {
        // given: MDC 中有 traceId
        String expectedTraceId = "trace-from-mdc-001";
        TraceContext.setTraceId(expectedTraceId);

        TradeEvent event = new TradeEvent();
        event.setEventId("evt-001");
        event.setPartitionKey("AU2406");

        // when
        outboxService.saveEvent("trade-event", event, 0);

        // then: 验证传给 mapper.insert 的 message 携带了 traceId
        assertEquals(1, outboxMapper.recorded.size(), "应记录1次 insert");
        OutboxMessage saved = outboxMapper.recorded.get(0);
        assertEquals(expectedTraceId, saved.getTraceId(), "OutboxMessage.traceId 应等于 MDC 中的 traceId");
    }

    @Test
    @DisplayName("MDC 无 traceId 时 OutboxMessage.traceId 应为 null")
    void saveEvent_whenMdcEmpty_traceIdShouldBeNull() {
        // given: MDC 未设置 traceId
        TraceContext.clear();

        TradeEvent event = new TradeEvent();
        event.setEventId("evt-002");
        event.setPartitionKey("AU2406");

        // when
        outboxService.saveEvent("trade-event", event, 0);

        // then
        assertEquals(1, outboxMapper.recorded.size(), "应记录1次 insert");
        OutboxMessage saved = outboxMapper.recorded.get(0);
        assertNull(saved.getTraceId(), "MDC 无 traceId 时 OutboxMessage.traceId 应为 null");
    }

    /**
     * 手写桩 Mapper，记录 insert 入参供断言，避免 Mockito 的 JDK25 兼容问题。
     */
    static class RecordingOutboxMapper implements OutboxMapper {
        final List<OutboxMessage> recorded = new ArrayList<>();

        @Override
        public int insert(OutboxMessage message) {
            recorded.add(message);
            return 1;
        }

        @Override
        public List<OutboxMessage> findPending(int shardId, int limit) {
            return List.of();
        }

        @Override
        public int markSent(Long id) {
            return 0;
        }

        @Override
        public int markFailed(Long id) {
            return 0;
        }
    }
}

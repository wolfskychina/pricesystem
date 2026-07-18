package com.bank.trading.account.service;

import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.common.persistence.idempotent.IdempotentConsumer;
import com.bank.trading.common.persistence.idempotent.ProcessedEventMapper;
import com.bank.trading.account.dto.CreditInfo;
import com.bank.trading.account.entity.Customer;
import com.bank.trading.account.mapper.CustomerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AccountService 单元测试。
 * <p>
 * 采用手写 Mock 实现类（Java 25 + Mockito 不兼容）。
 * 覆盖：客户 CRUD、信用额度查询、BUY/SELL 扣减释放、客户不存在/非 ACTIVE 跳过、
 * 负额度钳制、幂等重复消费、eventId 回退到 tradeId。
 */
class AccountServiceTest {

    private AccountService accountService;
    private InMemoryCustomerMapper customerMapper;
    private InMemoryProcessedEventMapper processedEventMapper;

    @BeforeEach
    void setUp() {
        customerMapper = new InMemoryCustomerMapper();
        processedEventMapper = new InMemoryProcessedEventMapper();
        IdempotentConsumer idempotentConsumer = new IdempotentConsumer(processedEventMapper);
        accountService = new AccountService(customerMapper, idempotentConsumer);
    }

    // ==================== 客户 CRUD 测试 ====================

    @Test
    @DisplayName("创建客户 → 默认值填充（level=NORMAL, status=ACTIVE, usedCredit=0, version=0）")
    void createCustomer_normal_fillsDefaults() {
        Customer customer = new Customer();
        customer.setCustomerId("CUST001");
        customer.setName("张三");
        customer.setCreditLimit(new BigDecimal("100000.00"));

        Customer created = accountService.createCustomer(customer);

        assertNotNull(created.getId(), "应生成主键 ID");
        assertEquals("CUST001", created.getCustomerId());
        assertEquals("张三", created.getName());
        assertEquals("NORMAL", created.getLevel(), "level 默认 NORMAL");
        assertEquals("ACTIVE", created.getStatus(), "status 默认 ACTIVE");
        assertDecimalEquals(new BigDecimal("100000.00"), created.getCreditLimit());
        assertDecimalEquals(BigDecimal.ZERO, created.getUsedCredit(), "usedCredit 默认 0");
        assertEquals(0, created.getVersion(), "version 默认 0");
        assertNotNull(created.getCreatedAt(), "createdAt 应填充");
        assertNotNull(created.getUpdatedAt(), "updatedAt 应填充");
    }

    @Test
    @DisplayName("创建客户 → 指定 level/status 时保留传入值")
    void createCustomer_withLevelStatus_preservesProvidedValues() {
        Customer customer = new Customer();
        customer.setCustomerId("CUST002");
        customer.setCreditLimit(new BigDecimal("500000.00"));
        customer.setLevel("VIP");
        customer.setStatus("FROZEN");

        Customer created = accountService.createCustomer(customer);

        assertEquals("VIP", created.getLevel(), "应保留传入的 VIP");
        assertEquals("FROZEN", created.getStatus(), "应保留传入的 FROZEN");
    }

    @Test
    @DisplayName("创建客户 → customerId 缺失抛 IllegalArgumentException")
    void createCustomer_missingCustomerId_throws() {
        Customer customer = new Customer();
        customer.setCreditLimit(new BigDecimal("100000.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> accountService.createCustomer(customer));
        assertTrue(ex.getMessage().contains("customerId"));
    }

    @Test
    @DisplayName("创建客户 → customerId 空串抛 IllegalArgumentException")
    void createCustomer_emptyCustomerId_throws() {
        Customer customer = new Customer();
        customer.setCustomerId("");
        customer.setCreditLimit(new BigDecimal("100000.00"));

        assertThrows(IllegalArgumentException.class,
                () -> accountService.createCustomer(customer));
    }

    @Test
    @DisplayName("创建客户 → creditLimit 缺失抛 IllegalArgumentException")
    void createCustomer_missingCreditLimit_throws() {
        Customer customer = new Customer();
        customer.setCustomerId("CUST003");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> accountService.createCustomer(customer));
        assertTrue(ex.getMessage().contains("creditLimit"));
    }

    @Test
    @DisplayName("创建客户 → customerId 重复抛 IllegalArgumentException")
    void createCustomer_duplicateCustomerId_throws() {
        Customer c1 = new Customer();
        c1.setCustomerId("CUST_DUP");
        c1.setCreditLimit(new BigDecimal("100000.00"));
        accountService.createCustomer(c1);

        Customer c2 = new Customer();
        c2.setCustomerId("CUST_DUP");
        c2.setCreditLimit(new BigDecimal("200000.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> accountService.createCustomer(c2));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("findByCustomerId → 存在返回实体，不存在返回 null")
    void findByCustomerId_existsOrNot() {
        Customer c = buildCustomer("CUST_FIND", "李四", new BigDecimal("100000.00"));
        accountService.createCustomer(c);

        Customer found = accountService.findByCustomerId("CUST_FIND");
        assertNotNull(found);
        assertEquals("李四", found.getName());

        assertNull(accountService.findByCustomerId("NOT_EXIST"));
    }

    @Test
    @DisplayName("findAll → 返回全部客户列表")
    void findAll_returnsAll() {
        accountService.createCustomer(buildCustomer("C1", "甲", new BigDecimal("10000.00")));
        accountService.createCustomer(buildCustomer("C2", "乙", new BigDecimal("20000.00")));
        accountService.createCustomer(buildCustomer("C3", "丙", new BigDecimal("30000.00")));

        List<Customer> all = accountService.findAll();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("updateCustomer → 正常更新主数据，保留 used_credit")
    void updateCustomer_normal_preservesUsedCredit() {
        Customer original = buildCustomer("CUST_UPD", "原名", new BigDecimal("100000.00"));
        accountService.createCustomer(original);

        // 模拟一笔成交使 used_credit 增加
        accountService.onTradeEvent(buildTradeEvent("T-UPD-1", "CUST_UPD", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00")));
        Customer beforeUpdate = accountService.findByCustomerId("CUST_UPD");
        assertDecimalEquals(new BigDecimal("5000.00"), beforeUpdate.getUsedCredit());

        // 更新主数据：改 name/level/status/credit_limit
        Customer toUpdate = new Customer();
        toUpdate.setCustomerId("CUST_UPD");
        toUpdate.setName("新名");
        toUpdate.setLevel("VIP");
        toUpdate.setStatus("ACTIVE");
        toUpdate.setCreditLimit(new BigDecimal("500000.00"));

        Customer updated = accountService.updateCustomer(toUpdate);

        assertEquals("新名", updated.getName());
        assertEquals("VIP", updated.getLevel());
        assertEquals("500000.00", updated.getCreditLimit().toString());
        // 关键：used_credit 应保留为更新前的值，不被清零
        assertDecimalEquals(new BigDecimal("5000.00"), updated.getUsedCredit(),
                "updateCustomer 不应清零 used_credit");
    }

    @Test
    @DisplayName("updateCustomer → 客户不存在抛 IllegalArgumentException")
    void updateCustomer_notExists_throws() {
        Customer toUpdate = buildCustomer("NOT_EXIST", "x", new BigDecimal("1000.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> accountService.updateCustomer(toUpdate));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("updateCustomer → 未传入字段保留原值（null 字段不覆盖）")
    void updateCustomer_partialFields_keepsOriginalValues() {
        Customer original = buildCustomer("CUST_PART", "原名", new BigDecimal("100000.00"));
        original.setLevel("VIP");
        original.setStatus("ACTIVE");
        accountService.createCustomer(original);

        // 只改 name，其他字段为 null
        Customer toUpdate = new Customer();
        toUpdate.setCustomerId("CUST_PART");
        toUpdate.setName("改名");

        Customer updated = accountService.updateCustomer(toUpdate);

        assertEquals("改名", updated.getName());
        assertEquals("VIP", updated.getLevel(), "level 应保留原值");
        assertEquals("ACTIVE", updated.getStatus(), "status 应保留原值");
        assertDecimalEquals(new BigDecimal("100000.00"), updated.getCreditLimit(),
                "creditLimit 应保留原值");
    }

    @Test
    @DisplayName("deleteCustomer → 存在则删除返回 true，不存在返回 false")
    void deleteCustomer_existsOrNot() {
        accountService.createCustomer(buildCustomer("CUST_DEL", "待删", new BigDecimal("10000.00")));

        assertTrue(accountService.deleteCustomer("CUST_DEL"));
        assertNull(accountService.findByCustomerId("CUST_DEL"));
        assertFalse(accountService.deleteCustomer("CUST_DEL"), "二次删除返回 false");
    }

    // ==================== 信用额度查询测试 ====================

    @Test
    @DisplayName("getCreditInfo → 正常返回 credit_limit/used_credit/available_credit")
    void getCreditInfo_normal() {
        accountService.createCustomer(buildCustomer("CUST_CREDIT", "王五", new BigDecimal("100000.00")));
        // 占用 30000
        accountService.onTradeEvent(buildTradeEvent("T-CR-1", "CUST_CREDIT", "BUY",
                new BigDecimal("60"), new BigDecimal("500.00"), new BigDecimal("30000.00")));

        CreditInfo info = accountService.getCreditInfo("CUST_CREDIT");

        assertNotNull(info);
        assertEquals("CUST_CREDIT", info.getCustomerId());
        assertDecimalEquals(new BigDecimal("100000.00"), info.getCreditLimit());
        assertDecimalEquals(new BigDecimal("30000.00"), info.getUsedCredit());
        assertDecimalEquals(new BigDecimal("70000.00"), info.getAvailableCredit(),
                "available = credit_limit - used_credit");
    }

    @Test
    @DisplayName("getCreditInfo → 客户不存在返回 null")
    void getCreditInfo_notExists_returnsNull() {
        assertNull(accountService.getCreditInfo("NOT_EXIST"));
    }

    // ==================== 成交事件消费测试 ====================

    @Test
    @DisplayName("BUY 成交 → used_credit += amount（占用信用）")
    void onTradeEvent_buy_increasesUsedCredit() {
        accountService.createCustomer(buildCustomer("CUST_BUY", "买客", new BigDecimal("100000.00")));

        accountService.onTradeEvent(buildTradeEvent("T-BUY-1", "CUST_BUY", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00")));

        Customer after = accountService.findByCustomerId("CUST_BUY");
        assertDecimalEquals(new BigDecimal("5000.00"), after.getUsedCredit());
    }

    @Test
    @DisplayName("多笔 BUY 累加 → used_credit 累计")
    void onTradeEvent_multipleBuys_accumulates() {
        accountService.createCustomer(buildCustomer("CUST_BUY2", "买客2", new BigDecimal("100000.00")));

        accountService.onTradeEvent(buildTradeEvent("T-B1", "CUST_BUY2", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00")));
        accountService.onTradeEvent(buildTradeEvent("T-B2", "CUST_BUY2", "BUY",
                new BigDecimal("5"), new BigDecimal("520.00"), new BigDecimal("2600.00")));

        Customer after = accountService.findByCustomerId("CUST_BUY2");
        assertDecimalEquals(new BigDecimal("7600.00"), after.getUsedCredit());
    }

    @Test
    @DisplayName("SELL 成交 → used_credit -= amount（释放信用）")
    void onTradeEvent_sell_decreasesUsedCredit() {
        accountService.createCustomer(buildCustomer("CUST_SELL", "卖客", new BigDecimal("100000.00")));
        // 先 BUY 占用 5000
        accountService.onTradeEvent(buildTradeEvent("T-S1", "CUST_SELL", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00")));
        // SELL 释放 3000
        accountService.onTradeEvent(buildTradeEvent("T-S2", "CUST_SELL", "SELL",
                new BigDecimal("6"), new BigDecimal("500.00"), new BigDecimal("3000.00")));

        Customer after = accountService.findByCustomerId("CUST_SELL");
        assertDecimalEquals(new BigDecimal("2000.00"), after.getUsedCredit(),
                "5000 - 3000 = 2000");
    }

    @Test
    @DisplayName("SELL 释放超过当前 used_credit → 钳制为 0（不出现负额度）")
    void onTradeEvent_sellExceedingUsed_clampsToZero() {
        accountService.createCustomer(buildCustomer("CUST_CLAMP", "钳制客", new BigDecimal("100000.00")));
        // BUY 占用 3000
        accountService.onTradeEvent(buildTradeEvent("T-C1", "CUST_CLAMP", "BUY",
                new BigDecimal("6"), new BigDecimal("500.00"), new BigDecimal("3000.00")));
        // SELL 释放 10000（超过 3000）
        accountService.onTradeEvent(buildTradeEvent("T-C2", "CUST_CLAMP", "SELL",
                new BigDecimal("20"), new BigDecimal("500.00"), new BigDecimal("10000.00")));

        Customer after = accountService.findByCustomerId("CUST_CLAMP");
        assertDecimalEquals(BigDecimal.ZERO, after.getUsedCredit(),
                "释放超过当前已用额度时应钳制为 0，不得为负");
    }

    @Test
    @DisplayName("直接 SELL（无 BUY）→ used_credit 从 0 钳制为 0")
    void onTradeEvent_sellFromZero_staysZero() {
        accountService.createCustomer(buildCustomer("CUST_ZERO", "零客", new BigDecimal("100000.00")));

        accountService.onTradeEvent(buildTradeEvent("T-Z1", "CUST_ZERO", "SELL",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00")));

        Customer after = accountService.findByCustomerId("CUST_ZERO");
        assertDecimalEquals(BigDecimal.ZERO, after.getUsedCredit(),
                "used_credit 不应变为负数");
    }

    @Test
    @DisplayName("客户不存在 → 跳过扣减，不抛异常")
    void onTradeEvent_customerNotFound_skipsSilently() {
        TradeEvent event = buildTradeEvent("T-NF", "NOT_EXIST_CUST", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00"));

        // 不应抛异常
        assertDoesNotThrow(() -> accountService.onTradeEvent(event));

        // 客户仍不存在
        assertNull(accountService.findByCustomerId("NOT_EXIST_CUST"));
    }

    @Test
    @DisplayName("客户状态非 ACTIVE（FROZEN）→ 跳过扣减")
    void onTradeEvent_frozenCustomer_skips() {
        Customer c = buildCustomer("CUST_FROZEN", "冻结客", new BigDecimal("100000.00"));
        c.setStatus("FROZEN");
        accountService.createCustomer(c);

        accountService.onTradeEvent(buildTradeEvent("T-FZ", "CUST_FROZEN", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00")));

        Customer after = accountService.findByCustomerId("CUST_FROZEN");
        assertDecimalEquals(BigDecimal.ZERO, after.getUsedCredit(),
                "FROZEN 客户不应被扣减额度");
    }

    @Test
    @DisplayName("客户状态 CLOSED → 跳过扣减")
    void onTradeEvent_closedCustomer_skips() {
        Customer c = buildCustomer("CUST_CLOSED", "销户客", new BigDecimal("100000.00"));
        c.setStatus("CLOSED");
        accountService.createCustomer(c);

        accountService.onTradeEvent(buildTradeEvent("T-CL", "CUST_CLOSED", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00")));

        Customer after = accountService.findByCustomerId("CUST_CLOSED");
        assertDecimalEquals(BigDecimal.ZERO, after.getUsedCredit(),
                "CLOSED 客户不应被扣减额度");
    }

    @Test
    @DisplayName("amount 为 null → 用 qty × price 计算")
    void onTradeEvent_nullAmount_usesQtyTimesPrice() {
        accountService.createCustomer(buildCustomer("CUST_NULL_AMT", "无额", new BigDecimal("100000.00")));

        TradeEvent event = buildTradeEvent("T-NA", "CUST_NULL_AMT", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), null);
        accountService.onTradeEvent(event);

        Customer after = accountService.findByCustomerId("CUST_NULL_AMT");
        assertDecimalEquals(new BigDecimal("5000.00"), after.getUsedCredit(),
                "amount 缺失时按 qty × price = 10 × 500 = 5000");
    }

    // ==================== 幂等消费测试 ====================

    @Test
    @DisplayName("重复消费同一事件 → 不重复扣减额度")
    void onTradeEvent_duplicateEvent_skipsSecond() {
        accountService.createCustomer(buildCustomer("CUST_DUP_EVT", "幂等客", new BigDecimal("100000.00")));

        TradeEvent event = buildTradeEvent("T-DUP", "CUST_DUP_EVT", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00"));
        // 显式设置 eventId
        event.setEventId("EVT-DUP-001");

        accountService.onTradeEvent(event);
        accountService.onTradeEvent(event);  // 重复消费

        Customer after = accountService.findByCustomerId("CUST_DUP_EVT");
        assertDecimalEquals(new BigDecimal("5000.00"), after.getUsedCredit(),
                "重复消费不应导致额度被二次扣减");
    }

    @Test
    @DisplayName("eventId 缺失 → 回退到 tradeId 作为幂等键")
    void onTradeEvent_eventIdMissing_fallsBackToTradeId() {
        accountService.createCustomer(buildCustomer("CUST_FALLBACK", "回退客", new BigDecimal("100000.00")));

        TradeEvent event = buildTradeEvent("T-FALLBACK", "CUST_FALLBACK", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00"));
        // eventId 留空，tradeId = "T-FALLBACK"
        event.setEventId(null);

        accountService.onTradeEvent(event);
        // 再次消费：eventId 仍为空，但 tradeId 相同 → 应判定为重复
        accountService.onTradeEvent(event);

        Customer after = accountService.findByCustomerId("CUST_FALLBACK");
        assertDecimalEquals(new BigDecimal("5000.00"), after.getUsedCredit(),
                "eventId 缺失时 tradeId 应作为幂等键，重复消费不重复扣减");

        // 验证 processed_events 中已记录 tradeId
        assertTrue(processedEventMapper.processed.contains("T-FALLBACK"),
                "processed_events 应记录 tradeId 作为幂等键");
    }

    @Test
    @DisplayName("不同 tradeId → 视为不同事件，分别扣减")
    void onTradeEvent_differentTradeIds_processedSeparately() {
        accountService.createCustomer(buildCustomer("CUST_DIFF", "差异客", new BigDecimal("100000.00")));

        accountService.onTradeEvent(buildTradeEvent("T-D1", "CUST_DIFF", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00")));
        accountService.onTradeEvent(buildTradeEvent("T-D2", "CUST_DIFF", "BUY",
                new BigDecimal("5"), new BigDecimal("500.00"), new BigDecimal("2500.00")));

        Customer after = accountService.findByCustomerId("CUST_DIFF");
        assertDecimalEquals(new BigDecimal("7500.00"), after.getUsedCredit(),
                "两笔不同事件应分别扣减：5000 + 2500 = 7500");
    }

    @Test
    @DisplayName("多客户独立 → 各客户额度互不影响")
    void onTradeEvent_multipleCustomers_independent() {
        accountService.createCustomer(buildCustomer("CA", "客户A", new BigDecimal("100000.00")));
        accountService.createCustomer(buildCustomer("CB", "客户B", new BigDecimal("200000.00")));

        accountService.onTradeEvent(buildTradeEvent("T-CA", "CA", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00")));
        accountService.onTradeEvent(buildTradeEvent("T-CB", "CB", "BUY",
                new BigDecimal("20"), new BigDecimal("500.00"), new BigDecimal("10000.00")));

        Customer a = accountService.findByCustomerId("CA");
        Customer b = accountService.findByCustomerId("CB");
        assertDecimalEquals(new BigDecimal("5000.00"), a.getUsedCredit());
        assertDecimalEquals(new BigDecimal("10000.00"), b.getUsedCredit());
    }

    @Test
    @DisplayName("BUY 后 SELL 后再 BUY → 额度正确累计")
    void onTradeEvent_buySellBuy_correctAccumulation() {
        accountService.createCustomer(buildCustomer("CUST_BSB", "买卖买", new BigDecimal("100000.00")));

        // BUY 占用 5000
        accountService.onTradeEvent(buildTradeEvent("T-BSB-1", "CUST_BSB", "BUY",
                new BigDecimal("10"), new BigDecimal("500.00"), new BigDecimal("5000.00")));
        // SELL 释放 3000
        accountService.onTradeEvent(buildTradeEvent("T-BSB-2", "CUST_BSB", "SELL",
                new BigDecimal("6"), new BigDecimal("500.00"), new BigDecimal("3000.00")));
        // BUY 占用 2000
        accountService.onTradeEvent(buildTradeEvent("T-BSB-3", "CUST_BSB", "BUY",
                new BigDecimal("4"), new BigDecimal("500.00"), new BigDecimal("2000.00")));

        Customer after = accountService.findByCustomerId("CUST_BSB");
        // 5000 - 3000 + 2000 = 4000
        assertDecimalEquals(new BigDecimal("4000.00"), after.getUsedCredit());
    }

    // ==================== 辅助方法 ====================

    private Customer buildCustomer(String customerId, String name, BigDecimal creditLimit) {
        Customer c = new Customer();
        c.setCustomerId(customerId);
        c.setName(name);
        c.setCreditLimit(creditLimit);
        return c;
    }

    private TradeEvent buildTradeEvent(String tradeId, String customerId, String side,
                                       BigDecimal qty, BigDecimal price, BigDecimal amount) {
        TradeEvent event = new TradeEvent(customerId);
        event.setTradeId(tradeId);
        event.setOrderId("ORD-" + tradeId);
        event.setSymbol("AU2406");
        event.setSide(side);
        event.setQty(qty);
        event.setPrice(price);
        event.setAmount(amount);
        event.setTradeTime(System.currentTimeMillis());
        return event;
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.compareTo(actual) == 0,
                "expected: <" + expected + "> but was: <" + actual + ">");
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual, String msg) {
        assertTrue(expected.compareTo(actual) == 0, msg
                + " expected: <" + expected + "> but was: <" + actual + ">");
    }

    // ==================== Mock 实现类 ====================

    /**
     * 手写 CustomerMapper 内存实现。
     * <p>
     * update / updateUsedCredit 按 customerId 定位并替换实体；
     * updateUsedCredit 不影响 credit_limit 等主数据字段（与 SQL 语义一致）。
     */
    static class InMemoryCustomerMapper implements CustomerMapper {
        final List<Customer> customers = new ArrayList<>();
        long idSeq = 0;

        @Override
        public int insert(Customer customer) {
            customer.setId(++idSeq);
            // 防御性拷贝以模拟数据库持久化
            customers.add(customer);
            return 1;
        }

        @Override
        public Customer findByCustomerId(String customerId) {
            return customers.stream()
                    .filter(c -> customerId.equals(c.getCustomerId()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<Customer> findAll() {
            return new ArrayList<>(customers);
        }

        @Override
        public int update(Customer customer) {
            for (int i = 0; i < customers.size(); i++) {
                if (customer.getCustomerId().equals(customers.get(i).getCustomerId())) {
                    // update SQL 只改 name/level/status/credit_limit/version/updated_at
                    // 保留 used_credit（模拟 SQL 中未包含 used_credit 字段）
                    Customer existing = customers.get(i);
                    customer.setUsedCredit(existing.getUsedCredit());
                    Integer newVersion = existing.getVersion() == null ? 1 : existing.getVersion() + 1;
                    customer.setVersion(newVersion);
                    customers.set(i, customer);
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int updateUsedCredit(Customer customer) {
            for (int i = 0; i < customers.size(); i++) {
                if (customer.getCustomerId().equals(customers.get(i).getCustomerId())) {
                    Customer existing = customers.get(i);
                    // updateUsedCredit SQL 只改 used_credit/version/updated_at
                    // 保留其他字段（credit_limit/name/level/status）
                    Customer updated = new Customer();
                    updated.setId(existing.getId());
                    updated.setCustomerId(existing.getCustomerId());
                    updated.setName(existing.getName());
                    updated.setLevel(existing.getLevel());
                    updated.setStatus(existing.getStatus());
                    updated.setCreditLimit(existing.getCreditLimit());
                    updated.setUsedCredit(customer.getUsedCredit());
                    Integer newVersion = existing.getVersion() == null ? 1 : existing.getVersion() + 1;
                    updated.setVersion(newVersion);
                    updated.setCreatedAt(existing.getCreatedAt());
                    updated.setUpdatedAt(customer.getUpdatedAt());
                    customers.set(i, updated);
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int deleteByCustomerId(String customerId) {
            boolean removed = customers.removeIf(c -> customerId.equals(c.getCustomerId()));
            return removed ? 1 : 0;
        }
    }

    /**
     * 手写 ProcessedEventMapper，模拟 INSERT OR IGNORE 语义：
     * 同一 eventId 第二次插入返回 0（视为重复）。
     */
    static class InMemoryProcessedEventMapper implements ProcessedEventMapper {
        final Set<String> processed = new HashSet<>();

        @Override
        public int insert(String eventId, LocalDateTime processedAt) {
            // INSERT OR IGNORE：首次返回 1，重复返回 0
            return processed.add(eventId) ? 1 : 0;
        }

        @Override
        public int exists(String eventId) {
            return processed.contains(eventId) ? 1 : 0;
        }
    }
}

package com.bank.trading.account.service;

import com.bank.trading.common.core.enums.OrderSide;
import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.common.core.idgen.IdGenerator;
import com.bank.trading.common.persistence.idempotent.IdempotentConsumer;
import com.bank.trading.account.dto.CreditInfo;
import com.bank.trading.account.entity.Customer;
import com.bank.trading.account.mapper.CustomerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 客户账户服务，是 account-service 的核心业务类。
 * <p>
 * 承担三大职责：
 * <ol>
 *   <li><b>客户主数据 CRUD</b>：创建、查询、修改、删除客户记录。</li>
 *   <li><b>信用额度管理</b>：维护 credit_limit / used_credit，提供可用额度查询
 *       （available = credit_limit − used_credit）。</li>
 *   <li><b>成交事件消费</b>：消费 {@link TradeEvent}，按成交金额扣减/释放可用额度。
 *       客户 BUY → used_credit += amount；客户 SELL → used_credit −= amount。</li>
 * </ol>
 * <p>
 * <b>幂等消费</b>：通过 {@link IdempotentConsumer} + processed_events 表保证同一事件
 * 不被重复处理。事件 ID 取自 BaseEvent.eventId，缺失时回退到 tradeId。
 * <p>
 * <b>扣减规则</b>：
 * <ul>
 *   <li>BUY 占用信用：used_credit += amount（如客户买入 10 手 × 500 元 = 5000 元）</li>
 *   <li>SELL 释放信用：used_credit −= amount（卖出回收资金，释放信用）</li>
 *   <li>若客户不存在或被冻结，仅记录日志不抛异常（避免阻塞消费）</li>
 * </ul>
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    /** 金额统一保留 2 位小数（人民币/美元分） */
    private static final int AMOUNT_SCALE = 2;

    private final CustomerMapper customerMapper;
    private final IdempotentConsumer idempotentConsumer;
    private final IdGenerator idGenerator;

    public AccountService(CustomerMapper customerMapper,
                          IdempotentConsumer idempotentConsumer,
                          IdGenerator idGenerator) {
        this.customerMapper = customerMapper;
        this.idempotentConsumer = idempotentConsumer;
        this.idGenerator = idGenerator;
    }

    // ==================== 客户主数据 CRUD ====================

    /**
     * 创建新客户。
     * <p>
     * 默认值：level=NORMAL、status=ACTIVE、used_credit=0、version=0。
     * credit_limit 必须由调用方提供（不可为 null）。
     *
     * @param customer 客户实体（必须包含 customerId 与 creditLimit）
     * @return 已创建的客户实体（含生成的 id）
     * @throws IllegalArgumentException 当 customerId 或 creditLimit 缺失、或 customerId 已存在
     */
    @Transactional
    public Customer createCustomer(Customer customer) {
        if (customer.getCustomerId() == null || customer.getCustomerId().isEmpty()) {
            throw new IllegalArgumentException("customerId must not be null or empty");
        }
        if (customer.getCreditLimit() == null) {
            throw new IllegalArgumentException("creditLimit must not be null");
        }
        if (customerMapper.findByCustomerId(customer.getCustomerId()) != null) {
            throw new IllegalArgumentException("customer already exists: " + customer.getCustomerId());
        }

        if (customer.getLevel() == null) customer.setLevel("NORMAL");
        if (customer.getStatus() == null) customer.setStatus("ACTIVE");
        if (customer.getUsedCredit() == null) customer.setUsedCredit(BigDecimal.ZERO);
        customer.setId(idGenerator.nextLongId());
        customer.setVersion(0);
        long now = System.currentTimeMillis();
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);

        customerMapper.insert(customer);
        log.info("Customer created: customerId={}, name={}, level={}, creditLimit={}",
                customer.getCustomerId(), customer.getName(),
                customer.getLevel(), customer.getCreditLimit());
        return customer;
    }

    /**
     * 根据客户 ID 查询客户。
     *
     * @param customerId 客户 ID
     * @return 客户实体；不存在返回 null
     */
    public Customer findByCustomerId(String customerId) {
        return customerMapper.findByCustomerId(customerId);
    }

    /**
     * 查询全部客户。
     *
     * @return 客户列表
     */
    public List<Customer> findAll() {
        return customerMapper.findAll();
    }

    /**
     * 更新客户主数据（name/level/status/credit_limit），不影响 used_credit。
     *
     * @param customer 客户实体（按 customerId 定位）
     * @return 更新后的客户实体；客户不存在返回 null
     * @throws IllegalArgumentException 当客户不存在
     */
    @Transactional
    public Customer updateCustomer(Customer customer) {
        Customer existing = customerMapper.findByCustomerId(customer.getCustomerId());
        if (existing == null) {
            throw new IllegalArgumentException("customer not found: " + customer.getCustomerId());
        }

        // 仅更新主数据字段，保留 used_credit 与 version（由 update SQL 处理）
        if (customer.getName() == null) customer.setName(existing.getName());
        if (customer.getLevel() == null) customer.setLevel(existing.getLevel());
        if (customer.getStatus() == null) customer.setStatus(existing.getStatus());
        if (customer.getCreditLimit() == null) customer.setCreditLimit(existing.getCreditLimit());
        customer.setUsedCredit(existing.getUsedCredit());
        customer.setUpdatedAt(System.currentTimeMillis());

        customerMapper.update(customer);
        log.info("Customer updated: customerId={}, name={}, level={}, status={}, creditLimit={}",
                customer.getCustomerId(), customer.getName(), customer.getLevel(),
                customer.getStatus(), customer.getCreditLimit());
        return customer;
    }

    /**
     * 删除客户（按 customerId 定位）。
     * <p>
     * 注意：物理删除，生产环境建议改为状态置为 CLOSED 而非物理删除。
     *
     * @param customerId 客户 ID
     * @return 是否删除成功
     */
    @Transactional
    public boolean deleteCustomer(String customerId) {
        int rows = customerMapper.deleteByCustomerId(customerId);
        if (rows > 0) {
            log.info("Customer deleted: customerId={}", customerId);
            return true;
        }
        return false;
    }

    // ==================== 信用额度查询 ====================

    /**
     * 查询客户信用额度信息。
     * <p>
     * 返回 credit_limit、used_credit、available_credit 三项。
     *
     * @param customerId 客户 ID
     * @return 信用额度视图；客户不存在返回 null
     */
    public CreditInfo getCreditInfo(String customerId) {
        Customer customer = customerMapper.findByCustomerId(customerId);
        if (customer == null) {
            return null;
        }
        BigDecimal creditLimit = customer.getCreditLimit() != null
                ? customer.getCreditLimit() : BigDecimal.ZERO;
        BigDecimal usedCredit = customer.getUsedCredit() != null
                ? customer.getUsedCredit() : BigDecimal.ZERO;
        BigDecimal available = creditLimit.subtract(usedCredit);
        return new CreditInfo(customerId, creditLimit, usedCredit, available);
    }

    // ==================== 成交事件消费 ====================

    /**
     * 处理客户成交事件，扣减/释放可用额度。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>幂等校验：processed_events 表去重</li>
     *   <li>查找客户，若不存在仅记录日志返回（不抛异常）</li>
     *   <li>按 BUY/SELL 方向计算额度变化量</li>
     *   <li>更新 used_credit 字段</li>
     * </ol>
     *
     * @param event 客户成交事件
     */
    @Transactional
    public void onTradeEvent(TradeEvent event) {
        String eventId = resolveEventId(event.getEventId(), event.getTradeId());
        idempotentConsumer.consume(eventId, () -> {
            log.info("Processing trade event for account: tradeId={}, customerId={}, side={}, amount={}",
                    event.getTradeId(), event.getCustomerId(), event.getSide(), event.getAmount());
            applyTradeEvent(event);
            return null;
        });
    }

    /**
     * 应用成交事件到客户额度（无幂等校验，由调用方保证）。
     */
    private void applyTradeEvent(TradeEvent event) {
        Customer customer = customerMapper.findByCustomerId(event.getCustomerId());
        if (customer == null) {
            log.warn("Customer not found, skip credit update: customerId={}, tradeId={}",
                    event.getCustomerId(), event.getTradeId());
            return;
        }
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            log.warn("Customer not active, skip credit update: customerId={}, status={}",
                    customer.getCustomerId(), customer.getStatus());
            return;
        }

        BigDecimal currentUsed = customer.getUsedCredit() != null
                ? customer.getUsedCredit() : BigDecimal.ZERO;
        BigDecimal amount = event.getAmount() != null
                ? event.getAmount() : event.getQty().multiply(event.getPrice());

        OrderSide side = OrderSide.of(event.getSide());
        BigDecimal newUsed;
        if (side == OrderSide.BUY) {
            // 占用信用：used_credit += amount
            newUsed = currentUsed.add(amount);
        } else {
            // 释放信用：used_credit -= amount（最低不低于 0）
            newUsed = currentUsed.subtract(amount);
            if (newUsed.signum() < 0) {
                log.info("Used credit would be negative, clamping to 0: customerId={}, before={}, computed={}",
                        customer.getCustomerId(), currentUsed, newUsed);
                newUsed = BigDecimal.ZERO;
            }
        }
        newUsed = newUsed.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        customer.setUsedCredit(newUsed);
        customer.setUpdatedAt(System.currentTimeMillis());
        customerMapper.updateUsedCredit(customer);

        log.info("Credit updated: customerId={}, side={}, amount={}, usedCredit={}, available={}",
                customer.getCustomerId(), side, amount, newUsed,
                customer.getCreditLimit().subtract(newUsed));
    }

    /**
     * 解析事件 ID：优先使用 eventId，否则回退到 tradeId。
     *
     * @param eventId    事件 ID
     * @param fallbackId 业务唯一键（tradeId）
     * @return 用于幂等去重的 ID
     */
    private String resolveEventId(String eventId, String fallbackId) {
        if (eventId != null && !eventId.isEmpty()) {
            return eventId;
        }
        return fallbackId;
    }
}

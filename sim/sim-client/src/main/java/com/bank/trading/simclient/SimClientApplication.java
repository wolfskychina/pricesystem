package com.bank.trading.simclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 模拟客户端启动类。
 * <p>
 * 模拟做市商客户行为：批量、随机地向 OMS / Gateway 提交订单，
 * 用于联调验证与轻量级压测。控制接口见 {@code SimClientController}。
 */
@SpringBootApplication
public class SimClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimClientApplication.class, args);
    }
}

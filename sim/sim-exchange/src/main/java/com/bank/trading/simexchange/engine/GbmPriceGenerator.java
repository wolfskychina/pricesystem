package com.bank.trading.simexchange.engine;

import java.util.Random;

public class GbmPriceGenerator {

    private final double drift;
    private final double volatility;
    private final double dt;
    private final Random random;

    public GbmPriceGenerator(double drift, double volatility, double dt, Random random) {
        this.drift = drift;
        this.volatility = volatility;
        this.dt = dt;
        this.random = random;
    }

    public GbmPriceGenerator(double drift, double volatility, double dtSeconds) {
        this.drift = drift;
        this.volatility = volatility;
        this.dt = dtSeconds / 86400.0;
        this.random = new Random();
    }

    public double nextPrice(double currentPrice) {
        double z = random.nextGaussian();
        double mu = drift - 0.5 * volatility * volatility;
        double exponent = mu * dt + volatility * Math.sqrt(dt) * z;
        return currentPrice * Math.exp(exponent);
    }
}

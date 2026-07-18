package com.bank.trading.simexchange.config;

import com.bank.trading.simexchange.model.SymbolConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "sim-exchange")
public class SimExchangeProperties {

    private int intervalMs;
    private List<SymbolConfig> symbols;

    public int getIntervalMs() {
        return intervalMs;
    }

    public List<SymbolConfig> getSymbols() {
        return symbols;
    }

    public void setIntervalMs(int intervalMs) {
        this.intervalMs = intervalMs;
    }

    public void setSymbols(List<SymbolConfig> symbols) {
        this.symbols = symbols;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimExchangeProperties that = (SimExchangeProperties) o;
        if (intervalMs != that.intervalMs) return false;
        if (symbols != null ? !symbols.equals(that.symbols) : that.symbols != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (int) intervalMs;
        result = 31 * result + (symbols != null ? symbols.hashCode() : 0);
        return result;
    }
    @Override
    public String toString() {
        return "SimExchangeProperties{intervalMs=" + intervalMs + ", symbols=" + symbols + "}";
    }

}

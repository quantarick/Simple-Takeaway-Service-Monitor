package com.engineering.challenge.solution.domain.entities;

import com.engineering.challenge.solution.domain.ShelfType;
import com.engineering.challenge.solution.utils.FNV1a;

import org.redisson.spring.data.connection.SecondsConvertor;
import org.springframework.util.SerializationUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

import lombok.Data;

import static java.time.temporal.ChronoUnit.SECONDS;

@Data
public class Order implements Serializable {

    private String name;

    private Double decayRate;

    private ShelfType temp;

    private Double shelfLife;

    private Double value;

    private LocalDateTime onShelfDate;

    private Boolean isOnOverflowShelf = false;

    private LocalDateTime decayDate;

    public Long getIdentifier() {
        return FNV1a.hash64(SerializationUtils.serialize(name));
    }

    /**
     * V(0) = ShelfLife - (1 + actualDecayRate) * t where t is the on-shelf duration and V(N) = V(N - 1) - ( 1 + actualDecayRate) * t where
     * t is the on-shelf duration counting from shelf-switch
     */
    public void resetValue() {
        Long onShelfDuration = SECONDS.between(onShelfDate, LocalDateTime.now());
        Double actualDecayRate = getActualDecayRate();
        if (value == null) {
            value = shelfLife - (1d + actualDecayRate) * onShelfDuration;
        } else {
            value = value - (1d + actualDecayRate) * onShelfDuration;
        }
    }

    public Double getNormalizedValue() {
        if (decayDate == null || shelfLife == null) return null;
        Long latestDeliveryTime = SECONDS.between(LocalDateTime.now(), decayDate);
        return latestDeliveryTime / shelfLife;
    }

    public Long getLatestDeliveryTime() {
        Double actualDecayRate = getActualDecayRate();
        Long latestDeliveryTime = null;
        if (value == null) {
            latestDeliveryTime = new Double(shelfLife / (1d + actualDecayRate)).longValue();
        } else {
            latestDeliveryTime = new Double(value / (1d + actualDecayRate)).longValue();
        }
        decayDate = LocalDateTime.now().plus(latestDeliveryTime, SECONDS);
        return latestDeliveryTime;
    }

    private Double getActualDecayRate() {
        return isOnOverflowShelf ? 2 * decayRate : decayRate;
    }

    @Override
    public String toString() {
        return "Order{" +
            "name='" + name + '\'' +
            ", decayRate=" + decayRate +
            ", temp=" + temp +
            ", shelfLife=" + shelfLife +
            ", value=" + value +
            ", onShelfDate=" + onShelfDate +
            ", isOnOverflowShelf=" + isOnOverflowShelf +
            ", normalizedValue=" + getNormalizedValue() +
            ", latestDeliveryTime=" + getLatestDeliveryTime() +
            '}';
    }
}

package com.engineering.challenge.solution.domain.entities;

import com.engineering.challenge.solution.domain.ShelfType;
import com.engineering.challenge.solution.utils.FNV1a;

import org.springframework.util.SerializationUtils;

import java.io.Serializable;
import java.time.LocalDateTime;

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
     *  Reset the order value, this needs to be called each time when the order has been picked up or put on the shelf.
     * V (1) = ShelfLife-(1 + actualDecayRate) * t where V (1) is the value on shelf-1 before shelf-switch and t is the time on the shelf-1
     * ....
     * V (N) = V (N-1)-(1 + actualDecayRate) * t where V (N) is the value on shelf-N and t is the time on shelf-N
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

    /**
     * Get the normalized order value.
     * @return
     */
    public Double getNormalizedValue() {
        if (decayDate == null) getLatestDeliveryTime();
        Long latestDeliveryTime = SECONDS.between(LocalDateTime.now(), decayDate);
        return latestDeliveryTime * (1d + getActualDecayRate()) / shelfLife;
    }

    /**
     * Get the latest delivery time in seconds.
     * @return
     */
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

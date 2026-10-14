package com.sporthub.common.exception;

import com.sporthub.common.schedule.ConfigurationGuardProtocol.Reservation;
import java.util.List;
import java.util.Map;

/** Only raised after the calling service has verified facility ownership. No customer PII. */
public class ConfigurationImpactException extends ConflictException {
    private final Map<String,Object> details;
    public ConfigurationImpactException(List<Reservation> affected) {
        super("Configuration affects existing reservations. Resolve the conflicts before applying it");
        details=Map.of("reason","CONFIGURATION_IMPACT","affectedCount",affected.size(),
            "affectedReservations",List.copyOf(affected.subList(0,Math.min(50,affected.size()))),
            "truncated",affected.size()>50,"resolution","MANUAL_RESOLUTION_REQUIRED");
    }
    public Map<String,Object> details() {return details;}
}

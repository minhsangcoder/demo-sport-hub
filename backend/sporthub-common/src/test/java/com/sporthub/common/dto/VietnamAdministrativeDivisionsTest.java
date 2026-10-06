package com.sporthub.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VietnamAdministrativeDivisionsTest {
    @Test void everyBundledWardResolvesOnlyUnderItsCurrentProvince() throws Exception {
        try (var input = getClass().getResourceAsStream("/vietnam-administrative-divisions.json")) {
            var provinces = new ObjectMapper().readTree(input).path("provinces");
            assertThat(provinces.size()).isEqualTo(34);
            int count = 0;
            for (var province : provinces) for (var ward : province.path("wards")) {
                var address = VietnamAdministrativeDivisions.resolve(province.path("code").asText(), ward.path("code").asText());
                assertThat(address.province()).isEqualTo(province.path("name").asText());
                assertThat(address.ward()).isEqualTo(ward.path("name").asText());
                count++;
            }
            assertThat(count).isEqualTo(3321);
        }
    }

    @Test void rejectsUnknownMissingAndCrossProvinceCodes() {
        assertThatThrownBy(() -> VietnamAdministrativeDivisions.resolve("99", "00004")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VietnamAdministrativeDivisions.resolve(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VietnamAdministrativeDivisions.resolve("01", "99999")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VietnamAdministrativeDivisions.resolve("79", "00004")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VietnamAdministrativeDivisions.resolve("01", "4")).isInstanceOf(IllegalArgumentException.class);
    }
}

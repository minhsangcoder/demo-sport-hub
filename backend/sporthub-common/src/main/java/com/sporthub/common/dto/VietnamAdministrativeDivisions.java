package com.sporthub.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** The bundled two-level Vietnam dataset is the authority for administrative codes and names. */
public final class VietnamAdministrativeDivisions {
    public record Address(String provinceCode, String province, String wardCode, String ward) {}
    private record Province(String name, Map<String, String> wards) {}
    private static final Map<String, Province> PROVINCES = load();

    private VietnamAdministrativeDivisions() {}

    public static Address resolve(String provinceCode, String wardCode) {
        var province = provinceCode == null ? null : PROVINCES.get(provinceCode);
        if (province == null) throw new IllegalArgumentException("Choose a current Vietnam province/city code");
        var ward = wardCode == null ? null : province.wards().get(wardCode);
        if (ward == null) throw new IllegalArgumentException("Choose a current commune/ward belonging to the selected province/city");
        return new Address(provinceCode, province.name(), wardCode, ward);
    }

    private static Map<String, Province> load() {
        try (var input = VietnamAdministrativeDivisions.class.getResourceAsStream("/vietnam-administrative-divisions.json")) {
            if (input == null) throw new IllegalStateException("Vietnam administrative dataset is missing");
            var root = new ObjectMapper().readTree(input);
            var provinces = new HashMap<String, Province>();
            for (var entry : root.path("provinces")) {
                var wards = new HashMap<String, String>();
                for (var ward : entry.path("wards")) {
                    String code = ward.path("code").asText(), name = ward.path("name").asText();
                    if (!code.matches("[0-9]{5}") || name.isBlank() || wards.put(code, name) != null)
                        throw new IllegalStateException("Invalid or duplicate Vietnam ward code");
                }
                String code = entry.path("code").asText(), name = entry.path("name").asText();
                if (!code.matches("[0-9]{2}") || name.isBlank() || wards.isEmpty()
                        || provinces.put(code, new Province(name, Map.copyOf(wards))) != null)
                    throw new IllegalStateException("Invalid or duplicate Vietnam province code");
            }
            if (provinces.isEmpty()) throw new IllegalStateException("Vietnam administrative dataset is empty");
            return Map.copyOf(provinces);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load Vietnam administrative dataset", ex);
        }
    }
}

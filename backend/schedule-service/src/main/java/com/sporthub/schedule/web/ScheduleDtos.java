package com.sporthub.schedule.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ScheduleDtos {
    private ScheduleDtos() {}
    public record Interval(@Min(1) @Max(7) int dayOfWeek,@NotNull LocalTime opensAt,
        @NotNull LocalTime closesAt,@Min(5) @Max(720) int slotMinutes) {}
    public record HoursInput(UUID courtId,@NotNull @Size(max=100) List<@Valid Interval> intervals) {}
    public record Hours(UUID id,UUID facilityId,UUID courtId,int dayOfWeek,LocalTime opensAt,
        LocalTime closesAt,int slotMinutes,long version) {}
    public record ExceptionInput(UUID courtId,@NotNull LocalDate date,
        @NotNull @Pattern(regexp="CLOSED|SPECIAL_HOURS") String type,LocalTime opensAt,
        LocalTime closesAt,@NotBlank @Size(max=500) String reason) {}
    public record ExceptionView(UUID id,UUID facilityId,UUID courtId,LocalDate date,String type,
        LocalTime opensAt,LocalTime closesAt,String reason) {}
    public record PriceInput(UUID courtId,UUID sportCategoryId,@Min(1) @Max(7) Integer dayOfWeek,
        LocalDate specificDate,@NotNull LocalTime startsAt,@NotNull LocalTime endsAt,
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=10,fraction=2) BigDecimal pricePerSlot,
        @Min(0) @Max(10000) int priority,@NotBlank @Size(max=200) String label,
        @NotNull LocalDate effectiveFrom,LocalDate effectiveTo,@Pattern(regexp="VND") @NotNull String currency,UUID courtTypeId,@com.fasterxml.jackson.annotation.JsonIgnore boolean courtTypeProvided) {
        public PriceInput(UUID court,UUID category,Integer weekday,LocalDate date,LocalTime start,LocalTime end,BigDecimal price,int priority,String label,LocalDate from,LocalDate to,String currency){this(court,category,weekday,date,start,end,price,priority,label,from,to,currency,null,false);}
        public PriceInput(UUID court,UUID category,Integer weekday,LocalDate date,LocalTime start,LocalTime end,BigDecimal price,int priority,String label,LocalDate from,LocalDate to,String currency,UUID type){this(court,category,weekday,date,start,end,price,priority,label,from,to,currency,type,true);}
        @com.fasterxml.jackson.annotation.JsonCreator public static PriceInput fromJson(@com.fasterxml.jackson.annotation.JsonProperty("courtId") UUID court,@com.fasterxml.jackson.annotation.JsonProperty("sportCategoryId") UUID category,@com.fasterxml.jackson.annotation.JsonProperty("dayOfWeek") Integer weekday,@com.fasterxml.jackson.annotation.JsonProperty("specificDate") LocalDate date,@com.fasterxml.jackson.annotation.JsonProperty("startsAt") LocalTime start,@com.fasterxml.jackson.annotation.JsonProperty("endsAt") LocalTime end,@com.fasterxml.jackson.annotation.JsonProperty("pricePerSlot") BigDecimal price,@com.fasterxml.jackson.annotation.JsonProperty("priority") int priority,@com.fasterxml.jackson.annotation.JsonProperty("label") String label,@com.fasterxml.jackson.annotation.JsonProperty("effectiveFrom") LocalDate from,@com.fasterxml.jackson.annotation.JsonProperty("effectiveTo") LocalDate to,@com.fasterxml.jackson.annotation.JsonProperty("currency") String currency,@com.fasterxml.jackson.annotation.JsonProperty("courtTypeId") com.fasterxml.jackson.databind.JsonNode type){return new PriceInput(court,category,weekday,date,start,end,price,priority,label,from,to,currency,type==null||type.isNull()?null:UUID.fromString(type.asText()),type!=null);}
    }
    public record PriceRule(UUID id,UUID facilityId,UUID courtId,UUID sportCategoryId,Integer dayOfWeek,
        LocalDate specificDate,LocalTime startsAt,LocalTime endsAt,BigDecimal pricePerSlot,int priority,
        String label,LocalDate effectiveFrom,LocalDate effectiveTo,String currency,boolean active,long version,UUID courtTypeId,String legacyCourtType) {
        public PriceRule(UUID id,UUID facility,UUID court,UUID category,Integer weekday,LocalDate date,LocalTime start,LocalTime end,BigDecimal price,int priority,String label,LocalDate from,LocalDate to,String currency,boolean active,long version){this(id,facility,court,category,weekday,date,start,end,price,priority,label,from,to,currency,active,version,null,null);}
    }
    public record PriceRevision(UUID ruleId,long version,com.fasterxml.jackson.databind.JsonNode snapshot,UUID actorId,Instant recordedAt) {}
    public record PricingDraft(@NotNull UUID courtId,@NotNull LocalDate date,@NotNull @Valid PriceInput rule,UUID replacesRuleId) {}
    public record QuoteInput(@NotNull UUID courtId,@NotNull Instant startsAt,@NotNull Instant endsAt) {}
    public record Slot(Instant startsAt,Instant endsAt,String state,String reason,BigDecimal amount,
        String currency,UUID ruleId,long ruleVersion,String ruleLabel,List<UUID> conflictingRuleIds) {
        public Slot(Instant start,Instant end,String state,String reason,BigDecimal amount,String currency,UUID rule,long version,String label){this(start,end,state,reason,amount,currency,rule,version,label,List.of());}
    }
    public record Preview(UUID facilityId,UUID courtId,LocalDate date,String timezone,List<Slot> slots) {}
    public record Quote(UUID facilityId,UUID courtId,Instant startsAt,Instant endsAt,BigDecimal amount,
        String currency,String timezone,List<Slot> segments) {}
}

package com.sporthub.schedule.service;

import com.sporthub.schedule.web.ScheduleDtos.PriceRule;
import java.time.*;
import java.util.*;

/** One policy used by persisted preview, draft preview and quote. No client-side calculation. */
public class PricingEngine {
    public record Rank(boolean specific,int qualifiers,int priority) implements Comparable<Rank> {
        public int compareTo(Rank other) {
            int result=Boolean.compare(specific,other.specific);
            if(result==0)result=Integer.compare(qualifiers,other.qualifiers);
            return result==0?Integer.compare(priority,other.priority):result;
        }
    }
    public Rank rank(PriceRule rule) {
        return new Rank(rule.courtId()!=null||rule.specificDate()!=null,
            (rule.courtId()!=null?1:0)+(rule.specificDate()!=null?1:0)+(rule.sportCategoryId()!=null?1:0)
                +(rule.courtTypeId()!=null||legacy(rule)?1:0),rule.priority());
    }
    public boolean legacy(PriceRule rule){return rule.courtTypeId()==null&&rule.legacyCourtType()!=null&&!rule.legacyCourtType().isBlank();}
    public boolean scopeMatches(PriceRule r,FacilityClient.Context c) {
        return (r.courtId()==null||r.courtId().equals(c.courtId()))
            &&(r.sportCategoryId()==null||r.sportCategoryId().equals(c.sportCategoryId()))
            &&(r.courtTypeId()==null||r.courtTypeId().equals(c.courtTypeId()));
    }
    public boolean matches(PriceRule r,FacilityClient.Context c,LocalDate day,LocalTime start,LocalTime end) {
        return r.active()&&scopeMatches(r,c)&&dateMatches(r,day)&&!start.isBefore(r.startsAt())&&!end.isAfter(r.endsAt());
    }
    public boolean dateMatches(PriceRule r,LocalDate day) {
        return r.effectiveFrom()!=null&&!day.isBefore(r.effectiveFrom())&&(r.effectiveTo()==null||!day.isAfter(r.effectiveTo()))
            &&(r.specificDate()!=null?r.specificDate().equals(day):Objects.equals(r.dayOfWeek(),day.getDayOfWeek().getValue()));
    }
    /** Exact calendar intersection, including a shared weekday inside the intersection of effective periods. */
    public boolean datesOverlap(PriceRule a,PriceRule b) {
        LocalDate from=a.effectiveFrom().isAfter(b.effectiveFrom())?a.effectiveFrom():b.effectiveFrom();
        LocalDate to=a.effectiveTo()==null?b.effectiveTo():b.effectiveTo()==null?a.effectiveTo():a.effectiveTo().isBefore(b.effectiveTo())?a.effectiveTo():b.effectiveTo();
        if(to!=null&&from.isAfter(to))return false;
        if(a.specificDate()!=null)return dateMatches(a,a.specificDate())&&dateMatches(b,a.specificDate());
        if(b.specificDate()!=null)return dateMatches(a,b.specificDate())&&dateMatches(b,b.specificDate());
        if(!Objects.equals(a.dayOfWeek(),b.dayOfWeek()))return false;
        return to==null||from.plusDays(Math.floorMod(a.dayOfWeek()-from.getDayOfWeek().getValue(),7)).compareTo(to)<=0;
    }
    public boolean sameScope(PriceRule a,PriceRule b) {
        return Objects.equals(a.courtId(),b.courtId())&&Objects.equals(a.sportCategoryId(),b.sportCategoryId())&&Objects.equals(a.courtTypeId(),b.courtTypeId())&&(a.specificDate()!=null)==(b.specificDate()!=null);
    }
    /** Different dimensions may intersect on a future court; do not rely on today's inventory to permit ambiguity. */
    public boolean scopeMayIntersect(PriceRule a,PriceRule b,java.util.function.Function<UUID,FacilityClient.Context> court) {
        if(a.courtId()!=null&&b.courtId()!=null&&!a.courtId().equals(b.courtId()))return false;
        if(a.sportCategoryId()!=null&&b.sportCategoryId()!=null&&!a.sportCategoryId().equals(b.sportCategoryId()))return false;
        if(a.courtTypeId()!=null&&b.courtTypeId()!=null&&!a.courtTypeId().equals(b.courtTypeId()))return false;
        UUID id=a.courtId()!=null?a.courtId():b.courtId();
        return id==null||(scopeMatches(a,court.apply(id))&&scopeMatches(b,court.apply(id)));
    }
    public boolean conflict(PriceRule a,PriceRule b,java.util.function.Function<UUID,FacilityClient.Context> court) {
        return a.active()&&b.active()&&Objects.equals(a.pricingUnit(),b.pricingUnit())&&a.priority()==b.priority()
            &&(sameScope(a,b)||rank(a).equals(rank(b)))
            &&a.startsAt().isBefore(b.endsAt())&&a.endsAt().isAfter(b.startsAt())
            &&datesOverlap(a,b)&&scopeMayIntersect(a,b,court);
    }
}

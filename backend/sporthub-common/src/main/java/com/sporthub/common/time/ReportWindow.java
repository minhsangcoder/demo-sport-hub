package com.sporthub.common.time;
import java.time.*;
/** Half-open instants. Zone only controls presentation; never rely on a DB session timezone. */
public record ReportWindow(Instant from,Instant to,ZoneId timezone) {
 public static ReportWindow of(Instant from,Instant to,String zone,Instant now){
  var end=to==null?now:to;var start=from==null?end.minus(Duration.ofDays(30)):from;
  if(!start.isBefore(end)||Duration.between(start,end).compareTo(Duration.ofDays(366))>0)throw new IllegalArgumentException("Report range must be positive and no more than 366 days");
  String name=zone==null?"UTC":zone;
  // PostgreSQL interprets numeric text offsets using POSIX signs, unlike java.time.
  if(!ZoneId.getAvailableZoneIds().contains(name))throw new IllegalArgumentException("Use an IANA report timezone such as UTC or Asia/Ho_Chi_Minh");
  return new ReportWindow(start,end,ZoneId.of(name));
 }
}

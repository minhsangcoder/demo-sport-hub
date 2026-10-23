package com.sporthub.schedule.service;

import static com.sporthub.schedule.web.ScheduleDtos.*;

import com.sporthub.common.exception.*;
import java.time.*;
import java.util.*;

public class ScheduleValues {

  private ScheduleValues() {}

  public static void validateTime(LocalTime time) {
    if (time == null || time.getSecond() != 0 || time.getNano() != 0)
      throw new IllegalArgumentException("Schedule times must use whole minutes");
  }

  public static boolean overlap(LocalTime a, LocalTime b, LocalTime c, LocalTime d) {
    return a.isBefore(d) && b.isAfter(c);
  }

  public static java.sql.Time time(LocalTime value) {
    return value == null ? null : java.sql.Time.valueOf(value);
  }

  public static java.sql.Date date(LocalDate value) {
    return value == null ? null : java.sql.Date.valueOf(value);
  }
}

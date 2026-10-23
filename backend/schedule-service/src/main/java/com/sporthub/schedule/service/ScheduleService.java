package com.sporthub.schedule.service;

import static com.sporthub.schedule.web.ScheduleDtos.*;

import com.sporthub.common.exception.*;
import com.sporthub.common.security.RemoteIdentity.Caller;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(
    readOnly = true,
    isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class ScheduleService {
  private final ScheduleReads reads;
  private final ScheduleSignup signup;
  private final ScheduleHours hours;
  private final SchedulePrices prices;

  public ScheduleService(
      ScheduleReads reads, ScheduleSignup signup, ScheduleHours hours, SchedulePrices prices) {
    this.reads = reads;
    this.signup = signup;
    this.hours = hours;
    this.prices = prices;
  }

  public List<Hours> hours(UUID facility, Caller caller, String token) {
    return reads.hours(facility, caller, token);
  }

  public List<ExceptionView> exceptions(UUID facility, Caller caller, String token) {
    return reads.exceptions(facility, caller, token);
  }

  public List<PriceRule> rules(UUID facility, Caller caller, String token) {
    return reads.rules(facility, caller, token);
  }

  @Transactional
  public Map<String, Object> prepareSignup(
      UUID facility,
      UUID application,
      UUID user,
      String timezone,
      com.sporthub.common.dto.OwnerSignupSetup setup) {
    return signup.prepareSignup(facility, application, user, timezone, setup);
  }

  @Transactional
  public Map<String, Object> freezeApplication(
      UUID facility, UUID application, List<FacilityClient.Context> contexts) {
    return signup.freezeApplication(facility, application, contexts);
  }

  @Transactional
  public Map<String, Object> releaseApplication(UUID facility, UUID application) {
    return signup.releaseApplication(facility, application);
  }

  @Transactional
  public List<Hours> replaceHours(UUID facility, HoursInput input, Caller caller, String token) {
    return hours.replaceHours(facility, input, caller, token);
  }

  @Transactional
  public ExceptionView addException(
      UUID facility, ExceptionInput input, Caller caller, String token) {
    return hours.addException(facility, input, caller, token);
  }

  @Transactional
  public ExceptionView updateException(
      UUID facility, UUID id, ExceptionInput input, Caller caller, String token) {
    return hours.updateException(facility, id, input, caller, token);
  }

  @Transactional
  public void removeException(UUID facility, UUID id, Caller caller, String token) {
    hours.removeException(facility, id, caller, token);
  }

  @Transactional
  public PriceRule addPrice(UUID facility, PriceInput input, Caller caller, String token) {
    return prices.addPrice(facility, input, caller, token);
  }

  @Transactional
  public PriceRule updatePrice(
      UUID facility, UUID id, PriceInput input, long expectedVersion, Caller caller, String token) {
    return prices.updatePrice(facility, id, input, expectedVersion, caller, token);
  }

  @Transactional
  public void disablePrice(UUID facility, UUID id, Caller caller, String token) {
    prices.disablePrice(facility, id, caller, token);
  }

  public List<PriceRevision> priceHistory(UUID facility, UUID id, Caller caller, String token) {
    return prices.priceHistory(facility, id, caller, token);
  }

  public Preview pricingDraft(UUID facility, PricingDraft draft, Caller caller, String token) {
    return prices.pricingDraft(facility, draft, caller, token);
  }

  public Preview ownerPreview(
      UUID facility, UUID court, LocalDate date, Caller caller, String token) {
    return reads.ownerPreview(facility, court, date, caller, token);
  }

  public Preview publicPreview(UUID court, LocalDate date) {
    return reads.publicPreview(court, date);
  }

  public Quote quote(QuoteInput input) {
    return reads.quote(input);
  }
}

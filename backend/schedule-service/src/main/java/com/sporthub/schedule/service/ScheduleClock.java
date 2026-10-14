package com.sporthub.schedule.service;
@org.springframework.context.annotation.Configuration
public class ScheduleClock {
    @org.springframework.context.annotation.Bean public java.time.Clock applicationClock(){return java.time.Clock.systemUTC();}
}

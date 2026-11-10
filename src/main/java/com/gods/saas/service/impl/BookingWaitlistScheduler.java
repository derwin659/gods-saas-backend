package com.gods.saas.service.impl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component @RequiredArgsConstructor @Slf4j
public class BookingWaitlistScheduler {
 private final BookingWaitlistService service;
 @Scheduled(fixedDelay=60000) public void process(){for(var id:service.dueIds())try{service.refresh(id);}catch(Exception ex){service.deferFailed(id);log.warn("waitlist check failed id={} type={}",id,ex.getClass().getSimpleName());}}
}

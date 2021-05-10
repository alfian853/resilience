package com.kruskal.resilix.core.window;

import com.kruskal.resilix.core.Configuration;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TimeBasedWindow extends AbstractSlidingWindow {

  private final Deque<Long> successAttemptWindow = new ConcurrentLinkedDeque<>();
  private final Deque<Long> failureAttemptWindow = new ConcurrentLinkedDeque<>();

  private final AtomicLong lastAttempt = new AtomicLong(0);
  private final AtomicBoolean updateQueLock = new AtomicBoolean(true);

  public TimeBasedWindow(Configuration configuration) {
    super(configuration);
  }

  @Override
  public void handleAckAttempt(boolean success) {
    lastAttempt.set(System.currentTimeMillis());
    if(success){
      successAttemptWindow.addLast(lastAttempt.get());
    }
    else {
      failureAttemptWindow.addLast(lastAttempt.longValue());
    }
    this.examineAttemptWindow();
  }

  @Override
  protected int getQueSize() {
    return successAttemptWindow.size() + failureAttemptWindow.size();
  }

  @Override
  protected double getErrorRateAfterMinCallSatisfied() {
    if(failureAttemptWindow.isEmpty() && successAttemptWindow.isEmpty()) return 0.0d;
    return ((double) failureAttemptWindow.size()) / (failureAttemptWindow.size() + successAttemptWindow.size());
  }

  private void examineAttemptWindow(){
    if(updateQueLock.compareAndSet(true, false)){
      while(!successAttemptWindow.isEmpty() &&
          successAttemptWindow.getFirst() < lastAttempt.get() - configuration.getWindowTimeRange()){
        successAttemptWindow.removeFirst();
      }
      while(!failureAttemptWindow.isEmpty() &&
          failureAttemptWindow.getFirst() < lastAttempt.get() - configuration.getWindowTimeRange()){
        failureAttemptWindow.removeFirst();
      }
      updateQueLock.set(true);
    }

  }

  @Override
  public void clear() {
    failureAttemptWindow.clear();
    successAttemptWindow.clear();
    lastAttempt.set(0);

  }
}
package org.infinispan.metrics.impl;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.metrics.Timer;
import org.infinispan.commons.stat.TimerTracker;

/**
 * A {@link TimerTracker} implementation that updates a {@link Timer} instance.
 *
 * @author Pedro Ruivo
 * @since 13.0
 */
public class TimerTrackerImpl implements TimerTracker {

   private final Timer timer;

   public TimerTrackerImpl(Timer timer) {
      this.timer = Objects.requireNonNull(timer, "Timer cannot be null.");
   }

   @Override
   public void update(long value, TimeUnit timeUnit) {
      timer.update(Duration.ofNanos(timeUnit.toNanos(value)));
   }
}

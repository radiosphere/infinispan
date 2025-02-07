package org.infinispan.notifications.cachelistener;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.infinispan.container.entries.CacheEntry;
import org.infinispan.notifications.cachelistener.event.Event;
import org.infinispan.reactive.publisher.impl.SegmentPublisherSupplier;
import org.infinispan.util.concurrent.CompletableFutures;
import org.infinispan.util.logging.Log;

/**
 * This is the base class for use when listening to segment completions when doing initial event
 * retrieval.  This will handle keeping track of concurrent key updates as well as iteration by calling
 * appropriate methods at the given time.
 * <p>
 * This base class provides a working set for tracking of entries as they are iterated on, assuming
 * the {@link QueueingSegmentListener#test(SegmentPublisherSupplier.Notification)}
 * method is invoked for each event serially (includes both segment and entries).  Also this class provides the events
 * that caused entry creations that may not be processed yet that are returned by the
 * {@link QueueingSegmentListener#findCreatedEntries()} method.
 *
 * @author wburns
 * @since 7.0
 */
abstract class BaseQueueingSegmentListener<K, V, E extends Event<K, V>> implements QueueingSegmentListener<K, V, E> {
   protected final AtomicBoolean completed = new AtomicBoolean(false);
   protected final ConcurrentMap<K, Object> notifiedKeys;

   protected BaseQueueingSegmentListener() {
      this.notifiedKeys = new ConcurrentHashMap<>();
   }

   @Override
   public Optional<CacheEntry<K, V>> apply(SegmentPublisherSupplier.Notification<CacheEntry<K, V>> cacheEntryNotification) throws Throwable {
      if (cacheEntryNotification.isSegmentComplete()) {
         segmentComplete(cacheEntryNotification.completedSegment());
         return Optional.empty();
      }

      CacheEntry<K, V> cacheEntry = cacheEntryNotification.value();
      K key = cacheEntry.getKey();
      // By putting the NOTIFIED value it has signaled that any more updates for this key have to be enqueued instead
      // of taking the last one
      Object value = notifiedKeys.put(key, NOTIFIED);
      if (value == null)
         return Optional.of(cacheEntry);

      if (getLog().isTraceEnabled()) {
         getLog().tracef("Processing key %s as a concurrent update occurred with value %s", key, value);
      }
      return value != QueueingSegmentListener.REMOVED ? Optional.of(((CacheEntry<K, V>) value)) : Optional.empty();
   }

   @Override
   public Set<CacheEntry<K, V>> findCreatedEntries() {
      Set<CacheEntry<K, V>> set = new HashSet<>();
      // We also have to look for any additional creations that we didn't iterate on
      for (Map.Entry<K, Object> entry : notifiedKeys.entrySet()) {
         Object value = entry.getValue();
         if (value != NOTIFIED) {
            K key = entry.getKey();
            Object replaceValue = value;
            // Now try to put NOTIFIED in there - this is in case if another concurrent event comes in like a
            // PUT/REMOVE/CLEAR
            while (replaceValue != NOTIFIED && !notifiedKeys.replace(key, replaceValue, NOTIFIED)) {
               replaceValue = notifiedKeys.get(key);
            }
            // Technically we should never get NOTIFIED as this is required to be called after manually marking
            // keys as processed
            if (replaceValue != NOTIFIED && replaceValue != REMOVED) {
               set.add((CacheEntry<K, V>)replaceValue);
            }
         }
      }
      return set;
   }

   @Override
   public CompletionStage<Void> delayProcessing() {
      return CompletableFutures.completedNull();
   }

   void segmentComplete(int segment) {
      // Don't do anything here - should implement accept if segment completions are desired
   }

   protected boolean addEvent(K key, Object value) {
      boolean returnValue;
      Object prevEvent = notifiedKeys.get(key);
      if (prevEvent == null) {
         Object nowPrevious = notifiedKeys.putIfAbsent(key, value);
         if (nowPrevious == null) {
            returnValue = true;
         } else if (nowPrevious != NOTIFIED) {
            returnValue = addEvent(key, value);
         } else {
            returnValue = false;
         }
      } else if (prevEvent != NOTIFIED) {
         if (notifiedKeys.replace(key, prevEvent, value)) {
            returnValue = true;
         } else {
            returnValue = addEvent(key, value);
         }
      } else {
         returnValue = false;
      }
      return returnValue;
   }

   protected abstract Log getLog();
}

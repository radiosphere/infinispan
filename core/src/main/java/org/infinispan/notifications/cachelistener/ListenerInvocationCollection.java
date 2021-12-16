package org.infinispan.notifications.cachelistener;

import org.infinispan.commands.CommandInvocationId;
import org.infinispan.commands.FlagAffectedCommand;
import org.infinispan.commands.write.WriteCommand;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.dataconversion.Wrapper;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.FlagBitSets;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.encoding.DataConversion;
import org.infinispan.marshall.core.EncoderRegistry;
import org.infinispan.metadata.Metadata;
import org.infinispan.notifications.cachelistener.event.impl.EventImpl;
import org.infinispan.notifications.cachelistener.filter.CacheEventConverter;
import org.infinispan.notifications.cachelistener.filter.CacheEventFilter;
import org.infinispan.notifications.cachelistener.filter.KeyBoundOperation;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.concurrent.AggregateCompletionStage;
import org.infinispan.util.concurrent.CompletionStages;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ListenerInvocationCollection<K, V> implements List<CacheEntryListenerInvocation<K, V>> {

    private CopyOnWriteArrayList<CacheEntryListenerInvocation<K, V>> allItemsList = new CopyOnWriteArrayList<>();

    private ConcurrentHashMap<K, List<CacheEntryListenerInvocation<K, V>>> keySpecificInvocations = new ConcurrentHashMap<>();
    private List<CacheEntryListenerInvocation<K, V>> nonKeySpecificInvocations = new CopyOnWriteArrayList<>();

    @Override
    public int size() {
        return allItemsList.size();
    }

    @Override
    public boolean isEmpty() {
        return allItemsList.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return allItemsList.contains(o);
    }

    @Override
    public Iterator<CacheEntryListenerInvocation<K, V>> iterator() {
        return allItemsList.iterator();
    }

    @Override
    public Object[] toArray() {
        return allItemsList.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return allItemsList.toArray(a);
    }

    @Override
    public boolean add(CacheEntryListenerInvocation<K, V> item) {
        if (item.getFilter() != null && item.getFilter() instanceof KeyBoundOperation) {
            K key = (K) (((KeyBoundOperation) item.getFilter()).getKey());
            synchronized (keySpecificInvocations) {
                if (!keySpecificInvocations.containsKey(key)) {
                    keySpecificInvocations.put(key, new CopyOnWriteArrayList<>());
                }
                keySpecificInvocations.get(key).add(item);
                System.out.println("Add Key Bound Operation.");
            }
        } else {
            nonKeySpecificInvocations.add(item);
        }
        return allItemsList.add(item);
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = allItemsList.remove(o);
        if(!removed) {
            return false;
        }

        if (o instanceof CacheEntryListenerInvocation
                && ((CacheEntryListenerInvocation<?, ?>) o).getFilter() != null
                && ((CacheEntryListenerInvocation<?, ?>) o).getFilter() instanceof KeyBoundOperation) {
            K key = (K) (((KeyBoundOperation) ((CacheEntryListenerInvocation<?, ?>) o).getFilter()).getKey());
            synchronized (keySpecificInvocations) {
                if (keySpecificInvocations.containsKey(key)) {
                    List<?> list = keySpecificInvocations.get(key);
                    list.remove(o);
                    if(list.size() == 0) {
                        keySpecificInvocations.remove(key);
                    }
                }
            }
        } else {
            nonKeySpecificInvocations.remove(o);
        }

        return true;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return allItemsList.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends CacheEntryListenerInvocation<K, V>> c) {
        return allItemsList.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends CacheEntryListenerInvocation<K, V>> c) {
        return allItemsList.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean changeOccurred = false;
        for(Object item : c) {
            changeOccurred = remove(item) || changeOccurred;
        }
        return changeOccurred;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        allItemsList.clear();
        nonKeySpecificInvocations.clear();
        keySpecificInvocations.clear();
    }

    @Override
    public CacheEntryListenerInvocation<K, V> get(int index) {
        return allItemsList.get(index);
    }

    @Override
    public CacheEntryListenerInvocation<K, V> set(int index, CacheEntryListenerInvocation<K, V> element) {
        return allItemsList.set(index, element);
    }

    @Override
    public void add(int index, CacheEntryListenerInvocation<K, V> element) {
        allItemsList.add(index, element);
    }

    @Override
    public CacheEntryListenerInvocation<K, V> remove(int index) {
        return allItemsList.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return allItemsList.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return allItemsList.lastIndexOf(o);
    }

    @Override
    public ListIterator<CacheEntryListenerInvocation<K, V>> listIterator() {
        return allItemsList.listIterator();
    }

    @Override
    public ListIterator<CacheEntryListenerInvocation<K, V>> listIterator(int index) {
        return allItemsList.listIterator(index);
    }

    @Override
    public List<CacheEntryListenerInvocation<K, V>> subList(int fromIndex, int toIndex) {
        return allItemsList.subList(fromIndex, toIndex);
    }

    protected static AggregateCompletionStage<Void> composeStageIfNeeded(
            AggregateCompletionStage<Void> aggregateCompletionStage, CompletionStage<Void> stage) {
        if (stage != null && !CompletionStages.isCompletedSuccessfully(stage)) {
            if (aggregateCompletionStage == null) {
                aggregateCompletionStage = CompletionStages.aggregateCompletionStage();
            }
            aggregateCompletionStage.dependsOn(stage);
        }
        return aggregateCompletionStage;
    }

    public AggregateCompletionStage<Void> notifyEvent(EventImpl<K, V> e, K key, V value, Metadata metadata,
                                                      boolean pre, InvocationContext ctx,
                                                      FlagAffectedCommand command, V previousValue, Metadata previousMetadata,
                                                      EncoderRegistry encoderRegistry, boolean isLocalNodePrimaryOwner) {
        AggregateCompletionStage<Void> aggregateCompletionStage = null;
        aggregateCompletionStage = notifyEventForList(nonKeySpecificInvocations, aggregateCompletionStage, e, key, value, metadata, pre, ctx, command, previousValue,
                previousMetadata, encoderRegistry, isLocalNodePrimaryOwner);
        if(keySpecificInvocations.containsKey(key)) {
            aggregateCompletionStage = notifyEventForList(keySpecificInvocations.get(key), aggregateCompletionStage, e, key, value, metadata, pre, ctx, command, previousValue,
                    previousMetadata, encoderRegistry, isLocalNodePrimaryOwner);
        }
        return aggregateCompletionStage;
    }

    private AggregateCompletionStage<Void> notifyEventForList(List<CacheEntryListenerInvocation<K, V>> list, AggregateCompletionStage<Void> aggregateCompletionStage,
                                                              EventImpl<K, V> e, K key, V value, Metadata metadata,
                                                              boolean pre, InvocationContext ctx,
                                                              FlagAffectedCommand command, V previousValue, Metadata previousMetadata,
                                                              EncoderRegistry encoderRegistry, boolean isLocalNodePrimaryOwner) {
        for (CacheEntryListenerInvocation<K, V> listener : nonKeySpecificInvocations) {
            // Need a wrapper per invocation since converter could modify the entry in it
            configureEvent(listener, e, key, value, metadata, pre, ctx, command, null, null,
                    encoderRegistry);
            aggregateCompletionStage = composeStageIfNeeded(aggregateCompletionStage,
                    listener.invoke(new EventWrapper<>(key, e), isLocalNodePrimaryOwner));
        }
        return aggregateCompletionStage;
    }

    /**
     * Configure event data. Currently used for 'created', 'modified', 'removed', 'invalidated' events.
     */
    private void configureEvent(CacheEntryListenerInvocation listenerInvocation,
                                EventImpl<K, V> e, K key, V value, Metadata metadata, boolean pre, InvocationContext ctx,
                                FlagAffectedCommand command, V previousValue, Metadata previousMetadata, EncoderRegistry encoderRegistry) {
        key = convertKey(listenerInvocation, key, encoderRegistry);
        value = convertValue(listenerInvocation, value, encoderRegistry);
        previousValue = convertValue(listenerInvocation, previousValue, encoderRegistry);

        e.setOriginLocal(ctx.isOriginLocal());
        e.setPre(pre);
        e.setValue(pre ? previousValue : value);
        e.setNewValue(value);
        e.setOldValue(previousValue);
        e.setOldMetadata(previousMetadata);
        e.setMetadata(metadata);
        if (command != null && command.hasAnyFlag(FlagBitSets.COMMAND_RETRY)) {
            e.setCommandRetried(true);
        }
        e.setKey(key);
        setSource(e, ctx, command);
    }

    private K convertKey(CacheEntryListenerInvocation listenerInvocation, K key, EncoderRegistry encoderRegistry) {
        if (key == null) return null;
        DataConversion keyDataConversion = listenerInvocation.getKeyDataConversion();
        Wrapper wrp = keyDataConversion.getWrapper();
        Object unwrappedKey = keyDataConversion.getEncoder().fromStorage(wrp.unwrap(key));
        CacheEventFilter filter = listenerInvocation.getFilter();
        CacheEventConverter converter = listenerInvocation.getConverter();
        if (filter == null && converter == null) {
            if (listenerInvocation.useStorageFormat()) {
                return (K) unwrappedKey;
            }
            // If no filter is present, convert to the requested format directly
            return (K) keyDataConversion.fromStorage(key);
        }
        MediaType convertFormat = filter == null ? converter.format() : filter.format();
        if (listenerInvocation.useStorageFormat() || convertFormat == null) {
            // Filter will be run on the storage format, return the unwrapped key
            return (K) unwrappedKey;
        }

        // Filter has a specific format to run, convert to that format
        return (K) encoderRegistry.convert(unwrappedKey, keyDataConversion.getStorageMediaType(), convertFormat);
    }

    private void setSource(EventImpl<K, V> e, InvocationContext ctx, FlagAffectedCommand command) {
        if (ctx != null && ctx.isInTxScope()) {
            GlobalTransaction tx = ((TxInvocationContext) ctx).getGlobalTransaction();
            e.setSource(tx);
        } else if (command instanceof WriteCommand) {
            CommandInvocationId invocationId = ((WriteCommand) command).getCommandInvocationId();
            e.setSource(invocationId);
        }
    }

    private V convertValue(CacheEntryListenerInvocation listenerInvocation, V value, EncoderRegistry encoderRegistry) {
        if (value == null) return null;
        DataConversion valueDataConversion = listenerInvocation.getValueDataConversion();
        Wrapper wrp = valueDataConversion.getWrapper();
        Object unwrappedValue = valueDataConversion.getEncoder().fromStorage(wrp.unwrap(value));
        CacheEventFilter filter = listenerInvocation.getFilter();
        CacheEventConverter converter = listenerInvocation.getConverter();
        if (filter == null && converter == null) {
            if (listenerInvocation.useStorageFormat()) {
                return (V) unwrappedValue;
            }
            // If no filter is present, convert to the requested format directly
            return (V) valueDataConversion.fromStorage(value);
        }
        MediaType convertFormat = filter == null ? converter.format() : filter.format();
        if (listenerInvocation.useStorageFormat() || convertFormat == null) {
            // Filter will be run on the storage format, return the unwrapped key
            return (V) unwrappedValue;
        }
        // Filter has a specific format to run, convert to that format
        return (V) encoderRegistry.convert(unwrappedValue, valueDataConversion.getStorageMediaType(), convertFormat);
    }
}

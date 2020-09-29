/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * ThreadLocal的一个特殊变体，当从FastThreadLocalThread访问时可产生更高的【访问】性能。
 *
 * 在内部，FastThreadLocal使用数组中的常量索引而不是使用 hash code 和 hash table 来查找变量。
 * 尽管看似非常微妙，但与使用哈希表相比，它在性能上却有一点优势，并且在经常访问时很有用。
 *
 * 要利用此线程局部变量，您的线程必须是FastThreadLocalThread或其子类型。
 * 由于这个原因，默认情况下，DefaultThreadFactory创建的所有线程均为FastThreadLocalThread。
 *
 * 请注意，只有在扩展FastThreadLocalThread的线程上才可以使用快速路径，因为它需要一个特殊的字段来存储必要的状态。
 * 任何其他类型的线程的访问都会回退到常规ThreadLocal。
 */

// 普通线程用这个有点亏吧，本来直接从 ThreadLocal 中 hash 找到就行了。现在还要进行一次数组的取值，虽然复杂度是O(1)

/**
 * A special variant of {@link ThreadLocal} that yields higher access performance when accessed from a
 * {@link FastThreadLocalThread}.
 * <p>
 * Internally, a {@link FastThreadLocal} uses a constant index in an array, instead of using hash code and hash table,
 * to look for a variable.  Although seemingly very subtle, it yields slight performance advantage over using a hash
 * table, and it is useful when accessed frequently.
 * </p><p>
 * To take advantage of this thread-local variable, your thread must be a {@link FastThreadLocalThread} or its subtype.
 * By default, all threads created by {@link DefaultThreadFactory} are {@link FastThreadLocalThread} due to this reason.
 * </p><p>
 * Note that the fast path is only possible on threads that extend {@link FastThreadLocalThread}, because it requires
 * a special field to store the necessary state.  An access by any other kind of thread falls back to a regular
 * {@link ThreadLocal}.
 * </p>
 *
 * @param <V> the type of the thread-local variable
 * @see ThreadLocal
 */
public class FastThreadLocal<V> {

    // 所有的 FastThreadLocal 都共用这个索引。这是 InternalThreadLocalMap 中的 Object[] 数组中的第一个位子。
    // 这个应该是用于 removeAll() 方法，不需要遍历当前线程的 InternalThreadLocalMap 中的 Object[] 数组。
    private static final int variablesToRemoveIndex = InternalThreadLocalMap.nextVariableIndex();

    /**
     * Removes all {@link FastThreadLocal} variables bound to the current thread.  This operation is useful when you
     * are in a container environment, and you don't want to leave the thread local variables in the threads you do not
     * manage.
     */
    // 将 Map 中的所有 value，都替换成 UNSET
    public static void removeAll() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            return;
        }

        try {
            Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
            if (v != null && v != InternalThreadLocalMap.UNSET) {
                @SuppressWarnings("unchecked")
                Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
                FastThreadLocal<?>[] variablesToRemoveArray =
                        variablesToRemove.toArray(new FastThreadLocal[0]);
                for (FastThreadLocal<?> tlv: variablesToRemoveArray) {
                    tlv.remove(threadLocalMap);
                }
            }
        } finally {
            InternalThreadLocalMap.remove();
        }
    }

    /**
     * Returns the number of thread local variables bound to the current thread.
     */
    public static int size() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            return 0;
        } else {
            return threadLocalMap.size();
        }
    }

    /**
     * Destroys the data structure that keeps all {@link FastThreadLocal} variables accessed from
     * non-{@link FastThreadLocalThread}s.  This operation is useful when you are in a container environment, and you
     * do not want to leave the thread local variables in the threads you do not manage.  Call this method when your
     * application is being unloaded from the container.
     */
    public static void destroy() {
        InternalThreadLocalMap.destroy();
    }

    @SuppressWarnings("unchecked")
    private static void addToVariablesToRemove(InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {
        // 从 threadLocalMap 中取一个 Set，不存在则新建并设置到该索引位
        Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
        Set<FastThreadLocal<?>> variablesToRemove;
        if (v == InternalThreadLocalMap.UNSET || v == null) {
            variablesToRemove = Collections.newSetFromMap(new IdentityHashMap<FastThreadLocal<?>, Boolean>());
            threadLocalMap.setIndexedVariable(variablesToRemoveIndex, variablesToRemove);
        } else {
            variablesToRemove = (Set<FastThreadLocal<?>>) v;
        }

        variablesToRemove.add(variable);
    }

    // 从 Set 中删除当前 FastThreadLocal
    private static void removeFromVariablesToRemove(
            InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {

        Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);

        if (v == InternalThreadLocalMap.UNSET || v == null) {
            return;
        }

        // 从当前线程的 Set 中移除这个 FastThreadLocal
        @SuppressWarnings("unchecked")
        Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
        variablesToRemove.remove(variable);
    }

    private final int index;

    public FastThreadLocal() {
        // 这个 FastThreadLocal 记录的变量，在数组中的索引位置，构造完就固定下来了，看 index 是 final 的。
        index = InternalThreadLocalMap.nextVariableIndex();
    }

    /**
     * Returns the current value for the current thread
     */
    @SuppressWarnings("unchecked")
    public final V get() {
        // 获取该线程关联的 InternalThreadLocalMap，如果当前线程是 FastThreadLocalThread，则基本没成本。
        // 如果是普通的 Thread，那么取关联的 InternalThreadLocalMap，会有一次 ThreadLocal 的 hash表 查找操作，会慢一点。
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
        // 获取数组中的值
        Object v = threadLocalMap.indexedVariable(index);
        if (v != InternalThreadLocalMap.UNSET) {
            // 如果该线程设置过这个索引位的值，则返回
            return (V) v;
        }

        // 走到这里，说明数组中该索引的位置为 UNSET，初始化一个值
        V value = initialize(threadLocalMap);
        // 不用管，已废弃
        registerCleaner(threadLocalMap);
        return value;
    }

    // todo：这个清理被废弃了
    private void registerCleaner(final InternalThreadLocalMap threadLocalMap) {
        Thread current = Thread.currentThread();
        if (FastThreadLocalThread.willCleanupFastThreadLocals(current) || threadLocalMap.isCleanerFlagSet(index)) {
            return;
        }

        threadLocalMap.setCleanerFlag(index);

        // TODO: We need to find a better way to handle this.
        /*
        // We will need to ensure we will trigger remove(InternalThreadLocalMap) so everything will be released
        // and FastThreadLocal.onRemoval(...) will be called.
        ObjectCleaner.register(current, new Runnable() {
            @Override
            public void run() {
                remove(threadLocalMap);

                // It's fine to not call InternalThreadLocalMap.remove() here as this will only be triggered once
                // the Thread is collected by GC. In this case the ThreadLocal will be gone away already.
            }
        });
        */
    }

    /**
     * Returns the current value for the specified thread local map.
     * The specified thread local map must be for the current thread.
     */
    @SuppressWarnings("unchecked")
    public final V get(InternalThreadLocalMap threadLocalMap) {
        Object v = threadLocalMap.indexedVariable(index);
        if (v != InternalThreadLocalMap.UNSET) {
            return (V) v;
        }

        return initialize(threadLocalMap);
    }

    private V initialize(InternalThreadLocalMap threadLocalMap) {
        V v = null;
        try {
            // 初始化一个值，默认是null，由使用方实现 initialValue 方法
            v = initialValue();
        } catch (Exception e) {
            PlatformDependent.throwException(e);
        }

        // 设置该值
        threadLocalMap.setIndexedVariable(index, v);
        // 将这个 FastThreadLocal 添加到待删除的 Set 中
        addToVariablesToRemove(threadLocalMap, this);
        return v;
    }

    /**
     * Set the value for the current thread.
     */
    public final void set(V value) {
        if (value != InternalThreadLocalMap.UNSET) {
            InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
            if (setKnownNotUnset(threadLocalMap, value)) {  // 设置 value
                // 不用管，废弃了
                registerCleaner(threadLocalMap);
            }
        } else {
            // 如果设置的值是 UNSET，则删除？
            remove();
        }
    }

    /**
     * Set the value for the specified thread local map. The specified thread local map must be for the current thread.
     */
    public final void set(InternalThreadLocalMap threadLocalMap, V value) {
        if (value != InternalThreadLocalMap.UNSET) {
            setKnownNotUnset(threadLocalMap, value);
        } else {
            remove(threadLocalMap);
        }
    }

    /**
     * @return see {@link InternalThreadLocalMap#setIndexedVariable(int, Object)}.
     */
    private boolean setKnownNotUnset(InternalThreadLocalMap threadLocalMap, V value) {
        if (threadLocalMap.setIndexedVariable(index, value)) {
            // 走到这里，说明老值是 UNSET，就是说设置了新值
            // 添加到待移除的 Set 上
            addToVariablesToRemove(threadLocalMap, this);
            return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if and only if this thread-local variable is set.
     */
    public final boolean isSet() {
        return isSet(InternalThreadLocalMap.getIfSet());
    }

    /**
     * Returns {@code true} if and only if this thread-local variable is set.
     * The specified thread local map must be for the current thread.
     */
    public final boolean isSet(InternalThreadLocalMap threadLocalMap) {
        return threadLocalMap != null && threadLocalMap.isIndexedVariableSet(index);
    }
    /**
     * Sets the value to uninitialized; a proceeding call to get() will trigger a call to initialValue().
     * 将值设置为未初始化；继续调用get()将触发对initialValue()的调用。
     */
    public final void remove() {
        //
        remove(InternalThreadLocalMap.getIfSet());
    }

    /**
     * Sets the value to uninitialized for the specified thread local map;
     * a proceeding call to get() will trigger a call to initialValue().
     * The specified thread local map must be for the current thread.
     */
    @SuppressWarnings("unchecked")
    public final void remove(InternalThreadLocalMap threadLocalMap) {
        if (threadLocalMap == null) {
            return;
        }

        // 将这个 index 的位子，设置成 UNSET
        Object v = threadLocalMap.removeIndexedVariable(index);
        removeFromVariablesToRemove(threadLocalMap, this);

        if (v != InternalThreadLocalMap.UNSET) {
            try {
                // 默认什么都不干，可重写
                onRemoval((V) v);
            } catch (Exception e) {
                PlatformDependent.throwException(e);
            }
        }
    }

    /**
     * Returns the initial value for this thread-local variable.
     */
    protected V initialValue() throws Exception {
        return null;
    }

    /**
     * Invoked when this thread local variable is removed by {@link #remove()}. Be aware that {@link #remove()}
     * is not guaranteed to be called when the `Thread` completes which means you can not depend on this for
     * cleanup of the resources in the case of `Thread` completion.
     */
    protected void onRemoval(@SuppressWarnings("UnusedParameters") V value) throws Exception { }
}

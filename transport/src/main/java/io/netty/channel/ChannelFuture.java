/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.util.concurrent.BlockingOperationException;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;

/**
 * 异步的通道I/O操作的结果。
 *
 * Netty中的所有I/O操作都是异步的。这意味着任何I/O调用都将立即返回，而不能保证所请求的I/O操作在调用结束时已完成。
 * 相反，将返回一个ChannelFuture实例，该实例为您提供有关I/O操作的结果或状态的信息。
 *
 * ChannelFuture未完成或已完成。 I/O操作开始时，将创建一个新的将来对象。
 * 新的future最初并未完成-因为I/O操作尚未完成，所以既没有成功，没有失败也没有取消。
 * 如果I/O操作成功完成，失败或通过取消完成，则将来标记为已完成，其中包含更多特定信息，例如失败原因。
 * 请注意，即使失败和取消也属于完成状态。
 *                                      +---------------------------+
 *                                      | Completed successfully    |
 *                                      +---------------------------+
 *                                 +---->      isDone() = true      |
 * +--------------------------+    |    |   isSuccess() = true      |
 * |        Uncompleted       |    |    +===========================+
 * +--------------------------+    |    | Completed with failure    |
 * |      isDone() = false    |    |    +---------------------------+
 * |   isSuccess() = false    |----+---->      isDone() = true      |
 * | isCancelled() = false    |    |    |       cause() = non-null  |
 * |       cause() = null     |    |    +===========================+
 * +--------------------------+    |    | Completed by cancellation |
 *                                 |    +---------------------------+
 *                                 +---->      isDone() = true      |
 *                                      | isCancelled() = true      |
 *                                      +---------------------------+
 *
 * 提供了各种方法来让您检查I/O操作是否已完成，等待完成以及获取I/O操作的结果。
 * 它还允许您添加ChannelFutureListeners，以便在I/O操作完成时得到通知。
 *
 * - 首选addListener(GenericFutureListener)代替await()
 * 建议在可能的情况下，将addListener(GenericFutureListener)首选为await()，以便在完成I/O操作并执行任何后续任务时得到通知。
 * addListener(GenericFutureListener)是非阻塞的。
 * 它只是将指定的ChannelFutureListener添加到ChannelFuture，并且与将来关联的I/O操作完成时，I/O线程将通知侦听器。
 * ChannelFutureListener完全不阻塞，因此可以产生最佳的性能和资源利用率，但是如果您不习惯事件驱动的编程，则实现顺序逻辑可能会比较棘手。
 * 相反，await()是阻塞操作。一旦被调用，调用者线程将阻塞直到操作完成。
 * 使用await()实现顺序逻辑更容易，但是调用者线程会不必要地阻塞，直到完成I/O操作并且线程间通知的成本相对较高为止。
 * 此外，在特定情况下还可能出现死锁，这将在下面进行描述。
 *
 * - 不要在ChannelHandler中调用await()
 * ChannelHandler中的事件处理程序方法通常由I/O线程调用。
 * 如果aevent()是由I/O线程调用的事件处理程序方法调用的，   (todo：这里翻的有问题)
 * 则它正在等待的I/O操作可能永远不会完成，因为await()会阻塞它正在等待的I/O操作，这是一个死锁。
 *
 *    // BAD - NEVER DO THIS
 *     @Override
 *    public void channelRead(ChannelHandlerContext ctx, Object msg) {
 *        ChannelFuture future = ctx.channel().close();
 *        future.awaitUninterruptibly();
 *        // Perform post-closure operation
 *        // ...
 *    }
 *
 *    // GOOD
 *     @Override
 *    public void channelRead(ChannelHandlerContext ctx, Object msg) {
 *        ChannelFuture future = ctx.channel().close();
 *        future.addListener(new ChannelFutureListener() {
 *            public void operationComplete(ChannelFuture future) {
 *                // Perform post-closure operation
 *                // ...
 *            }
 *        });
 *    }
 *
 * 尽管有上述缺点，但是在某些情况下，调用await()更方便。
 * 在这种情况下，请确保不要在I/O线程中调用await()。
 * 否则，将引发BlockingOperationException以防止死锁。
 *
 *
 * - 不要混淆I/O超时和等待超时
 * 您使用await(long)，await(long，TimeUnit)，awaitUninterruptible(long)或awaitUninterruptible(long，TimeUnit)指定的超时值
 * 与I/O超时根本不相关。如果I/O操作超时，则将来将标记为“失败完成”，如上图所示。例如，应通过特定于传输的选项配置连接超时：
 *
 *    // BAD - NEVER DO THIS
 *    Bootstrap b = ...;
 *    ChannelFuture f = b.connect(...);
 *    f.awaitUninterruptibly(10, TimeUnit.SECONDS);
 *    if (f.isCancelled()) {
 *        // Connection attempt cancelled by user
 *    } else if (!f.isSuccess()) {
 *        // You might get a NullPointerException here because the future
 *        // might not be completed yet.
 *        f.cause().printStackTrace();
 *    } else {
 *        // Connection established successfully
 *    }
 *
 *    // GOOD
 *    Bootstrap b = ...;
 *    // Configure the connect timeout option.
 *    b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
 *    ChannelFuture f = b.connect(...);
 *    f.awaitUninterruptibly();
 *
 *    // Now we are sure the future is completed.
 *    assert f.isDone();
 *
 *    if (f.isCancelled()) {
 *        // Connection attempt cancelled by user
 *    } else if (!f.isSuccess()) {
 *        f.cause().printStackTrace();
 *    } else {
 *        // Connection established successfully
 *    }
 */

/**
 * The result of an asynchronous {@link Channel} I/O operation.
 * <p>
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link ChannelFuture} instance which gives you the information about the
 * result or status of the I/O operation.
 * <p>
 * A {@link ChannelFuture} is either <em>uncompleted</em> or <em>completed</em>.
 * When an I/O operation begins, a new future object is created.  The new future
 * is uncompleted initially - it is neither succeeded, failed, nor cancelled
 * because the I/O operation is not finished yet.  If the I/O operation is
 * finished either successfully, with failure, or by cancellation, the future is
 * marked as completed with more specific information, such as the cause of the
 * failure.  Please note that even failure and cancellation belong to the
 * completed state.
 * <pre>
 *                                      +---------------------------+
 *                                      | Completed successfully    |
 *                                      +---------------------------+
 *                                 +---->      isDone() = true      |
 * +--------------------------+    |    |   isSuccess() = true      |
 * |        Uncompleted       |    |    +===========================+
 * +--------------------------+    |    | Completed with failure    |
 * |      isDone() = false    |    |    +---------------------------+
 * |   isSuccess() = false    |----+---->      isDone() = true      |
 * | isCancelled() = false    |    |    |       cause() = non-null  |
 * |       cause() = null     |    |    +===========================+
 * +--------------------------+    |    | Completed by cancellation |
 *                                 |    +---------------------------+
 *                                 +---->      isDone() = true      |
 *                                      | isCancelled() = true      |
 *                                      +---------------------------+
 * </pre>
 *
 * Various methods are provided to let you check if the I/O operation has been
 * completed, wait for the completion, and retrieve the result of the I/O
 * operation. It also allows you to add {@link ChannelFutureListener}s so you
 * can get notified when the I/O operation is completed.
 *
 * <h3>Prefer {@link #addListener(GenericFutureListener)} to {@link #await()}</h3>
 *
 * It is recommended to prefer {@link #addListener(GenericFutureListener)} to
 * {@link #await()} wherever possible to get notified when an I/O operation is
 * done and to do any follow-up tasks.
 * <p>
 * {@link #addListener(GenericFutureListener)} is non-blocking.  It simply adds
 * the specified {@link ChannelFutureListener} to the {@link ChannelFuture}, and
 * I/O thread will notify the listeners when the I/O operation associated with
 * the future is done.  {@link ChannelFutureListener} yields the best
 * performance and resource utilization because it does not block at all, but
 * it could be tricky to implement a sequential logic if you are not used to
 * event-driven programming.
 * <p>
 * By contrast, {@link #await()} is a blocking operation.  Once called, the
 * caller thread blocks until the operation is done.  It is easier to implement
 * a sequential logic with {@link #await()}, but the caller thread blocks
 * unnecessarily until the I/O operation is done and there's relatively
 * expensive cost of inter-thread notification.  Moreover, there's a chance of
 * dead lock in a particular circumstance, which is described below.
 *
 * <h3>Do not call {@link #await()} inside {@link ChannelHandler}</h3>
 * <p>
 * The event handler methods in {@link ChannelHandler} are usually called by
 * an I/O thread.  If {@link #await()} is called by an event handler
 * method, which is called by the I/O thread, the I/O operation it is waiting
 * for might never complete because {@link #await()} can block the I/O
 * operation it is waiting for, which is a dead lock.
 * <pre>
 * // BAD - NEVER DO THIS
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.awaitUninterruptibly();
 *     // Perform post-closure operation
 *     // ...
 * }
 *
 * // GOOD
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.addListener(new {@link ChannelFutureListener}() {
 *         public void operationComplete({@link ChannelFuture} future) {
 *             // Perform post-closure operation
 *             // ...
 *         }
 *     });
 * }
 * </pre>
 * <p>
 * In spite of the disadvantages mentioned above, there are certainly the cases
 * where it is more convenient to call {@link #await()}. In such a case, please
 * make sure you do not call {@link #await()} in an I/O thread.  Otherwise,
 * {@link BlockingOperationException} will be raised to prevent a dead lock.
 *
 * <h3>Do not confuse I/O timeout and await timeout</h3>
 *
 * The timeout value you specify with {@link #await(long)},
 * {@link #await(long, TimeUnit)}, {@link #awaitUninterruptibly(long)}, or
 * {@link #awaitUninterruptibly(long, TimeUnit)} are not related with I/O
 * timeout at all.  If an I/O operation times out, the future will be marked as
 * 'completed with failure,' as depicted in the diagram above.  For example,
 * connect timeout should be configured via a transport-specific option:
 * <pre>
 * // BAD - NEVER DO THIS
 * {@link Bootstrap} b = ...;
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly(10, TimeUnit.SECONDS);
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     // You might get a NullPointerException here because the future
 *     // might not be completed yet.
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 *
 * // GOOD
 * {@link Bootstrap} b = ...;
 * // Configure the connect timeout option.
 * <b>b.option({@link ChannelOption}.CONNECT_TIMEOUT_MILLIS, 10000);</b>
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly();
 *
 * // Now we are sure the future is completed.
 * assert f.isDone();
 *
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 * </pre>
 */
public interface ChannelFuture extends Future<Void> {

    /**
     * Returns a channel where the I/O operation associated with this
     * future takes place.
     * （译：返回与此future关联的I/O操作发生的channel。）
     */
    Channel channel();

    @Override
    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture sync() throws InterruptedException;

    @Override
    ChannelFuture syncUninterruptibly();

    @Override
    ChannelFuture await() throws InterruptedException;

    @Override
    ChannelFuture awaitUninterruptibly();

    /**
     * Returns {@code true} if this {@link ChannelFuture} is a void future and so not allow to call any of the
     * following methods:
     * <ul>
     *     <li>{@link #addListener(GenericFutureListener)}</li>
     *     <li>{@link #addListeners(GenericFutureListener[])}</li>
     *     <li>{@link #await()}</li>
     *     <li>{@link #await(long, TimeUnit)} ()}</li>
     *     <li>{@link #await(long)} ()}</li>
     *     <li>{@link #awaitUninterruptibly()}</li>
     *     <li>{@link #sync()}</li>
     *     <li>{@link #syncUninterruptibly()}</li>
     * </ul>
     */
    boolean isVoid();
}

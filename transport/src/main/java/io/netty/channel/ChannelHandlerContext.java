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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import io.netty.util.concurrent.EventExecutor;

import java.nio.channels.Channels;

/**
 * 使ChannelHandler与其ChannelPipeline和其他handlers进行交互。
 * 处理程序除其他外，可以通知ChannelPipeline中的下一个ChannelHandler以及动态修改其所属的ChannelPipeline。
 *
 * - 通知
 * 您可以通过调用此处提供的各种方法之一来通知同一ChannelPipeline中最接近的处理程序。请参考ChannelPipeline以了解事件的流向。
 *
 * - 修改pipeline
 * 您可以通过调用pipeline()来获取handler所属的ChannelPipeline。
 * 一个非平凡的应用程序可以在运行时动态地在pipeline中插入，删除或替换handler。
 *
 * - 检索以备后用
 * 您可以保留ChannelHandlerContext供以后使用，例如在处理程序方法之外触发事件，即使是从其他线程触发也是如此。
 *    public class MyHandler extends ChannelDuplexHandler {
 *
 *        private ChannelHandlerContext ctx;
 *
 *        public void beforeAdd(ChannelHandlerContext ctx) {
 *            this.ctx = ctx;
 *        }
 *
 *        public void login(String username, password) {
 *            ctx.write(new LoginMessage(username, password));
 *        }
 *        ...
 *    }
 *
 * - 存储状态信息
 * attr(AttributeKey)允许您存储和访问与处理程序及其上下文相关的有状态信息。
 * 请参考ChannelHandler来学习各种建议的管理状态信息的方法。
 *
 * - 处理程序可以具有多个上下文
 * 请注意，可以将ChannelHandler实例添加到多个ChannelPipeline中。
 * 这意味着一个ChannelHandler实例可以具有多个ChannelHandlerContext，
 * 因此，如果将一个实例多次添加到一个或多个ChannelPipelines中，
 * 则可以使用不同的ChannelHandlerContext调用该实例。
 * 例如，以下处理程序将具有与添加到管道中的次数一样多的独立AttributeKey，无论它是多次添加到同一管道还是多次添加到不同的管道：
 *    public class FactorialHandler extends ChannelInboundHandlerAdapter {
 *
 *      private final AttributeKey<Integer> counter = AttributeKey.valueOf("counter");
 *
 *      // This handler will receive a sequence of increasing integers starting
 *      // from 1.
 *       @Override
 *      public void channelRead(ChannelHandlerContext ctx, Object msg) {
 *        Integer a = ctx.attr(counter).get();
 *
 *        if (a == null) {
 *          a = 1;
 *        }
 *
 *        attr.set(a * (Integer) msg);
 *      }
 *    }
 *
 *    // Different context objects are given to "f1", "f2", "f3", and "f4" even if
 *    // they refer to the same handler instance.  Because the FactorialHandler
 *    // stores its state in a context object (using an AttributeKey), the factorial is
 *    // calculated correctly 4 times once the two pipelines (p1 and p2) are active.
 *    FactorialHandler fh = new FactorialHandler();
 *
 *    ChannelPipeline p1 = Channels.pipeline();
 *    p1.addLast("f1", fh);
 *    p1.addLast("f2", fh);
 *
 *    ChannelPipeline p2 = Channels.pipeline();
 *    p2.addLast("f3", fh);
 *    p2.addLast("f4", fh);
 *
 * - 值得阅读的其他资源
 * 请参考ChannelHandler和ChannelPipeline，以了解有关入站和出站操作，
 * 它们之间有哪些根本区别，它们如何在管道中流动以及如何在应用程序中处理该操作的更多信息。
 */

/**
 * Enables a {@link ChannelHandler} to interact with its {@link ChannelPipeline}
 * and other handlers. Among other things a handler can notify the next {@link ChannelHandler} in the
 * {@link ChannelPipeline} as well as modify the {@link ChannelPipeline} it belongs to dynamically.
 *
 * <h3>Notify</h3>
 *
 * You can notify the closest handler in the same {@link ChannelPipeline} by calling one of the various methods
 * provided here.
 *
 * Please refer to {@link ChannelPipeline} to understand how an event flows.
 *
 * <h3>Modifying a pipeline</h3>
 *
 * You can get the {@link ChannelPipeline} your handler belongs to by calling
 * {@link #pipeline()}.  A non-trivial application could insert, remove, or
 * replace handlers in the pipeline dynamically at runtime.
 *
 * <h3>Retrieving for later use</h3>
 *
 * You can keep the {@link ChannelHandlerContext} for later use, such as
 * triggering an event outside the handler methods, even from a different thread.
 * <pre>
 * public class MyHandler extends {@link ChannelDuplexHandler} {
 *
 *     <b>private {@link ChannelHandlerContext} ctx;</b>
 *
 *     public void beforeAdd({@link ChannelHandlerContext} ctx) {
 *         <b>this.ctx = ctx;</b>
 *     }
 *
 *     public void login(String username, password) {
 *         ctx.write(new LoginMessage(username, password));
 *     }
 *     ...
 * }
 * </pre>
 *
 * <h3>Storing stateful information</h3>
 *
 * {@link #attr(AttributeKey)} allow you to
 * store and access stateful information that is related with a handler and its
 * context.  Please refer to {@link ChannelHandler} to learn various recommended
 * ways to manage stateful information.
 *
 * <h3>A handler can have more than one context</h3>
 *
 * Please note that a {@link ChannelHandler} instance can be added to more than
 * one {@link ChannelPipeline}.  It means a single {@link ChannelHandler}
 * instance can have more than one {@link ChannelHandlerContext} and therefore
 * the single instance can be invoked with different
 * {@link ChannelHandlerContext}s if it is added to one or more
 * {@link ChannelPipeline}s more than once.
 * <p>
 * For example, the following handler will have as many independent {@link AttributeKey}s
 * as how many times it is added to pipelines, regardless if it is added to the
 * same pipeline multiple times or added to different pipelines multiple times:
 * <pre>
 * public class FactorialHandler extends {@link ChannelInboundHandlerAdapter} {
 *
 *   private final {@link AttributeKey}&lt;{@link Integer}&gt; counter = {@link AttributeKey}.valueOf("counter");
 *
 *   // This handler will receive a sequence of increasing integers starting
 *   // from 1.
 *   {@code @Override}
 *   public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     Integer a = ctx.attr(counter).get();
 *
 *     if (a == null) {
 *       a = 1;
 *     }
 *
 *     attr.set(a * (Integer) msg);
 *   }
 * }
 *
 * // Different context objects are given to "f1", "f2", "f3", and "f4" even if
 * // they refer to the same handler instance.  Because the FactorialHandler
 * // stores its state in a context object (using an {@link AttributeKey}), the factorial is
 * // calculated correctly 4 times once the two pipelines (p1 and p2) are active.
 * FactorialHandler fh = new FactorialHandler();
 *
 * {@link ChannelPipeline} p1 = {@link Channels}.pipeline();
 * p1.addLast("f1", fh);
 * p1.addLast("f2", fh);
 *
 * {@link ChannelPipeline} p2 = {@link Channels}.pipeline();
 * p2.addLast("f3", fh);
 * p2.addLast("f4", fh);
 * </pre>
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 */
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {

    /**
     * Return the {@link Channel} which is bound to the {@link ChannelHandlerContext}.
     */
    // 绑定在该上下文中的Channel
    Channel channel();

    /**
     * Returns the {@link EventExecutor} which is used to execute an arbitrary task.
     */
    EventExecutor executor();

    /**
     * The unique name of the {@link ChannelHandlerContext}.The name was used when then {@link ChannelHandler}
     * was added to the {@link ChannelPipeline}. This name can also be used to access the registered
     * {@link ChannelHandler} from the {@link ChannelPipeline}.
     */
    String name();

    /**
     * The {@link ChannelHandler} that is bound this {@link ChannelHandlerContext}.
     */
    // 绑定在该上下文中的ChannelHandler
    ChannelHandler handler();

    /**
     * Return {@code true} if the {@link ChannelHandler} which belongs to this context was removed
     * from the {@link ChannelPipeline}. Note that this method is only meant to be called from with in the
     * {@link EventLoop}.
     */
    boolean isRemoved();

    @Override
    ChannelHandlerContext fireChannelRegistered();

    @Override
    ChannelHandlerContext fireChannelUnregistered();

    @Override
    ChannelHandlerContext fireChannelActive();

    @Override
    ChannelHandlerContext fireChannelInactive();

    @Override
    ChannelHandlerContext fireExceptionCaught(Throwable cause);

    @Override
    ChannelHandlerContext fireUserEventTriggered(Object evt);

    @Override
    ChannelHandlerContext fireChannelRead(Object msg);

    @Override
    ChannelHandlerContext fireChannelReadComplete();

    @Override
    ChannelHandlerContext fireChannelWritabilityChanged();

    @Override
    ChannelHandlerContext read();

    @Override
    ChannelHandlerContext flush();

    /**
     * Return the assigned {@link ChannelPipeline}
     */
    // 返回关联的ChannelPipeline
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     */
    ByteBufAllocator alloc();

    /**
     * @deprecated Use {@link Channel#attr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> Attribute<T> attr(AttributeKey<T> key);

    /**
     * @deprecated Use {@link Channel#hasAttr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> boolean hasAttr(AttributeKey<T> key);
}

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

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 处理I/O事件或拦截I/O操作，并将其转发到其ChannelPipeline中的下一个handler。
 *
 * 子类型
 * ChannelHandler本身不提供许多方法，但是通常你必须实现其子类型之一：
 * ChannelInboundHandler处理输入的I/O事件
 * ChannelOutboundHandler处理输出的I/O操作
 *
 * 另外，为了您的方便，提供了以下适配器类：
 * ChannelInboundHandlerAdapter处理输入的I/O事件
 * ChannelOutboundHandlerAdapter处理输出的I/O操作
 * ChannelDuplexHandler处理输入、输出事件
 *
 * 有关更多信息，请参阅每个子类型的文档。
 *
 * 上下文对象
 * ChannelHandler随ChannelHandlerContext对象一起提供。
 * ChannelHandler应该通过上下文对象与其所属的ChannelPipeline进行交互。
 * 使用上下文对象，ChannelHandler可以在上游或下游传递事件，动态修改pipeline或存储特定于handler的信息（使用AttributeKeys）。
 *
 * 状态管理
 * ChannelHandler通常需要存储一些状态信息。推荐的最简单方法是使用成员变量：
 *    public interface Message {
 *        // your methods here
 *    }
 *
 *    public class DataServerHandler extends SimpleChannelInboundHandler<Message> {
 *
 *        private boolean loggedIn;
 *
 *         @Override
 *        public void channelRead0(ChannelHandlerContext ctx, Message message) {
 *            if (message instanceof LoginMessage) {
 *                authenticate((LoginMessage) message);
 *                loggedIn = true;
 *            } else (message instanceof GetDataMessage) {
 *                if (loggedIn) {
 *                    ctx.writeAndFlush(fetchSecret((GetDataMessage) message));
 *                } else {
 *                    fail();
 *                }
 *            }
 *        }
 *        ...
 *    }
 *
 * 因为handler实例具有专用于一个连接的状态变量，
 * 所以您必须为每个新通道创建一个新的处理程序实例，
 * 以避免竞争状态，未经身份验证的客户端可以获取机密信息：
 *   // Create a new handler instance per channel.
 *    // See ChannelInitializer.initChannel(Channel).
 *    public class DataServerInitializer extends ChannelInitializer<Channel> {
 *         @Override
 *        public void initChannel(Channel channel) {
 *            channel.pipeline().addLast("handler", new DataServerHandler());
 *        }
 *    }
 *
 *
 * 使用AttributeKeys
 * 尽管建议使用成员变量来存储handler的状态，但是由于某些原因，您可能不想创建许多handler实例。
 * 在这种情况下，可以使用ChannelHandlerContext提供的AttributeKeys：
 *    public interface Message {
 *        // your methods here
 *    }
 *
 *     @Sharable
 *    public class DataServerHandler extends SimpleChannelInboundHandler<Message> {
 *        private final AttributeKey<Boolean> auth =
 *              AttributeKey.valueOf("auth");
 *
 *         @Override
 *        public void channelRead(ChannelHandlerContext ctx, Message message) {
 *            Attribute<Boolean> attr = ctx.attr(auth);
 *            if (message instanceof LoginMessage) {
 *                authenticate((LoginMessage) o);
 *                attr.set(true);
 *            } else (message instanceof GetDataMessage) {
 *                if (Boolean.TRUE.equals(attr.get())) {
 *                    ctx.writeAndFlush(fetchSecret((GetDataMessage) o));
 *                } else {
 *                    fail();
 *                }
 *            }
 *        }
 *        ...
 *    }
 *
 * 现在，handler的状态已附加到ChannelHandlerContext上，您可以将相同的handler实例添加到不同的管道中：
 *    public class DataServerInitializer extends ChannelInitializer<Channel> {
 *
 *        private static final DataServerHandler SHARED = new DataServerHandler();
 *
 *         @Override
 *        public void initChannel(Channel channel) {
 *            channel.pipeline().addLast("handler", SHARED);
 *        }
 *    }
 *
 *
 * @Sharable 注解
 * 在上面的使用AttributeKey的示例中，您可能已经注意到@Sharable注解。
 * 如果使用@Sharable注释对ChannelHandler进行注释，则意味着您可以只创建一次处理程序的实例，
 * 然后将其多次添加到一个或多个ChannelPipelines中，而不会出现竞争条件。
 * 如果未指定此注释，则每次将其添加到管道时都必须创建一个新的处理程序实例，因为它具有未共享的状态，例如成员变量。
 * 提供此注释是出于文档目的，就像JCIP注释一样
 *
 *
 * 值得阅读的其他资源
 * 请参考ChannelHandler和ChannelPipeline，以了解有关入站和出站操作，
 * 它们之间有哪些根本区别，它们如何在管道中流动以及如何在应用程序中处理该操作的更多信息。
 */

/**
 * Handles an I/O event or intercepts an I/O operation, and forwards it to its next handler in
 * its {@link ChannelPipeline}.
 *
 * <h3>Sub-types</h3>
 * <p>
 * {@link ChannelHandler} itself does not provide many methods, but you usually have to implement one of its subtypes:
 * <ul>
 * <li>{@link ChannelInboundHandler} to handle inbound I/O events, and</li>
 * <li>{@link ChannelOutboundHandler} to handle outbound I/O operations.</li>
 * </ul>
 * </p>
 * <p>
 * Alternatively, the following adapter classes are provided for your convenience:
 * <ul>
 * <li>{@link ChannelInboundHandlerAdapter} to handle inbound I/O events,</li>
 * <li>{@link ChannelOutboundHandlerAdapter} to handle outbound I/O operations, and</li>
 * <li>{@link ChannelDuplexHandler} to handle both inbound and outbound events</li>
 * </ul>
 * </p>
 * <p>
 * For more information, please refer to the documentation of each subtype.
 * </p>
 *
 * <h3>The context object</h3>
 * <p>
 * A {@link ChannelHandler} is provided with a {@link ChannelHandlerContext}
 * object.  A {@link ChannelHandler} is supposed to interact with the
 * {@link ChannelPipeline} it belongs to via a context object.  Using the
 * context object, the {@link ChannelHandler} can pass events upstream or
 * downstream, modify the pipeline dynamically, or store the information
 * (using {@link AttributeKey}s) which is specific to the handler.
 *
 * <h3>State management</h3>
 *
 * A {@link ChannelHandler} often needs to store some stateful information.
 * The simplest and recommended approach is to use member variables:
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *
 *     <b>private boolean loggedIn;</b>
 *
 *     {@code @Override}
 *     public void channelRead0({@link ChannelHandlerContext} ctx, Message message) {
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) message);
 *             <b>loggedIn = true;</b>
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>loggedIn</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) message));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Because the handler instance has a state variable which is dedicated to
 * one connection, you have to create a new handler instance for each new
 * channel to avoid a race condition where a unauthenticated client can get
 * the confidential information:
 * <pre>
 * // Create a new handler instance per channel.
 * // See {@link ChannelInitializer#initChannel(Channel)}.
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>new DataServerHandler()</b>);
 *     }
 * }
 *
 * </pre>
 *
 * <h4>Using {@link AttributeKey}s</h4>
 *
 * Although it's recommended to use member variables to store the state of a
 * handler, for some reason you might not want to create many handler instances.
 * In such a case, you can use {@link AttributeKey}s which is provided by
 * {@link ChannelHandlerContext}:
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *     private final {@link AttributeKey}&lt;{@link Boolean}&gt; auth =
 *           {@link AttributeKey#valueOf(String) AttributeKey.valueOf("auth")};
 *
 *     {@code @Override}
 *     public void channelRead({@link ChannelHandlerContext} ctx, Message message) {
 *         {@link Attribute}&lt;{@link Boolean}&gt; attr = ctx.attr(auth);
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>attr.set(true)</b>;
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>Boolean.TRUE.equals(attr.get())</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Now that the state of the handler is attached to the {@link ChannelHandlerContext}, you can add the
 * same handler instance to different pipelines:
 * <pre>
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *
 *     private static final DataServerHandler <b>SHARED</b> = new DataServerHandler();
 *
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>SHARED</b>);
 *     }
 * }
 * </pre>
 *
 *
 * <h4>The {@code @Sharable} annotation</h4>
 * <p>
 * In the example above which used an {@link AttributeKey},
 * you might have noticed the {@code @Sharable} annotation.
 * <p>
 * If a {@link ChannelHandler} is annotated with the {@code @Sharable}
 * annotation, it means you can create an instance of the handler just once and
 * add it to one or more {@link ChannelPipeline}s multiple times without
 * a race condition.
 * <p>
 * If this annotation is not specified, you have to create a new handler
 * instance every time you add it to a pipeline because it has unshared state
 * such as member variables.
 * <p>
 * This annotation is provided for documentation purpose, just like
 * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 */
public interface ChannelHandler {

    /**
     * Gets called after the {@link ChannelHandler} was added to the actual context and it's ready to handle events.
     * （译：在将ChannelHandler添加到实际上下文中并可以处理事件后被调用。）
     */
    void handlerAdded(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called after the {@link ChannelHandler} was removed from the actual context and it doesn't handle events
     * anymore.
     * （译：从实际上下文中删除ChannelHandler之后，将调用它，并且不再处理事件。）
     */
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called if a {@link Throwable} was thrown.
     *
     * @deprecated is part of {@link ChannelInboundHandler}
     */
    @Deprecated
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    /**
     * Indicates that the same instance of the annotated {@link ChannelHandler}
     * can be added to one or more {@link ChannelPipeline}s multiple times
     * without a race condition.
     * <p>
     * If this annotation is not specified, you have to create a new handler
     * instance every time you add it to a pipeline because it has unshared
     * state such as member variables.
     * <p>
     * This annotation is provided for documentation purpose, just like
     * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
     */
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sharable {
        // no value
    }
}

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

import java.net.SocketAddress;

/**
 * {@link ChannelHandler} which will get notified for IO-outbound-operations.
 * （译：ChannelHandler，将通知IO出站操作。）
 */
public interface ChannelOutboundHandler extends ChannelHandler {

    /**
     * 进行绑定操作后被调用。
     *
     * @param ctx           对其执行绑定操作的ChannelHandlerContext
     * @param localAddress  它应该绑定到的SocketAddress
     * @param promise       操作完成后通知ChannelPromise
     * @throws Exception    发生错误时抛出
     */
    /**
     * Called once a bind operation is made.
     *
     * @param ctx           the {@link ChannelHandlerContext} for which the bind operation is made
     * @param localAddress  the {@link SocketAddress} to which it should bound
     * @param promise       the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception    thrown if an error occurs
     */
    void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception;

    /**
     * 进行连接操作后被调用。
     *
     * @param ctx               执行连接操作的ChannelHandlerContext
     * @param remoteAddress     它应该连接到的SocketAddress
     * @param localAddress      在连接上用作源的SocketAddress
     * @param promise           操作完成后通知ChannelPromise
     * @throws Exception        发生错误时抛出
     */
    /**
     * Called once a connect operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the connect operation is made
     * @param remoteAddress     the {@link SocketAddress} to which it should connect
     * @param localAddress      the {@link SocketAddress} which is used as source on connect
     * @param promise           the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception        thrown if an error occurs
     */
    void connect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelPromise promise) throws Exception;

    /**
     * 进行断开连接操作后被调用。
     *
     * @param ctx               对其执行断开操作的ChannelHandlerContext
     * @param promise           操作完成后通知ChannelPromise
     * @throws Exception        发生错误时抛出
     */
    /**
     * Called once a disconnect operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the disconnect operation is made
     * @param promise           the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception        thrown if an error occurs
     */
    void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    /**
     * 进行关闭操作后被调用。
     *
     * @param ctx               对其执行关闭操作的ChannelHandlerContext
     * @param promise           操作完成后通知ChannelPromise
     * @throws Exception        发生错误时抛出
     */
    /**
     * Called once a close operation is made.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the close operation is made
     * @param promise           the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception        thrown if an error occurs
     */
    void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    /**
     * 从当前已注册的EventLoop进行注销操作后调用。
     *
     * @param ctx               对其执行关闭操作的ChannelHandlerContext
     * @param promise           操作完成后通知ChannelPromise
     * @throws Exception        发生错误时抛出
     */
    /**
     * Called once a deregister operation is made from the current registered {@link EventLoop}.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the close operation is made
     * @param promise           the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception        thrown if an error occurs
     */
    void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception;

    /**
     * Intercepts {@link ChannelHandlerContext#read()}.
     * （译：拦截ChannelHandlerContext.read()）
     */
    void read(ChannelHandlerContext ctx) throws Exception;

    /**
     * 进行写操作后调用。写入操作将通过ChannelPipeline写入消息。
     * 一旦调用Channel.flush()，它们便准备好刷新到实际的Channel了。
     *
     * @param ctx               对其执行写操作的ChannelHandlerContext
     * @param msg               要写的消息
     * @param promise           操作完成后通知ChannelPromise
     * @throws Exception        发生错误时抛出
     */
    /**
     * Called once a write operation is made. The write operation will write the messages through the
     * {@link ChannelPipeline}. Those are then ready to be flushed to the actual {@link Channel} once
     * {@link Channel#flush()} is called
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the write operation is made
     * @param msg               the message to write
     * @param promise           the {@link ChannelPromise} to notify once the operation completes
     * @throws Exception        thrown if an error occurs
     */
    void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception;

    /**
     * 进行flush操作后调用。flush操作将尝试清除所有之前待处理的已写消息。
     *
     * @param ctx               对其执行flush操作的ChannelHandlerContext
     * @throws Exception        发生错误时抛出
     */
    /**
     * Called once a flush operation is made. The flush operation will try to flush out all previous written messages
     * that are pending.
     *
     * @param ctx               the {@link ChannelHandlerContext} for which the flush operation is made
     * @throws Exception        thrown if an error occurs
     */
    void flush(ChannelHandlerContext ctx) throws Exception;
}

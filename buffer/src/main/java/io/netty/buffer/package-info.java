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

/**
 * 字节缓冲区的抽象 - 表示低级二进制和文本消息的基本数据结构。
 *
 * Netty使用其自己的缓冲区API而不是NIO java.nio.ByteBuffer来表示字节序列。
 * 与使用java.nio.ByteBuffer相比，此方法具有明显的优势。
 * Netty的新缓冲区类型ByteBuf从头开始设计，以解决java.nio.ByteBuffer的问题并满足网络应用程序开发人员的日常需求。
 * 列出一些很酷的功能：
 * - 您可以根据需要定义缓冲区类型。
 * - 透明的零拷贝是通过内置的复合缓冲区类型实现的。
 * - 现成的动态缓冲区类型可以像StringBuffer一样按需扩展容量。
 * - 不再需要调用flip()方法。
 * - 它通常比java.nio.ByteBuffer快。
 *
 * 》》 可扩展性
 * ByteBuf具有针对快速协议实现进行优化的丰富操作集。
 * 例如，ByteBuf提供了各种操作，用于访问无符号值和字符串以及在缓冲区中搜索某些字节序列。
 * 您还可以扩展或包装现有的缓冲区类型以添加​​方便的访问器。
 * 自定义缓冲区类型仍然实现ByteBuf接口，而不是引入不兼容的类型。
 *
 *
 * 》》 透明零拷贝
 * 为了将网络应用程序的性能提升到极致，您需要减少内存复制操作的数量。
 * 您可能有一组缓冲区，可以对其进行切片和组合以组成整个消息。
 * Netty提供了一个复合缓冲区，使您可以从任意数量的现有缓冲区中创建一个新的缓冲区，而无需复制内存。
 * 例如，一条消息可以由两部分组成：标头和正文。在模块化应用程序中，这两个部分可以由不同的模块生产，并在稍后发送消息时组装。
 *    +--------+----------+
 *    | header |   body   |
 *    +--------+----------+
 * 如果使用java.nio.ByteBuffer，则必须创建一个新的大缓冲区，并将这两部分复制到新缓冲区中。
 * 另外，您可以在NIO中执行收集写操作，但是它限制您将缓冲区的组合表示为java.nio.ByteBuffers的数组而不是单个缓冲区，
 * 这破坏了抽象并引入了复杂的状态管理。而且，如果您不打算从NIO通道进行读取或写入，也没有用。
 *    // The composite type is incompatible with the component type.
 *    ByteBuffer[] message = new ByteBuffer[] { header, body };
 * 相比之下，ByteBuf没有这样的警告，因为它是完全可扩展的并且具有内置的复合缓冲区类型。
 *    // The composite type is compatible with the component type.
 *    ByteBuf message = Unpooled.wrappedBuffer(header, body);
 *
 *    // Therefore, you can even create a composite by mixing a composite and an
 *    // ordinary buffer.
 *    ByteBuf messageWithFooter = Unpooled.wrappedBuffer(message, footer);
 *
 *    // Because the composite is still a ByteBuf, you can access its content
 *    // easily, and the accessor method will behave just like it's a single buffer
 *    // even if the region you want to access spans over multiple components.  The
 *    // unsigned integer being read here is located across body and footer.
 *    messageWithFooter.getUnsignedInt(
 *        messageWithFooter.readableBytes() - footer.readableBytes() - 1);
 *
 *
 * 》》 自动扩容
 * 许多协议都定义了可变长度的消息，这意味着在构造消息之前或者精确地计算长度很困难且不方便之前，无法确定消息的长度。
 * 就像建立一个String一样。您通常会估计结果字符串的长度，并让StringBuffer根据需要扩展自身。
 *    // A new dynamic buffer is created.  Internally, the actual buffer is created
 *    // lazily to avoid potentially wasted memory space.
 *    ByteBuf b = Unpooled.buffer(4);
 *
 *    // When the first write attempt is made, the internal buffer is created with
 *    // the specified initial capacity (4).
 *    b.writeByte('1');
 *
 *    b.writeByte('2');
 *    b.writeByte('3');
 *    b.writeByte('4');
 *
 *    // When the number of written bytes exceeds the initial capacity (4), the
 *    // internal buffer is reallocated automatically with a larger capacity.
 *    b.writeByte('5');
 *
 *
 * 》》 更好的性能
 * ByteBuf最常用的缓冲区实现是字节数组（即byte[]）的非常薄的包装器。
 * 与java.nio.ByteBuffer不同，它没有复杂的边界检查和索引补偿，因此JVM更容易优化缓冲区访问。
 * 更复杂的缓冲区实现仅用于切片或复合缓冲区，并且与java.nio.ByteBuffer一样好。
 */

// todo：零拷贝

/**
 * Abstraction of a byte buffer - the fundamental data structure
 * to represent a low-level binary and text message.
 *
 * Netty uses its own buffer API instead of NIO {@link java.nio.ByteBuffer} to
 * represent a sequence of bytes. This approach has significant advantage over
 * using {@link java.nio.ByteBuffer}.  Netty's new buffer type,
 * {@link io.netty.buffer.ByteBuf}, has been designed from ground
 * up to address the problems of {@link java.nio.ByteBuffer} and to meet the
 * daily needs of network application developers.  To list a few cool features:
 * <ul>
 *   <li>You can define your buffer type if necessary.</li>
 *   <li>Transparent zero copy is achieved by built-in composite buffer type.</li>
 *   <li>A dynamic buffer type is provided out-of-the-box, whose capacity is
 *       expanded on demand, just like {@link java.lang.StringBuffer}.</li>
 *   <li>There's no need to call the {@code flip()} method anymore.</li>
 *   <li>It is often faster than {@link java.nio.ByteBuffer}.</li>
 * </ul>
 *
 * <h3>Extensibility</h3>
 *
 * {@link io.netty.buffer.ByteBuf} has rich set of operations
 * optimized for rapid protocol implementation.  For example,
 * {@link io.netty.buffer.ByteBuf} provides various operations
 * for accessing unsigned values and strings and searching for certain byte
 * sequence in a buffer.  You can also extend or wrap existing buffer type
 * to add convenient accessors.  The custom buffer type still implements
 * {@link io.netty.buffer.ByteBuf} interface rather than
 * introducing an incompatible type.
 *
 * <h3>Transparent Zero Copy</h3>
 *
 * To lift up the performance of a network application to the extreme, you need
 * to reduce the number of memory copy operation.  You might have a set of
 * buffers that could be sliced and combined to compose a whole message.  Netty
 * provides a composite buffer which allows you to create a new buffer from the
 * arbitrary number of existing buffers with no memory copy.  For example, a
 * message could be composed of two parts; header and body.  In a modularized
 * application, the two parts could be produced by different modules and
 * assembled later when the message is sent out.
 * <pre>
 * +--------+----------+
 * | header |   body   |
 * +--------+----------+
 * </pre>
 * If {@link java.nio.ByteBuffer} were used, you would have to create a new big
 * buffer and copy the two parts into the new buffer.   Alternatively, you can
 * perform a gathering write operation in NIO, but it restricts you to represent
 * the composite of buffers as an array of {@link java.nio.ByteBuffer}s rather
 * than a single buffer, breaking the abstraction and introducing complicated
 * state management.  Moreover, it's of no use if you are not going to read or
 * write from an NIO channel.
 * <pre>
 * // The composite type is incompatible with the component type.
 * ByteBuffer[] message = new ByteBuffer[] { header, body };
 * </pre>
 * By contrast, {@link io.netty.buffer.ByteBuf} does not have such
 * caveats because it is fully extensible and has a built-in composite buffer
 * type.
 * <pre>
 * // The composite type is compatible with the component type.
 * {@link io.netty.buffer.ByteBuf} message = {@link io.netty.buffer.Unpooled}.wrappedBuffer(header, body);
 *
 * // Therefore, you can even create a composite by mixing a composite and an
 * // ordinary buffer.
 * {@link io.netty.buffer.ByteBuf} messageWithFooter = {@link io.netty.buffer.Unpooled}.wrappedBuffer(message, footer);
 *
 * // Because the composite is still a {@link io.netty.buffer.ByteBuf}, you can access its content
 * // easily, and the accessor method will behave just like it's a single buffer
 * // even if the region you want to access spans over multiple components.  The
 * // unsigned integer being read here is located across body and footer.
 * messageWithFooter.getUnsignedInt(
 *     messageWithFooter.readableBytes() - footer.readableBytes() - 1);
 * </pre>
 *
 * <h3>Automatic Capacity Extension</h3>
 *
 * Many protocols define variable length messages, which means there's no way to
 * determine the length of a message until you construct the message or it is
 * difficult and inconvenient to calculate the length precisely.  It is just
 * like when you build a {@link java.lang.String}. You often estimate the length
 * of the resulting string and let {@link java.lang.StringBuffer} expand itself
 * on demand.
 * <pre>
 * // A new dynamic buffer is created.  Internally, the actual buffer is created
 * // lazily to avoid potentially wasted memory space.
 * {@link io.netty.buffer.ByteBuf} b = {@link io.netty.buffer.Unpooled}.buffer(4);
 *
 * // When the first write attempt is made, the internal buffer is created with
 * // the specified initial capacity (4).
 * b.writeByte('1');
 *
 * b.writeByte('2');
 * b.writeByte('3');
 * b.writeByte('4');
 *
 * // When the number of written bytes exceeds the initial capacity (4), the
 * // internal buffer is reallocated automatically with a larger capacity.
 * b.writeByte('5');
 * </pre>
 *
 * <h3>Better Performance</h3>
 *
 * Most frequently used buffer implementation of
 * {@link io.netty.buffer.ByteBuf} is a very thin wrapper of a
 * byte array (i.e. {@code byte[]}).  Unlike {@link java.nio.ByteBuffer}, it has
 * no complicated boundary check and index compensation, and therefore it is
 * easier for a JVM to optimize the buffer access.  More complicated buffer
 * implementation is used only for sliced or composite buffers, and it performs
 * as well as {@link java.nio.ByteBuffer}.
 */
package io.netty.buffer;

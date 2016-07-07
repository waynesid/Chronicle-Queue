/*
 * Copyright 2016 higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.openhft.chronicle.queue.impl;

import net.openhft.chronicle.bytes.MappedBytes;
import net.openhft.chronicle.core.ReferenceCounted;
import net.openhft.chronicle.queue.impl.single.ScanResult;
import net.openhft.chronicle.wire.Demarshallable;
import net.openhft.chronicle.wire.UnrecoverableTimeoutException;
import net.openhft.chronicle.wire.Wire;
import net.openhft.chronicle.wire.WriteMarshallable;
import org.jetbrains.annotations.NotNull;

import java.io.EOFException;
import java.io.File;
import java.io.StreamCorruptedException;

public interface WireStore extends ReferenceCounted, Demarshallable, WriteMarshallable {
    /**
     * @return the file associated with this store.
     */
    File file();

    WireStore writePosition(long position);

    /**
     * @return an epoch offset as the number of number of milliseconds since January 1, 1970,
     * 00:00:00 GMT, if you set the epoch to the current time, then the cycle will be ZERO
     */
    long epoch();

    /**
     * @return the next writable position, Will be or-ed with ROLLED_BIT if it has rolled.
     */
    long writePosition();

    ScanResult moveToIndexForRead(@NotNull ExcerptContext ec, long index);

    @NotNull
    MappedBytes bytes();

    /**
     * Reverse look up an index for a position.
     *
     * @param ec the wire of the bytes, to work with
     * @param position  of the start of the message
     * @return index in this store.
     */
    long sequenceForPosition(ExcerptContext ec, long position) throws EOFException, UnrecoverableTimeoutException, StreamCorruptedException;

    String dump();

    void lastAcknowledgedIndexReplicated(long lastAcknowledgedIndexReplicated);

    long lastAcknowledgedIndexReplicated();

    void setPositionForSequenceNumber(final ExcerptContext ec, long sequenceNumber, long position) throws UnrecoverableTimeoutException, StreamCorruptedException;

    long writeHeader(Wire wire, int length, long timeoutMS) throws EOFException, UnrecoverableTimeoutException;

    void writeEOF(Wire wire, long timeoutMS) throws UnrecoverableTimeoutException;
}

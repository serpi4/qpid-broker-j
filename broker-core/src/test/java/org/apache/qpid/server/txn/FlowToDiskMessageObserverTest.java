/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.txn;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.test.utils.QpidTestCase;

public class FlowToDiskMessageObserverTest extends QpidTestCase
{
    private static final int MAX_UNCOMMITTED_IN_MEMORY_SIZE = 100;
    private LocalTransaction.FlowToDiskMessageObserver _flowToDiskMessageObserver;
    private EventLogger _eventLogger    ;
    private LogSubject _logSubject;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _eventLogger = mock(EventLogger.class);
        _logSubject = mock(LogSubject.class);
        _flowToDiskMessageObserver = new LocalTransaction.FlowToDiskMessageObserver(MAX_UNCOMMITTED_IN_MEMORY_SIZE,
                                                                                    _logSubject,
                                                                                    _eventLogger);
    }

    public void testOnMessageEnqueue() throws Exception
    {
        EnqueueableMessage<?> message1 = createMessage(MAX_UNCOMMITTED_IN_MEMORY_SIZE);
        EnqueueableMessage<?> message2 = createMessage(1);
        EnqueueableMessage<?> message3 = createMessage(1);

        _flowToDiskMessageObserver.onMessageEnqueue(message1);

        StoredMessage handle1 = message1.getStoredMessage();
        verify(handle1, never()).flowToDisk();
        verify(_eventLogger, never()).message(same(_logSubject), any(LogMessage.class));

        _flowToDiskMessageObserver.onMessageEnqueue(message2);

        StoredMessage handle2 = message2.getStoredMessage();
        verify(handle1).flowToDisk();
        verify(handle2).flowToDisk();
        verify(_eventLogger).message(same(_logSubject), any(LogMessage.class));

        _flowToDiskMessageObserver.onMessageEnqueue(message3);

        StoredMessage handle3 = message2.getStoredMessage();
        verify(handle1).flowToDisk();
        verify(handle2).flowToDisk();
        verify(handle3).flowToDisk();
        verify(_eventLogger).message(same(_logSubject), any(LogMessage.class));
    }

    public void testReset() throws Exception
    {
        EnqueueableMessage<?> message1 = createMessage(MAX_UNCOMMITTED_IN_MEMORY_SIZE);
        EnqueueableMessage<?> message2 = createMessage(1);

        _flowToDiskMessageObserver.onMessageEnqueue(message1);
        _flowToDiskMessageObserver.reset();
        _flowToDiskMessageObserver.onMessageEnqueue(message2);

        StoredMessage handle1 = message1.getStoredMessage();
        StoredMessage handle2 = message2.getStoredMessage();
        verify(handle1, never()).flowToDisk();
        verify(handle2, never()).flowToDisk();
        verify(_eventLogger, never()).message(same(_logSubject), any(LogMessage.class));
    }

    private EnqueueableMessage<?> createMessage(int size)
    {
        EnqueueableMessage message = mock(EnqueueableMessage.class);
        StoredMessage handle = mock(StoredMessage.class);
        when(message.getStoredMessage()).thenReturn(handle);
        when(handle.getContentSize()).thenReturn(size);
        return message;
    }
}

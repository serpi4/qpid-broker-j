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
package org.apache.qpid.server.security;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.KeyStoreMessages;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.State;

public abstract class AbstractKeyStore<X extends AbstractKeyStore<X>>
        extends AbstractConfiguredObject<X> implements KeyStore<X>
{
    private static Logger LOGGER = LoggerFactory.getLogger(AbstractKeyStore.class);

    private static final long ONE_DAY = 24l * 60l * 60l * 1000l;

    private final Broker<?> _broker;
    private final EventLogger _eventLogger;

    private ScheduledFuture<?> _checkExpiryTaskFuture;


    public AbstractKeyStore(Map<String, Object> attributes, Broker<?> broker)
    {
        super(parentsMap(broker), attributes);

        _broker = broker;
        _eventLogger = broker.getEventLogger();
        _eventLogger.message(KeyStoreMessages.CREATE(getName()));
    }

    public final Broker<?> getBroker()
    {
        return _broker;
    }

    final EventLogger getEventLogger()
    {
        return _eventLogger;
    }

    @Override
    protected void onClose()
    {
        super.onClose();
        if(_checkExpiryTaskFuture != null)
        {
            _checkExpiryTaskFuture.cancel(false);
            _checkExpiryTaskFuture = null;
        }
    }

    protected void initializeExpiryChecking()
    {
        int checkFrequency;
        try
        {
            checkFrequency = getContextValue(Integer.class, CERTIFICATE_EXPIRY_CHECK_FREQUENCY);
        }
        catch (IllegalArgumentException | NullPointerException e)
        {
            LOGGER.warn("Cannot parse the context variable {} ", CERTIFICATE_EXPIRY_CHECK_FREQUENCY, e);
            checkFrequency = DEFAULT_CERTIFICATE_EXPIRY_CHECK_FREQUENCY;
        }
        if(getBroker().getState() == State.ACTIVE)
        {
            _checkExpiryTaskFuture = getBroker().scheduleHouseKeepingTask(checkFrequency, TimeUnit.DAYS, new Runnable()
            {
                @Override
                public void run()
                {
                    checkCertificateExpiry();
                }
            });
        }
        else
        {
            final int frequency = checkFrequency;
            getBroker().addChangeListener(new ConfigurationChangeListener()
            {
                @Override
                public void stateChanged(final ConfiguredObject<?> object, final State oldState, final State newState)
                {
                    if (newState == State.ACTIVE)
                    {
                        _checkExpiryTaskFuture =
                                getBroker().scheduleHouseKeepingTask(frequency, TimeUnit.DAYS, new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        checkCertificateExpiry();
                                    }
                                });
                        getBroker().removeChangeListener(this);
                    }
                }

                @Override
                public void childAdded(final ConfiguredObject<?> object, final ConfiguredObject<?> child)
                {

                }

                @Override
                public void childRemoved(final ConfiguredObject<?> object, final ConfiguredObject<?> child)
                {

                }

                @Override
                public void attributeSet(final ConfiguredObject<?> object,
                                         final String attributeName,
                                         final Object oldAttributeValue,
                                         final Object newAttributeValue)
                {

                }

                @Override
                public void bulkChangeStart(final ConfiguredObject<?> object)
                {

                }

                @Override
                public void bulkChangeEnd(final ConfiguredObject<?> object)
                {

                }
            });
        }
    }

    protected abstract void checkCertificateExpiry();
}
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
package org.apache.qpid.server.exchange;

import org.apache.log4j.Logger;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.virtualhost.UnknownExchangeException;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DefaultExchangeFactory implements ExchangeFactory
{
    public static final String DEFAULT_DLE_NAME_SUFFIX = "_DLE";

    private static final Logger LOGGER = Logger.getLogger(DefaultExchangeFactory.class);

    private static final String[] BASE_EXCHANGE_TYPES =
                                    new String[]{ExchangeDefaults.DIRECT_EXCHANGE_CLASS,
                                                 ExchangeDefaults.FANOUT_EXCHANGE_CLASS,
                                                 ExchangeDefaults.HEADERS_EXCHANGE_CLASS,
                                                 ExchangeDefaults.TOPIC_EXCHANGE_CLASS};

    private final VirtualHost _host;
    private Map<String, ExchangeType<? extends ExchangeImpl>> _exchangeClassMap = new HashMap<String, ExchangeType<? extends ExchangeImpl>>();

    public DefaultExchangeFactory(VirtualHost host)
    {
        _host = host;

        @SuppressWarnings("rawtypes")
        Iterable<ExchangeType> exchangeTypes = loadExchangeTypes();
        for (ExchangeType<?> exchangeType : exchangeTypes)
        {
            String typeName = exchangeType.getType();

            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Registering exchange type '" + typeName + "' using class '" + exchangeType.getClass().getName() + "'");
            }

            if(_exchangeClassMap.containsKey(typeName))
            {
                ExchangeType<?> existingType = _exchangeClassMap.get(typeName);

                throw new IllegalStateException("ExchangeType with type name '" + typeName + "' is already registered using class '"
                                                 + existingType.getClass().getName() + "', can not register class '"
                                                 + exchangeType.getClass().getName() + "'");
            }

            _exchangeClassMap.put(typeName, exchangeType);
        }

        for(String type : BASE_EXCHANGE_TYPES)
        {
            if(!_exchangeClassMap.containsKey(type))
            {
                throw new IllegalStateException("Did not find expected exchange type: " + type);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    protected Iterable<ExchangeType> loadExchangeTypes()
    {
        return new QpidServiceLoader<ExchangeType>().atLeastOneInstanceOf(ExchangeType.class);
    }

    public Collection<ExchangeType<? extends ExchangeImpl>> getRegisteredTypes()
    {
        return _exchangeClassMap.values();
    }

    @Override
    public NonDefaultExchange createExchange(final Map<String, Object> attributes)
            throws AMQUnknownExchangeType, UnknownExchangeException
    {
        String type = MapValueConverter.getStringAttribute(org.apache.qpid.server.model.Exchange.TYPE, attributes);
        ExchangeType<? extends ExchangeImpl> exchType = _exchangeClassMap.get(type);
        if (exchType == null)
        {
            throw new AMQUnknownExchangeType("Unknown exchange type: " + type,null);
        }
        return exchType.newInstance(_host, attributes);
    }

    @Override
    public NonDefaultExchange restoreExchange(Map<String,Object> attributes)
            throws AMQUnknownExchangeType, UnknownExchangeException
    {
        return createExchange(attributes);

    }
}

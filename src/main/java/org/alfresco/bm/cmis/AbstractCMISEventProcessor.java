/*
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.bm.cmis;

import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.impl.jaxb.CmisException;
import org.apache.commons.lang3.exception.ExceptionUtils;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

/**
 * Abstract event processing to handle CMIS-specific behaviour e.g. the catch-and-report of {@link CmisException}.
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public abstract class AbstractCMISEventProcessor extends AbstractEventProcessor
{
    /**
     * {@inheritDoc}
     */
    protected abstract EventResult processCMISEvent(Event event) throws Exception;
    
    public final EventResult processEvent(Event event) throws Exception
    {
        try
        {
            return processCMISEvent(event);
        }
        catch (CmisRuntimeException e)
        {
            String error = e.getMessage();
            String stack = ExceptionUtils.getStackTrace(e);
            // Grab the CMIS information
            DBObject data = BasicDBObjectBuilder
                    .start()
                    .append("msg", error)
                    .append("stack", stack)
                    .push("cmisFault")
                        .append("code", "" + e.getCode())               // BigInteger is not Serializable
                        .append("errorContent", e.getErrorContent())
                    .pop()
                    .get();
            // Build failure result
            return new EventResult(data, false);
        }
    }
}

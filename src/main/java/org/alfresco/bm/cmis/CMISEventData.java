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

import java.util.LinkedList;

import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.Session;

/**
 * A data transport that is explicitly *not* serializable.
 * <p/>
 * This data can be used by events to pass data from one event to the next.
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class CMISEventData
{
    private final Session session;
    private final LinkedList<Folder> breadcrumb;
    
    public CMISEventData(Session session)
    {
        this.session = session;
        this.breadcrumb = new LinkedList<Folder>();
    }
    
    public CMISEventData(CMISEventData copyFrom)
    {
        this.session = copyFrom.session;
        this.breadcrumb = new LinkedList<Folder>(copyFrom.breadcrumb);
    }

    public Session getSession()
    {
        return session;
    }

    /**
     * @return              the internally-stored breadcrumb that can be modified directly
     */
    public LinkedList<Folder> getBreadcrumb()
    {
        return breadcrumb;
    }
}

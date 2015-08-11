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

import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.Session;

/**
 * A data transport that is explicitly *not* serializable.
 * <p/>
 * This data can be used by events to pass data from one event to the next.
 * 
 * @author Derek Hulley / Frank Becker
 * @since 1.0
 */
public class CMISEventData
{
    private final Session session;
    private final LinkedList<Folder> breadcrumb;
    private Document document;
    
    /** @since 1.3 */
    private int pageSize;
    private int pageCount;
    private String query;
    
    public CMISEventData(Session session)
    {
        this.session = session;
        this.breadcrumb = new LinkedList<Folder>();
        this.document = null;
        this.pageSize = 0;
        this.pageCount = 0;
    }
    
    public CMISEventData(CMISEventData copyFrom)
    {
        this.session = copyFrom.session;
        this.breadcrumb = new LinkedList<Folder>(copyFrom.breadcrumb);
        this.document = copyFrom.document;
        this.pageSize = copyFrom.pageSize;
        this.pageCount = copyFrom.pageCount;
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

    /**
     * @return              the document in the current event
     */
    public Document getDocument()
    {
        return document;
    }
    /**
     * @param document      set the document for the current event
     */
    public void setDocument(Document document)
    {
        this.document = document;
    }
    
    /**
     * @return pageSize in searches
     * @since 1.3
     */
    public int getPageSize()
    {
        return this.pageSize;
    }
    
    /**
     * Sets the page size in searches.
     * @param pageSize_p (int) page size to set
     * @since 1.3
     */
    public void setPageSize(int pageSize_p)
    {
        this.pageSize = pageSize_p;
    }

    /**
     * @return (int) page count of the current search
     * @since 1.3
     */
    public int getPageCount()
    {
        return this.pageCount;
    }
    
    /**
     * Sets the page count of the current search
     * @param pageCount_p (int) page count
     * @since 1.3
     */
    public void setPageCount(int pageCount_p)
    {
        this.pageCount = pageCount_p;
    }
    
    /**
     * Sets the query string to be "paged"
     * @param query_p (String) query
     * @since 1.3
     */
    public void setQuery(String query_p)
    {
        this.query = query_p;
    }
    
    /**
     * @return (String) query to be paged. 
     */
    public String getQuery()
    {
        return this.query;
    }
}

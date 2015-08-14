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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

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
    private ArrayList<String> objectIdCollection;

    public CMISEventData(Session session)
    {
        this.session = session;
        this.breadcrumb = new LinkedList<Folder>();
        this.document = null;
        this.objectIdCollection = new ArrayList<String>();
    }

    @SuppressWarnings("unchecked")
    public CMISEventData(CMISEventData copyFrom)
    {
        this.session = copyFrom.session;
        this.breadcrumb = new LinkedList<Folder>(copyFrom.breadcrumb);
        this.document = copyFrom.document;
        this.objectIdCollection = (ArrayList<String>)copyFrom.objectIdCollection.clone();
    }

    public Session getSession()
    {
        return session;
    }

    /**
     * @return the internally-stored bread-crumb that can be modified directly
     */
    public LinkedList<Folder> getBreadcrumb()
    {
        return breadcrumb;
    }

    /**
     * @return the document in the current event
     */
    public Document getDocument()
    {
        return document;
    }

    /**
     * @param document
     *            set the document for the current event
     */
    public void setDocument(Document document)
    {
        this.document = document;
    }

    /**
     * @return Direct access to the String ArrayList of object IDs to process
     */
    public ArrayList<String> getObjectIds()
    {
        return this.objectIdCollection;
    }
}

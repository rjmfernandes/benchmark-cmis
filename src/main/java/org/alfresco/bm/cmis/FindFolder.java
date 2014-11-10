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

import java.util.Iterator;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;

import com.mongodb.BasicDBObjectBuilder;

/**
 * Perform a search for any folder
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object } containing an existing session
 * 
 * <h1>Actions</h1>
 * 
 * Perform a repository-wide search for a folder
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_FOLDER_FOUND}: The {@link CMISEventData data object} with a folder appended<br/>
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class FindFolder extends AbstractCMISEventProcessor
{
    public static final String EVENT_NAME_FOLDER_FOUND = "cmis.folderFound";
    
    private String eventNameFolderFound;

    /**
     */
    public FindFolder()
    {
        super();
        this.eventNameFolderFound = EVENT_NAME_FOLDER_FOUND;
    }

    /**
     * Override the {@link #EVENT_NAME_FOLDER_FOUND default} event name for 'folder found'.
     */
    public void setEventNameFolderFound(String eventNameFolderFound)
    {
        this.eventNameFolderFound = eventNameFolderFound;
    }
    
    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        super.suspendTimer();                               // Timer control
        
        CMISEventData data = (CMISEventData) event.getData();
        // A quick double-check
        if (data == null)
        {
            return new EventResult("Unable to search for folder; no session provided.", false);
        }
        Session session = data.getSession();
        
        super.resumeTimer();                            // Timer control
        
        String typeStr = "cmis:folder";

        // Get the query name of cmis:objectId
        ObjectType type = session.getTypeDefinition(typeStr);
        PropertyDefinition<?> objectIdPropDef = type.getPropertyDefinitions().get(PropertyIds.OBJECT_ID);
        String objectIdQueryName = objectIdPropDef.getQueryName();
        String query = "SELECT " + objectIdQueryName + " FROM " + type.getQueryName();
    
        // execute query
        ItemIterable<QueryResult> queryResults = session.query(query, false);

        long totalResults = queryResults.getTotalNumItems();               // For information only
        Folder folder = null;
        if (totalResults == 0L)
        {
            return new EventResult("No folders found.  Unable to do folder listing.", false);
        }
        if (totalResults == -1L)
        {
            return new EventResult("Result size unknown.  Unable to choose a random folder.", false);
        }
        // Choose a random skip value
        long skip = (long) (Math.random() * (double) totalResults);
        
        Iterable<QueryResult> pageQueryResults = queryResults.skipTo(skip);
        Iterator<QueryResult> pageQueryIterator = pageQueryResults.iterator();
        if (pageQueryIterator.hasNext())
        {
            QueryResult result = pageQueryIterator.next();
            String objectId = result.getPropertyValueByQueryName(objectIdQueryName);
            folder = (Folder) session.getObject(session.createObjectId(objectId));
        }
        else
        {
            return new EventResult("Skipped to position with no result.  Total: " + totalResults + ".  Skipped: " + skip, false);
        }
    
        super.stopTimer();                              // Timer control
        
        // Attach the folder to the data
        data.getBreadcrumb().add(folder);
        
        // Done
        Event doneEvent = new Event(eventNameFolderFound, data);
        EventResult result = new EventResult(
                BasicDBObjectBuilder
                    .start()
                    .append("msg", "Successfully selected random folder.")
                    .append("query", query)
                    .push("paging")
                        .append("total", totalResults)
                        .append("skippedTo", skip)
                    .pop()
                    .push("folder")
                        .append("id", folder.getId())
                        .append("name", folder.getName())
                    .pop()
                    .get(),
                doneEvent);
        
        // Done
        return result;
    }
}

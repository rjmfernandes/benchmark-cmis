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
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.SessionParameter;

import com.mongodb.BasicDBObjectBuilder;

/**
 * Retrieve the root folder
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object } containing the {@link CMISEventData#getSession() CMIS session}.
 * 
 * <h1>Actions</h1>
 * 
 * Retrieve the root folder object
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_ROOT_FOLDER_RETRIEVED}: The process name<br/>
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class RetrieveRootFolder extends AbstractEventProcessor
{
    public static final String REPOSITORY_ID_USE_FIRST = "---";
    public static final String EVENT_NAME_ROOT_FOLDER_RETRIEVED = "cmis.rootFolderRetrieved";
    
    private String eventNameRootFolderRetrieved;

    /**
     * @param repositoryId              the ID of the repository required by the {@link SessionParameter.REPOSITORY_ID} parameter
     */
    public RetrieveRootFolder()
    {
        super();
        this.eventNameRootFolderRetrieved = EVENT_NAME_ROOT_FOLDER_RETRIEVED;
    }

    /**
     * Override the {@link #EVENT_NAME_ROOT_FOLDER_RETRIEVED default} event name for 'root folder retrieved'.
     */
    public void setEventNameRootFolderRetrieved(String eventNameRootFolderRetrieved)
    {
        this.eventNameRootFolderRetrieved = eventNameRootFolderRetrieved;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception
    {
        CMISEventData data = (CMISEventData) event.getDataObject();
        // A quick double-check
        if (data == null)
        {
            return new EventResult("Unable to get CMIS root folder; no session provided.", false);
        }

        // Get the session
        Session session = data.getSession();
        
        Folder folder = session.getRootFolder();
        
        // Store the folder
        data = new CMISEventData(data);
        data.setFolder(folder);

        // Done
        Event doneEvent = new Event(eventNameRootFolderRetrieved, session);
        EventResult result = new EventResult(
                BasicDBObjectBuilder
                    .start()
                    .append("msg", "Successfully retrieved root folder.")
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

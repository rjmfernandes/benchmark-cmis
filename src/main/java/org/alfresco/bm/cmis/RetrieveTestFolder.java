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

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.util.FileUtils;

import com.mongodb.BasicDBObjectBuilder;

/**
 * Retrieve a test folder
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object } containing the {@link CMISEventData#getSession() CMIS session}.
 * 
 * <h1>Actions</h1>
 * 
 * Retrieve a test folder by path and push it into the event data
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_TEST_FOLDER_RETRIEVED}: The process name<br/>
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class RetrieveTestFolder extends AbstractCMISEventProcessor
{
    public static final String REPOSITORY_ID_USE_FIRST = "---";
    public static final String EVENT_NAME_TEST_FOLDER_RETRIEVED = "cmis.testFolderRetrieved";
    
    private final String path;
    private String eventNameTestFolderRetrieved;

    /**
     * @param path                  the path (starts with '/') to the test folder from the root
     */
    public RetrieveTestFolder(String path)
    {
        super();
        this.path = path;
        this.eventNameTestFolderRetrieved = EVENT_NAME_TEST_FOLDER_RETRIEVED;
    }

    /**
     * Override the {@link #EVENT_NAME_TEST_FOLDER_RETRIEVED default} event name for 'test folder retrieved'.
     */
    public void setEventNameTestFolderRetrieved(String eventNameTestFolderRetrieved)
    {
        this.eventNameTestFolderRetrieved = eventNameTestFolderRetrieved;
    }

    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        super.suspendTimer();                               // Timer control
        
        CMISEventData data = (CMISEventData) event.getDataObject();
        // A quick double-check
        if (data == null)
        {
            return new EventResult("Unable to get CMIS test folder; no session provided.", false);
        }

        // Get the session
        Session session = data.getSession();
        
        super.resumeTimer();                                // Timer control
        Folder folder = FileUtils.getFolder(path, session);
        super.stopTimer();                                  // Timer control
        if (folder == null)
        {
            return new EventResult("Failed to find test folder at path " + path, false);
        }
        
        // Store the folder
        data = new CMISEventData(data);
        data.getBreadcrumb().clear();
        data.getBreadcrumb().add(folder);

        // Done
        Event doneEvent = new Event(eventNameTestFolderRetrieved, data);
        EventResult result = new EventResult(
                BasicDBObjectBuilder
                    .start()
                    .append("msg", "Successfully retrieved test folder.")
                    .push("folder")
                        .append("id", folder.getPath())
                        .append("id", folder.getId())
                        .append("name", folder.getName())
                    .pop()
                    .get(),
                doneEvent);
        
        // Done
        return result;
    }
}

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.commons.PropertyIds;

import com.mongodb.BasicDBObjectBuilder;

/**
 * Create a folder
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object } containing an existing folder.
 * 
 * <h1>Actions</h1>
 * 
 * Create a new folder in the given folder
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_FOLDER_CREATED}: The {@link CMISEventData data object} with the new folder<br/>
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class CreateFolder extends AbstractCMISEventProcessor
{
    public static final String EVENT_NAME_FOLDER_CREATED = "cmis.folderCreated";
    
    private String eventNameFolderCreated;

    /**
     */
    public CreateFolder()
    {
        super();
        this.eventNameFolderCreated = EVENT_NAME_FOLDER_CREATED;
    }

    /**
     * Override the {@link #EVENT_NAME_FOLDER_CREATED default} event name for 'folder created'.
     */
    public void setEventNameFolderCreated(String eventNameFolderCreated)
    {
        this.eventNameFolderCreated = eventNameFolderCreated;
    }

    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        // Suspect timer
        super.suspendTimer();
        
        CMISEventData data = (CMISEventData) event.getData();
        // A quick double-check
        if (data == null)
        {
            return new EventResult("Unable to create folder; no session provided.", false);
        }
        if (data.getBreadcrumb().isEmpty())
        {
            return new EventResult("Unable to create folder; no folder provided.", false);
        }
        Folder folder = data.getBreadcrumb().getLast();
        
        // The folder name
        String newFolderName = UUID.randomUUID().toString() + "-" + super.getName();
        
        Map<String, String> newFolderProps = new HashMap<String, String>();
        newFolderProps.put(PropertyIds.OBJECT_TYPE_ID, "cmis:folder");
        newFolderProps.put(PropertyIds.NAME, newFolderName);
        
        super.resumeTimer();
        Folder newFolder = folder.createFolder(newFolderProps);
        super.stopTimer();

        // Append it to the breadcrumb
        data.getBreadcrumb().add(newFolder);
        
        // Done
        Event doneEvent = new Event(eventNameFolderCreated, data);
        EventResult result = new EventResult(
                BasicDBObjectBuilder
                    .start()
                    .append("msg", "Successfully created folder.")
                    .push("folder")
                        .append("id", newFolder.getId())
                        .append("name", newFolder.getName())
                    .pop()
                    .get(),
                doneEvent);
        
        // Done
        return result;
    }
}

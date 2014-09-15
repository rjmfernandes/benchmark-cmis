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

import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.apache.chemistry.opencmis.client.api.Folder;

import com.mongodb.BasicDBObjectBuilder;

/**
 * Delete a folder
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object } containing an existing folder.
 * 
 * <h1>Actions</h1>
 * 
 * Delete the last folder and pop it off the breadcrumb
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_FOLDER_DELETED}: The {@link CMISEventData data object} with the folder popped off the breadcrumb.<br/>
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class DeleteFolder extends AbstractEventProcessor
{
    public static final String EVENT_NAME_FOLDER_DELETED = "cmis.folderDeleted";
    
    private String eventNameFolderDeleted;

    /**
     */
    public DeleteFolder()
    {
        super();
        this.eventNameFolderDeleted = EVENT_NAME_FOLDER_DELETED;
    }

    /**
     * Override the {@link #EVENT_NAME_FOLDER_DELETED default} event name for 'folder deleted'.
     */
    public void setEventNameFolderDeleted(String eventNameFolderDeleted)
    {
        this.eventNameFolderDeleted = eventNameFolderDeleted;
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
        LinkedList<Folder> breadcrumb = data.getBreadcrumb();
        if (breadcrumb.size() < 2)
        {
            return new EventResult("We need at least two folders to work with.", false);
        }
        
        // Delete the last folder
        Folder folder = breadcrumb.getLast();
        folder.delete();
        
        // Append it to the breadcrumb
        data.getBreadcrumb().removeLast();
        
        // Done
        Event doneEvent = new Event(eventNameFolderDeleted, data);
        EventResult result = new EventResult(
                BasicDBObjectBuilder
                    .start()
                    .append("msg", "Successfully deleted folder.")
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

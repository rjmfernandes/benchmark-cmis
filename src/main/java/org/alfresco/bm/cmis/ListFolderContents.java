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
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.commons.SessionParameter;

import com.mongodb.BasicDBObjectBuilder;

/**
 * List the contents of a folder
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object } containing the folder.
 * 
 * <h1>Actions</h1>
 * 
 * Retrieve the contents of the folder
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_ROOT_FOLDER_RETRIEVED}: the {@link CMISEventData data object} as inbound<br/>
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class ListFolderContents extends AbstractEventProcessor
{
    public static final String EVENT_NAME_FOLDER_CONTENTS_LISTED = "cmis.folderContentsListed";
    
    private String eventNameFolderContentsListed;

    /**
     * @param repositoryId              the ID of the repository required by the {@link SessionParameter.REPOSITORY_ID} parameter
     */
    public ListFolderContents()
    {
        super();
        this.eventNameFolderContentsListed = EVENT_NAME_FOLDER_CONTENTS_LISTED;
    }

    /**
     * Override the {@link #EVENT_NAME_FOLDER_CONTENTS_LISTED default} event name for 'folder contents listed'.
     */
    public void setEventNameFolderContentsListed(String eventNameFolderContentsListed)
    {
        this.eventNameFolderContentsListed = eventNameFolderContentsListed;
    }

    @Override
    @SuppressWarnings("unused")
    public EventResult processEvent(Event event) throws Exception
    {
        CMISEventData data = (CMISEventData) event.getDataObject();
        // A quick double-check
        if (data == null)
        {
            return new EventResult("Unable to get CMIS root folder; no session provided.", false);
        }
        if (data.getBreadcrumb().isEmpty())
        {
            return new EventResult("Unable to get CMIS folder listing; no folder provided.", false);
        }
        Folder folder = data.getBreadcrumb().getLast();

        // Get details of how to page, etc
        OperationContext ctx = data.getSession().getDefaultContext();
        int pageSize = ctx.getMaxItemsPerPage();
        
        ItemIterable<CmisObject> children = folder.getChildren();
        long totalChildren = children.getTotalNumItems();               // For information only
        int pageCount = 0;
        // We have to iterate using paging
        long skip = 0L;
        ItemIterable<CmisObject> pageOfChildren = children.skipTo(skip);
        while (pageOfChildren.getPageNumItems() > 0L)
        {
            pageCount++;
            for (CmisObject cmisObject : pageOfChildren)
            {
                skip++;
            }
            // Get the next page of children
            pageOfChildren = children.skipTo(skip);
        }

        // Done
        Event doneEvent = new Event(eventNameFolderContentsListed, data);
        EventResult result = new EventResult(
                BasicDBObjectBuilder
                    .start()
                    .append("msg", "Successfully retrieved folder listing.")
                    .push("folder")
                        .append("id", folder.getId())
                        .append("name", folder.getName())
                    .push("paging")
                        .append("pageSize", pageSize)
                        .append("totalChildren", totalChildren)
                        .append("pageCount", pageCount)
                    .pop()
                    .get(),
                doneEvent);
        
        // Done
        return result;
    }
}

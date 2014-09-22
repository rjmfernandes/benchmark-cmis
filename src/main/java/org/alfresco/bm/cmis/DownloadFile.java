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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.commons.io.FileUtils;

import com.mongodb.BasicDBObjectBuilder;

/**
 * Download an existing file
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object } containing an existing file
 * 
 * <h1>Actions</h1>
 * 
 * Download the current file from the session data
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_FILE_DOWNLOADED}: The {@link CMISEventData data object} with the existing file<br/>
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class DownloadFile extends AbstractCMISEventProcessor
{
    public static final String EVENT_NAME_FILE_DOWNLOADED = "cmis.fileDownloaded";
    
    private String eventNameFileDownloaded;

    /**
     * @param testFileService               service to provide sample files for upload
     */
    public DownloadFile()
    {
        super();
        this.eventNameFileDownloaded = EVENT_NAME_FILE_DOWNLOADED;
    }

    /**
     * Override the {@link #EVENT_NAME_FILE_DOWNLOADED default} event name for 'file downloaded'.
     */
    public void setEventNameFileDownloaded(String eventNameFileDownloaded)
    {
        this.eventNameFileDownloaded = eventNameFileDownloaded;
    }

    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        super.suspendTimer();                               // Timer control
        
        CMISEventData data = (CMISEventData) event.getDataObject();
        // A quick double-check
        if (data == null)
        {
            return new EventResult("Unable to download file; no session provided.", false);
        }
        if (data.getBreadcrumb().size() == 0)
        {
            return new EventResult("Unable to download file; no folder provided.", false);
        }
        if (data.getDocument() == null)
        {
            return new EventResult("Unable to download file; no file provided.", false);
        }
        // Go to the folder
        Document document = data.getDocument();
        String filename = document.getName();
        // We will need this to look it up by path
        Folder folder = data.getBreadcrumb().getLast();
        String folderPath = folder.getPath();
        
        // The path
        String path = folderPath + "/" + filename;
        
        super.resumeTimer();                                // Timer control
        // Look it up.
        try
        {
            CmisObject foundObj = data.getSession().getObjectByPath(path);
            if (!(foundObj instanceof Document))
            {
                return new EventResult("Recently-created document not found at '" + path + "', but found " + foundObj, false);
            }
            document = (Document) foundObj;
        }
        catch (CmisObjectNotFoundException e)
        {
            return new EventResult("Unable to find recently-created document: " + path, false);
        }
        // Now download
        ContentStream cs = document.getContentStream();
        if (cs == null)
        {
            return new EventResult("Recently-created document has no content: " + path, false);
        }
        InputStream is = cs.getStream();
        File file = null;
        long fileSize = 0L;
        try
        {
            file = File.createTempFile(getName(), ".tmp");
            FileUtils.copyInputStreamToFile(is, file);
        }
        finally
        {
            if (is != null)
            {
                try { is.close(); } catch (IOException e) {}
            }
            // Stop the timer, here
            super.stopTimer();                              // Timer control
            // Remove the local file
            if (file != null)
            {
                fileSize = file.length();
                FileUtils.deleteQuietly(file);
            }
        }
        
        // Done
        Event doneEvent = new Event(eventNameFileDownloaded, data);
        EventResult result = new EventResult(
                BasicDBObjectBuilder
                    .start()
                    .append("msg", "Successfully downloaded document.")
                    .push("document")
                        .append("id", document.getId())
                        .append("name", document.getName())
                        .append("paths", document.getPaths())
                        .append("size", fileSize)
                    .pop()
                    .get(),
                doneEvent);
        
        // Done
        return result;
    }
}

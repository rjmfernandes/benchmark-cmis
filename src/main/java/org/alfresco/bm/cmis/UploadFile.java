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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.file.TestFileService;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.ContentStreamImpl;

import com.mongodb.BasicDBObjectBuilder;

/**
 * Upload a new file
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object } containing an existing folder.
 * 
 * <h1>Actions</h1>
 * 
 * Upload a random file to the current folder
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_FILE_UPLOADED}: The {@link CMISEventData data object} with the new file<br/>
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class UploadFile extends AbstractCMISEventProcessor
{
    public static final String EVENT_NAME_FILE_UPLOADED = "cmis.fileUploaded";
    
    private final TestFileService testFileService;
    private String eventNameFileUploaded;

    /**
     * @param testFileService               service to provide sample files for upload
     */
    public UploadFile(TestFileService testFileService)
    {
        super();
        this.testFileService = testFileService;
        this.eventNameFileUploaded = EVENT_NAME_FILE_UPLOADED;
    }

    /**
     * Override the {@link #EVENT_NAME_FILE_UPLOADED default} event name for 'file uploaded'.
     */
    public void setEventNameFileUploaded(String eventNameFileUploaded)
    {
        this.eventNameFileUploaded = eventNameFileUploaded;
    }

    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        super.suspendTimer();                               // Timer control
        
        CMISEventData data = (CMISEventData) event.getDataObject();
        // A quick double-check
        if (data == null)
        {
            return new EventResult("Unable to upload file; no session provided.", false);
        }
        if (data.getBreadcrumb().isEmpty())
        {
            return new EventResult("Unable to upload file; no folder provided.", false);
        }
        Folder folder = data.getBreadcrumb().getLast();
        
        // The file name
        File file = testFileService.getFile();
        if (file == null)
        {
            return new EventResult("No test files exist for upload: " + testFileService, false);
        }
        String filename = UUID.randomUUID().toString() + "-" + super.getName() + "-" + file.getName();
        
        Map<String, String> newFileProps = new HashMap<String, String>();
        newFileProps.put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
        newFileProps.put(PropertyIds.NAME, filename);
        
        // Open up a stream to the file
        InputStream is = new BufferedInputStream(new FileInputStream(file));
        Document newFile = null;
        try
        {
            long fileLen = file.length();
            ContentStream cs = new ContentStreamImpl(filename, BigInteger.valueOf(fileLen), "application/octet-stream", is);

            // Make sure we only time the document creation
            super.resumeTimer();                            // Timer control
            newFile = folder.createDocument(newFileProps, cs, VersioningState.MAJOR);
            super.stopTimer();                              // Timer control
        }
        finally
        {
            if (is != null)
            {
                try { is.close(); } catch (IOException e) {}
            }
        }
        

        // Append it to the breadcrumb
        data.setDocument(newFile);
        
        // Done
        Event doneEvent = new Event(eventNameFileUploaded, data);
        EventResult result = new EventResult(
                BasicDBObjectBuilder
                    .start()
                    .append("msg", "Successfully uploaded document.")
                    .push("document")
                        .append("id", newFile.getId())
                        .append("name", newFile.getName())
                        .append("paths", newFile.getPaths())
                    .pop()
                    .get(),
                doneEvent);
        
        // Done
        return result;
    }
}

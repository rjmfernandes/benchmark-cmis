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
import org.alfresco.bm.file.TestFileService;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.QueryResult;

import com.mongodb.BasicDBObjectBuilder;

/**
 * Perform a search in the current folder
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object } containing an existing folder.
 * 
 * <h1>Actions</h1>
 * 
 * Perform a search in the current folder using search terms from a remotely-provided text file
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_SEARCH_COMPLETED}: The {@link CMISEventData data object} without changes<br/>
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class SearchInFolder extends AbstractCMISEventProcessor
{
    public static final String EVENT_NAME_SEARCH_COMPLETED = "cmis.searchCompleted";
    
    private final TestFileService testFileService;
    private final String searchTermsFilename;
    private String[] searchStrings;
    private String eventNameSearchCompleted;

    /**
     * @param testFileService               service to provide search terms files
     * @param searchTermsFilename           the name of the remote file containing search terms to use
     */
    public SearchInFolder(TestFileService testFileService, String searchTermsFilename)
    {
        super();
        this.testFileService = testFileService;
        this.searchTermsFilename = searchTermsFilename;
        this.searchStrings = DEFAULT_SEARCH_STRINGS;
        
        this.eventNameSearchCompleted = EVENT_NAME_SEARCH_COMPLETED;
    }

    /**
     * Override the {@link #EVENT_NAME_SEARCH_COMPLETED default} event name for 'search completed'.
     */
    public void setEventNameSearchCompleted(String eventNameSearchCompleted)
    {
        this.eventNameSearchCompleted = eventNameSearchCompleted;
    }
    
    /**
     * Safe method to get the search strings.  Blocking will be short-lived.
     */
    private synchronized String[] getSearchStrings()
    {
        if (searchStrings != null)
        {
            return searchStrings;
        }
        // No files.  Go straight to the defaults.
        if (testFileService == null)
        {
            searchStrings = DEFAULT_SEARCH_STRINGS;
        }
        else
        {
            searchStrings = getSearchStrings(testFileService, searchTermsFilename);
            if (searchStrings.length == 0)
            {
                searchStrings = DEFAULT_SEARCH_STRINGS;
            }
        }
        return searchStrings;
    }

    @Override
    @SuppressWarnings("unused")
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
        
        // Get details of how to page, etc
        OperationContext ctx = data.getSession().getDefaultContext();
        int pageSize = ctx.getMaxItemsPerPage();
        
        // Get a random string
        String[] searchStrings = getSearchStrings();
        String searchString = AbstractCMISEventProcessor.getRandomSearchString(searchStrings);
        String query = String.format(
                "SELECT D.* FROM cmis:document D WHERE IN_FOLDER('%s') AND CONTAINS('%s')",
                folder.getId(),
                searchString);

        super.resumeTimer();
        ItemIterable<QueryResult> queryResults = data.getSession().query(query, false);
        long totalResults = queryResults.getTotalNumItems();               // For information only
        int pageCount = 0;
        // We have to iterate using paging
        long skip = 0L;
        ItemIterable<QueryResult> pageOfResults = queryResults.skipTo(skip);
        while (pageOfResults.getPageNumItems() > 0L)
        {
            pageCount++;
            for (QueryResult queryResult : pageOfResults)
            {
                skip++;
            }
            // Get the next page of children
            pageOfResults = queryResults.skipTo(skip);
        }
        super.stopTimer();                              // Timer control
        
        // Done
        Event doneEvent = new Event(eventNameSearchCompleted, data);
        EventResult result = new EventResult(
                BasicDBObjectBuilder
                    .start()
                    .append("msg", "Successfully searched in folder.")
                    .append("query", query)
                    .push("folder")
                        .append("id", folder.getId())
                        .append("name", folder.getName())
                    .push("paging")
                        .append("pageSize", pageSize)
                        .append("totalResults", totalResults)
                        .append("pageCount", pageCount)
                    .pop()
                    .get(),
                doneEvent);
        
        // Done
        return result;
    }
}

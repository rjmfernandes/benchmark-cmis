package org.alfresco.bm.cmis;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.file.TestFileService;

/**
 * Query documents by executing a CMIS search for properties, optional in a folder
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object } that may contain a folder.
 * 
 * <h1>Actions</h1>
 * 
 * Executes a CMIS property search, stores the documents in copies of the event data.
 * 
 * <h1>Output</h1>
 * 
 * As much {@link #EVENT_NAME_QUERY_COMPLETED} as configured and found by search: the {@link CMISEventData data object} <br/>
 * 
 * @author Frank Becker
 * @since 1.3
 */
public class QueryDocuments extends AbstractQueryCMISEventProcessor
{
    /** default event name of the next event */
    public static final String EVENT_NAME_QUERY_COMPLETED = "cmis.documentsQueryCompleted";

    /** Stores the name and location in the resources of the document query file */
    public static final String RESSOURCE_QUERY_FILENAME = "config/documentsQuery.txt";
  
    /**
     * Constructor
     * 
     * @param eventNameQueryCompleted_p
     *            (String) name of next event or null/empty if to use default {@link #EVENT_NAME_QUERY_COMPLETED}
     */
    public QueryDocuments(TestFileService testFileService_p, String queryFileName_p, String eventNameQueryCompleted_p)
    {
        super(testFileService_p, queryFileName_p, eventNameQueryCompleted_p, EVENT_NAME_QUERY_COMPLETED);
    }

    
    /**
     * TODO
     */
    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        throw new UnsupportedOperationException();
    }

}

package org.alfresco.bm.cmis;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.file.TestFileService;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mongodb.BasicDBObjectBuilder;

/**
 * Query a folder by executing a CMIS search for properties
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object }.
 * 
 * <h1>Actions</h1>
 * 
 * Executes a CMIS property search, stores the folder in the event data
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_QUERY_COMPLETED}: the {@link CMISEventData data object} <br/>
 * 
 * @author Frank Becker
 * @since 1.3
 */
public class QueryFolder extends AbstractQueryCMISEventProcessor
{
    /** default event name of the next event */
    public static final String EVENT_NAME_QUERY_COMPLETED = "cmis.folderQueryCompleted";

    /** Logger for the class */
    private static Log logger = LogFactory.getLog(QueryFolder.class);
    
    /**
     * Constructor
     * 
     * @param testFileService_p
     *            (TestFileService, required if queryFileName_p is provided) test file service
     * 
     * @param queryFileName_p
     *            (String, optional) name of file that contains the search strings for the folders. If null, the content
     *            of a resource file will be loaded instead.
     * 
     * @param eventNameQueryCompleted_p
     *            (String, optional) name of next event or null/empty if to use default
     *            {@link #EVENT_NAME_QUERY_COMPLETED}
     */
    public QueryFolder(TestFileService testFileService_p, String queryFileName_p, String eventNameQueryCompleted_p)
    {
        super(testFileService_p, queryFileName_p, eventNameQueryCompleted_p, EVENT_NAME_QUERY_COMPLETED);
    }

    

    /**
     * TODO
     */
    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        // Timer control
        super.suspendTimer();

        // get event data
        CMISEventData data = (CMISEventData) event.getData();
        if (data == null)
        {
            return new EventResult("Unable to query CMIS folder: no session provided.", false);
        }
        
        // check query strings and random select one
        // TODO

        // TODO
        Folder folder = null;

        // Timer control
        super.stopTimer();

        // Done
        Event doneEvent = new Event(getEventNameQueryCompleted(), data);
        EventResult result = new EventResult(BasicDBObjectBuilder.start()
                .append("msg", "Successfully query a folder.").push("folder").append("id", folder.getId())
                .append("name", folder.getName()).pop().get(), doneEvent);

        // Done
        return result;
    }

}

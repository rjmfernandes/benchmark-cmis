package org.alfresco.bm.cmis;

import com.mongodb.BasicDBObjectBuilder;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.file.TestFileService;
import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Iterator;

/**
 * Query a folder by executing a CMIS search for properties
 * 
 * <h1>Input</h1>
 * 
 * A {@link org.alfresco.bm.cmis.CMISEventData data object }.
 *
 * <h1>Actions</h1>
 *
 * Executes a CMIS property search
 *
 * <h1>Output</h1>
 *
 * {@link #EVENT_NAME_QUERY_COMPLETED}: the {@link org.alfresco.bm.cmis.CMISEventData data object} <br/>
 *
 * @author Rui Fernandes
 */
public class QueryFolderFromData extends AbstractCMISEventProcessor
{
    /** default event name of the next event */
    public static final String EVENT_NAME_QUERY_COMPLETED = "cmis.folderQueryCompleted";

    /** Logger for the class */
    private static Log logger = LogFactory.getLog(QueryFolderFromData.class);

    private String eventNameFolderQueried;

    public QueryFolderFromData()
    {
        eventNameFolderQueried=EVENT_NAME_QUERY_COMPLETED;
    }

    public void setEventNameFolderQueried(String eventName)
    {
        this.eventNameFolderQueried = eventName;
    }

    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Entering 'QueryFolderFromData' event processor.");
        }

        // Timer control
        super.suspendTimer();

        // get event data
        CMISEventData data = (CMISEventData) event.getData();
        if (data == null)
        {
            logger.warn("Unable to query CMIS folder: no session provided.");
            return new EventResult("Unable to query CMIS folder: no session provided.", false);
        }

        if (data.getBreadcrumb().isEmpty())
        {
            return new EventResult("Unable to query folder; no folder provided.", false);
        }
        Folder folderBreadcrumb = data.getBreadcrumb().getLast();

        String query = String.format("select cmis:objectId from cmis:folder where cmis:name='%s'",folderBreadcrumb.getName());
        Session session = data.getSession();
        String objectId = null;

        super.resumeTimer();

        // execute query
        ItemIterable<QueryResult> results = session.query(query, false);
        Iterator<QueryResult> it = results.iterator();

        while (it.hasNext())
        {
            QueryResult queryResult = null;
            try
            {
                queryResult = it.next();
            }
            catch (Exception e)
            {
                logger.error("Unable to get next folder query result.", e);
                continue;
            }

            objectId = (String)queryResult.getPropertyById("cmis:objectId").getFirstValue();
            if (logger.isDebugEnabled())
            {
                logger.debug("Found folder with ID '" + objectId + "'.");
            }
            break;
        }

        // Timer control
        super.stopTimer();

        Event doneEvent = new Event(eventNameFolderQueried, data);

        if (null != objectId)
        {
            // Done & found folder
            EventResult result = new EventResult(BasicDBObjectBuilder.start()
                    .append("msg", "Successfully query a folder.").push("folder").append("id", folderBreadcrumb.getId())
                    .append("name", folderBreadcrumb.getName()).pop().get(), doneEvent);

            // Done
            return result;
        }

        // failed
        if (logger.isDebugEnabled())
        {
            logger.debug("Query didn't return any accessible folder: '" + query + "'");
        }
        return new EventResult(BasicDBObjectBuilder.start().append("msg", "Failed query a folder.").push("folder")
                .pop().get(), doneEvent);
    }

}

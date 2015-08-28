package org.alfresco.bm.cmis;

import com.mongodb.BasicDBObjectBuilder;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.file.TestFileService;
import org.apache.chemistry.opencmis.client.api.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Iterator;

/**
 * Query a document by executing a CMIS search for properties
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
public class QueryDocumentFromData extends AbstractCMISEventProcessor
{
    /** default event name of the next event */
    public static final String EVENT_NAME_QUERY_COMPLETED = "cmis.documentQueryCompleted";

    /** Logger for the class */
    private static Log logger = LogFactory.getLog(QueryDocumentFromData.class);
    private String eventNameDocumentQueried;


    public QueryDocumentFromData()
    {
        eventNameDocumentQueried=EVENT_NAME_QUERY_COMPLETED;
    }

    public void setEventNameDocumentQueried(String eventNameDocumentQueried) {
        this.eventNameDocumentQueried = eventNameDocumentQueried;
    }


    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Entering 'QueryDocumentFromData' event processor.");
        }

        // Timer control
        super.suspendTimer();

        // get event data
        CMISEventData data = (CMISEventData) event.getData();
        if (data == null)
        {
            logger.warn("Unable to query CMIS document: no session provided.");
            return new EventResult("Unable to query CMIS document: no session provided.", false);
        }
        Document document = data.getDocument();
        if (document == null)
        {
            logger.warn("Unable to query CMIS document: no session provided.");
            return new EventResult("Unable to query CMIS document: no session provided.", false);
        }

        String query = String.format("select cmis:objectId from cmis:document where cmis:name='%s'",document.getName());
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
                logger.error("Unable to get next document query result.", e);
                continue;
            }

            objectId = (String)queryResult.getPropertyById("cmis:objectId").getFirstValue();
            if (logger.isDebugEnabled())
            {
                logger.debug("Found document with ID '" + objectId + "'.");
            }
            break;
        }

        // Timer control
        super.stopTimer();

        Event doneEvent = new Event(eventNameDocumentQueried, data);

        if (null != objectId)
        {
            // Done & found folder
            EventResult result = new EventResult(BasicDBObjectBuilder.start()
                    .append("msg", "Successfully query a document.").push("folder").append("id", document.getId())
                    .append("name", document.getName()).pop().get(), doneEvent);

            // Done
            return result;
        }

        // failed
        if (logger.isDebugEnabled())
        {
            logger.debug("Query didn't return any accessible document: '" + query + "'");
        }
        return new EventResult(BasicDBObjectBuilder.start().append("msg", "Failed query a document.").push("document")
                .pop().get(), doneEvent);
    }

}

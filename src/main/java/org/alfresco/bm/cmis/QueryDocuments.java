package org.alfresco.bm.cmis;

import java.util.ArrayList;
import java.util.List;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.file.TestFileService;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

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
    public static final String EVENT_NAME_QUERY_COMPLETED = "cmis.documentQueryCompleted";

    /** default name of event to create if all document events are processed */
    public static final String EVENT_NAME_DOCUMENTS_QUERY_COMPLETED = "cmis.documentsQueryCompleted";

    /** Stores the name and location in the resources of the document query file */
    public static final String RESSOURCE_QUERY_FILENAME = "config/documentsQuery.txt";

    /** Stores the final event raised if all document events are done */
    private String eventNameDocumentsQueryCompleted;

    /** stores the page size of each document query */
    private long pageSize;

    /** stores the delay [ms] between document events */
    private long delayDocumentEventsMs;

    /** stores the delay [ms] between re-loops of this event processor */
    private long delayReLoppEventMs;

    /**
     * Constructor
     * 
     * @param testFileService_p
     *            (TestFileService, required if queryFileName_p is set) test file service if to retrieve query from test
     *            file service.
     * 
     * @param queryFileName_p
     *            (String) file name to retrieve from test file service or null if to use resource
     *            {@link #RESSOURCE_QUERY_FILENAME}
     * 
     * @param eventNameDocumentsQueryCompleted_p
     *            (String, optional) name of the next event if all document events are "fired". Null to use
     *            {@link #EVENT_NAME_DOCUMENTS_QUERY_COMPLETED}
     * 
     * @param eventNameQueryCompleted_p
     *            (String) name of event to start for each document or null/empty if to use default
     *            {@link #EVENT_NAME_QUERY_COMPLETED}
     * 
     * @param documentEventDelayMs_p
     *            (long) Delay between document events
     * 
     * @param documentEventsPerLoopMax_p
     *            (long) number of documents per "page" - restricts number of fired parallel events for documents
     * 
     * @param reLoopDelayMs_p
     *            (long) delay in [ms] between re-loops of this event
     */
    public QueryDocuments(long documentEventDelayMs_p, long documentEventsPerLoopMax_p, long reLoopDelayMs_p,
            TestFileService testFileService_p, String queryFileName_p, String eventNameQueryCompleted_p,
            String eventNameDocumentsQueryCompleted_p)
    {
        super(testFileService_p, queryFileName_p, eventNameQueryCompleted_p, EVENT_NAME_QUERY_COMPLETED);

        // store page processing values
        this.delayDocumentEventsMs = documentEventDelayMs_p;
        this.delayReLoppEventMs = reLoopDelayMs_p;
        this.pageSize = documentEventsPerLoopMax_p;

        // save event name of finished starting document events
        this.eventNameDocumentsQueryCompleted = (null == eventNameDocumentsQueryCompleted_p || eventNameDocumentsQueryCompleted_p
                .isEmpty()) ? EVENT_NAME_DOCUMENTS_QUERY_COMPLETED : eventNameDocumentsQueryCompleted_p;
    }

    /**
     * event processing
     */
    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        // Timer control
        super.suspendTimer();

        // create list of next events to process
        List<Event> nextEvents = new ArrayList<Event>();

        // get event data to get session
        CMISEventData data = (CMISEventData) event.getData();
        if (null == data)
        {
            return new EventResult("Unable to execute CMIS query: no session provided.", false);
        }
        Session session = data.getSession();

        // Timer control
        super.resumeTimer();

        // get the query to execute
        String query = getQuery(data);

        // check if a re-loop is to be done, if not, store query in event data now
        if (null == data.getQuery())
        {
            data.setQuery(query);
        }
        long pageCount = data.getPageCount();
        long currentPage = 0;
        long docCount = 0;
        boolean moreWorkToDo = false;

        // execute query
        ItemIterable<QueryResult> results = session.query(query, false);
        for (QueryResult queryResult : results)
        {
            if (pageCount > 0)
            {
                // skip documents already processed
                docCount++;
                if (docCount > this.pageSize)
                {
                    pageCount--;
                    docCount = 0;
                    currentPage++;
                }
            }
            else
            {
                long nextEventTime = System.currentTimeMillis() + this.delayDocumentEventsMs;

                // store current page
                data.setPageCount(currentPage);

                // get document object from CMIS and store it to new document event data
                String objectId = queryResult.getPropertyValueByQueryName(data.getObjectIdQueryName());
                Document doc = null;
                try
                {
                    doc = (Document) session.getObject(session.createObjectId(objectId));
                }
                catch(Exception e)
                {
                    logger.error("Unable to create document from object with ID '" + objectId + "'.", e);
                }
                if (null != doc)
                {
                    CMISEventData docEventData = new CMISEventData(data);
                    docEventData.setDocument(doc);
                    docCount++;

                    // create as much document events as configured (paging)
                    // and re-loop
                    Event nextEvent = new Event(super.getEventNameQueryCompleted(), nextEventTime, docEventData);
                    nextEvents.add(nextEvent);

                    if (logger.isDebugEnabled())
                    {
                        logger.debug("Scheduled document '" + objectId + "' for event processing.");
                    }

                    if (docCount > this.pageSize)
                    {
                        moreWorkToDo = true;
                        break;
                    }
                }
                else // if (null != doc)
                {
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("Object with ID '" + objectId + "' is not a document ... skipping ...");
                    }
                }
            }
        }

        if (moreWorkToDo)
        {
            // re-loop: schedule self
            Event nextEvent = new Event(event.getName(), System.currentTimeMillis() + this.delayReLoppEventMs, data);
            nextEvents.add(nextEvent);
        }
        else
        {
            // no more documents left to process? Finish
            super.stopTimer();

            Event nextEvent = new Event(eventNameDocumentsQueryCompleted, data);
            nextEvents.add(nextEvent);
        }

        DBObject dataObj = new BasicDBObject().append("Query", query).append("PageCount", currentPage)
                .append("DocCountInPage", docCount);
        return new EventResult(dataObj, nextEvents);
    }

    /**
     * Retrieves and returns the query to execute
     */
    private String getQuery(CMISEventData data_p)
    {
        String query = data_p.getQuery();
        // check if we are "re-looping" and continue with next "page" if
        if (null != query && !query.isEmpty())
        {
            // simple re-loop - return query
            return query;
        }

        // else load query file (either from resources or test file service)
        String[] queryStrings = super.getQueryStrings(RESSOURCE_QUERY_FILENAME, logger);

        // random select next query
        query = super.getRandomSearchString(queryStrings);
        checkStringArgument("query", query);
        if (!query.startsWith("SELECT "))
        {
            throw new RuntimeException("Query '" + query + "': supporting 'SELECT' only ...");
        }
        if (query.contains(";"))
        {
            throw new RuntimeException("Query '" + query + "': single CMIS SQL statements only, please ...");
        }

        // get type from query
        int pos = query.indexOf(QUERY_TYPE_VALUE_STRING);
        if (pos > 0)
        {
            String typeValue = query.substring(pos + QUERY_TYPE_VALUE_STRING.length());
            checkStringArgument(QUERY_TYPE_VALUE_STRING, typeValue);

            // cut query
            query = query.substring(0, pos);

            // replace query variables:

            // FOLDER ID
            pos = query.indexOf(QUERY_FOLDERID_FIELDNAME);
            if (pos > 0)
            {
                // check whether we have a folder or not
                Folder folder = (data_p.getBreadcrumb().isEmpty()) ? null : data_p.getBreadcrumb().getLast();
                // query contains a folder ID value - folder is mandatory!
                if (null == folder)
                {
                    throw new RuntimeException("Query '" + query + "' contains '" + QUERY_FOLDERID_FIELDNAME
                            + "', but no folder selected by previous benchmark events!");
                }
                query.replace(QUERY_FOLDERID_FIELDNAME, folder.getId());
            }

            // TYPE
            ObjectType type = null;
            pos = query.indexOf(QUERY_TYPE_FIELDNAME);
            if (pos > 0)
            {
                type = data_p.getSession().getTypeDefinition(typeValue);
                query.replace(QUERY_TYPE_FIELDNAME, type.getQueryName());
            }
            else
            {
                throw new RuntimeException("Query '" + query + "': missing mandatory '" + QUERY_TYPE_FIELDNAME + "'!");
            }

            // OBJECT ID
            pos = query.indexOf(QUERY_OBJECT_ID_FIELDNAME);
            if (pos > 0)
            {
                PropertyDefinition<?> objectIdPropDef = type.getPropertyDefinitions().get(PropertyIds.OBJECT_ID);
                query.replace(QUERY_OBJECT_ID_FIELDNAME, objectIdPropDef.getQueryName());

                // also store in event data!
                data_p.setObjectIdQueryName(objectIdPropDef.getQueryName());
            }
            else
            {
                throw new RuntimeException("Query '" + query + "': missing mandatory '" + QUERY_OBJECT_ID_FIELDNAME
                        + "'!");
            }
        }
        else
        // if (pos > 0)
        {
            throw new RuntimeException("Query '" + query + "' doesn't contain '" + QUERY_TYPE_VALUE_STRING + "'!");
        }

        checkStringArgument("CMIS Query", query);
        return query;
    }

}

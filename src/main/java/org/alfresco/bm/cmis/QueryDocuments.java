package org.alfresco.bm.cmis;

import java.util.Iterator;
import java.util.Random;

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
    public static final String EVENT_NAME_QUERY_COMPLETED = "cmis.queryCompleted";

    /** default name of event to create if all document events are processed */
    public static final String EVENT_NAME_DOCUMENTS_QUERY_COMPLETED = "cmis.documentQueryCompleted";

    /** Stores the name and location in the resources of the document query file */
    public static final String RESSOURCE_QUERY_FILENAME = "config/documentsQuery.txt";

    /** stores the page size of each document query */
    private long maxResults;

    /** Stores the object ID query name */
    private String objectIdQueryName;

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
     * @param eventNameQueryCompleted_p
     *            (String) name of event to start for each document or null/empty if to use default
     *            {@link #EVENT_NAME_QUERY_COMPLETED}
     * 
     * @param maxResultsToProcess_p
     *            (long) max number of documents to process
     */
    public QueryDocuments(TestFileService testFileService_p, String queryFileName_p, long maxResultsToProcess_p,
            String eventNameQueryCompleted_p)
    {
        super(testFileService_p, queryFileName_p, eventNameQueryCompleted_p, EVENT_NAME_QUERY_COMPLETED);

        this.maxResults = maxResultsToProcess_p;
        if (this.maxResults < 0)
        {
            throw new IllegalArgumentException("'maxResultsToProcess_p': expected positive value or 0.");
        }
    }

    /**
     * event processing
     */
    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Entering 'QueryDocuments' event processor.");
        }
        
        // Timer control
        super.suspendTimer();

        // get event data / CMIS session
        CMISEventData data = (CMISEventData) event.getData();
        if (null == data)
        {
            logger.warn("Unable to execute CMIS query: no session provided.");
            return new EventResult("Unable to execute CMIS query: no session provided.", false);
        }
        Session session = data.getSession();

        // Timer control
        super.resumeTimer();

        // get the query to execute
        String query = getQuery(data);
        long docCount = 0;

        // execute query
        ItemIterable<QueryResult> results = session.query(query, false);
        Iterator<QueryResult> it = results.iterator();

        // Random chose a document from query
        Random random = new Random();
        long resultCount = results.getTotalNumItems();
        int chose = 0;
        if (resultCount > 0) 
        {
            chose = random.nextInt(resultCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) resultCount);
        }
        
        // for (QueryResult queryResult : results) note - throws exceptions sometimes (item not found) - replacing by
        // "safer" code ...
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

            // get document object from CMIS and store it to new document event data
            String objectId = queryResult.getPropertyValueByQueryName(this.objectIdQueryName);
            Document doc = null;
            try
            {
                doc = (Document) session.getObject(session.createObjectId(objectId));
            }
            catch (Exception e)
            {
                logger.error("Unable to create document from object with ID '" + objectId + "'.", e);
            }
            if (null != doc)
            {
                // store if chosen document is found and no document stored so far
                if (null == data.getDocument() && docCount == (long)chose)
                {
                    data.setDocument(doc);
                }
                
                // count number and store object ID to data object for further processing
                docCount++;
                if (docCount <= this.maxResults)
                {
                    data.getObjectIds().add(objectId);
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("Stored document no. " + docCount + " ID '" + objectId + "' for event processing.");
                    }
                }
            }
            else
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Object with ID '" + objectId + "' is not a document or missing ... skipping.");
                }
            }
        }

        // no more documents left to process? Finish
        if (logger.isDebugEnabled())
        {
            logger.debug("Document query completed.");
        }
        super.stopTimer();

        Event nextEvent = new Event(super.getEventNameQueryCompleted(), data);
        DBObject dataObj = new BasicDBObject().append("Query", query).append("DocCount", docCount)
                .append("Docs for processing", data.getObjectIds().size());
        return new EventResult(dataObj, nextEvent);
    }

    /**
     * Retrieves and returns the query to execute
     */
    private String getQuery(CMISEventData data_p)
    {
        // load query file (either from resources or test file service)
        String[] queryStrings = super.getQueryStrings(RESSOURCE_QUERY_FILENAME, logger);

        // random select next query
        String query = super.getRandomSearchString(queryStrings);
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
            String typeValue = query.substring(pos + QUERY_TYPE_VALUE_STRING.length()).trim();
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
                query = query.replace(QUERY_FOLDERID_FIELDNAME, folder.getId());
            }

            // TYPE
            ObjectType type = null;
            pos = query.indexOf(QUERY_TYPE_FIELDNAME);
            if (pos > 0)
            {
                type = data_p.getSession().getTypeDefinition(typeValue);
                query = query.replace(QUERY_TYPE_FIELDNAME, type.getQueryName());
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
                this.objectIdQueryName = objectIdPropDef.getQueryName();
                query = query.replace(QUERY_OBJECT_ID_FIELDNAME, this.objectIdQueryName);
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

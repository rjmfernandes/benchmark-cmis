package org.alfresco.bm.cmis;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.file.TestFileService;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.client.api.QueryResult;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
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

    /** Stores the name and location in the resources of the document query file */
    public static final String RESSOURCE_QUERY_FILENAME = "config/folderQuery.txt";

    /** Logger for the class */
    private static Log logger = LogFactory.getLog(QueryFolder.class);

    /** Stores the object ID query name */
    private String objectIdQueryName;
    
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

    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        // Timer control
        super.suspendTimer();

        // get event data
        CMISEventData data = (CMISEventData) event.getData();
        if (data == null)
        {
            logger.warn("Unable to query CMIS folder: no session provided.");
            return new EventResult("Unable to query CMIS folder: no session provided.", false);
        }

        // check query strings and random select one
        String query = getQuery(data);
        Session session = data.getSession();
        Folder folder = null;
        
        // execute query
        ItemIterable<QueryResult> results = session.query(query, false);
        for (QueryResult queryResult : results)
        {
            // get folder object from CMIS and store it to bread-crumb event data
            String objectId = queryResult.getPropertyValueByQueryName(this.objectIdQueryName);
            try
            {
                folder = (Folder) session.getObject(session.createObjectId(objectId));
                data.getBreadcrumb().add(folder);
                if (logger.isDebugEnabled())
                {
                    logger.debug("Found folder with ID '" + objectId + "'.");
                }
                // TODO add all folders to bread-crumb?
                break;
            }
            catch(Exception e)
            {
                logger.error("Unable to create folder from object with ID '" + objectId + "'.", e);
            }
        }
        

        // Timer control
        super.stopTimer();
        
        Event doneEvent = new Event(getEventNameQueryCompleted(), data);

        if (null != folder)
        {
            // Done
            EventResult result = new EventResult(
                    BasicDBObjectBuilder.start()
                        .append("msg", "Successfully query a folder.")
                        .push("folder")
                        .append("id", folder.getId())
                        .append("name", folder.getName())
                        .pop()
                        .get(), doneEvent);
            
            
            // Done
            return result;
        }
        
        // failed 
        return new EventResult(data, false);
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

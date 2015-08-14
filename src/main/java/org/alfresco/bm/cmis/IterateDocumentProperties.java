package org.alfresco.bm.cmis;

import java.util.Iterator;
import java.util.List;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Property;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mongodb.BasicDBObjectBuilder;

/**
 * Iterates the CMIS properties of a document
 * 
 * <h1>Input</h1>
 * 
 * A {@link CMISEventData data object } that should contain a document.
 * 
 * <h1>Actions</h1>
 * 
 * Executes a CMIS property iteration on the document in the event data.
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_ITERATE_COMPLETED}: the {@link CMISEventData data object} <br/>
 * 
 * @author Frank Becker
 * @since 1.3
 */
public class IterateDocumentProperties extends AbstractCMISEventProcessor
{
    /** Logger for the class */
    private static Log logger = LogFactory.getLog(IterateDocumentProperties.class);

    /** default event name of the next event */
    public static final String EVENT_NAME_ITERATE_COMPLETED = "cmis.iteratePropertiesCompleted";

    /** Name of the next event */
    private String eventNameIterateCompleted;

    /**
     * Constructor
     * 
     * @param eventNameIterateCompleted_p
     *            (String) name of next event or default
     */
    public IterateDocumentProperties(String eventNameIterateCompleted_p)
    {
        setEventNameIterateCompleted(eventNameIterateCompleted_p);
    }

    /**
     * Sets the event name of the next event.
     * 
     * @param eventNameIterateCompleted_p
     *            (String) event name or null/empty to use default {@link #EVENT_NAME_ITERATE_COMPLETED}
     */
    public void setEventNameIterateCompleted(String eventNameIterateCompleted_p)
    {
        // store next event name
        if (null == eventNameIterateCompleted_p || eventNameIterateCompleted_p.isEmpty())
        {
            this.eventNameIterateCompleted = EVENT_NAME_ITERATE_COMPLETED;
        }
        else
        {
            this.eventNameIterateCompleted = eventNameIterateCompleted_p;
        }
    }

    /**
     * Iterates the document properties
     */
    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Entering 'IterateDocumentProperties' event processor.");
        }
        
        // timer control
        super.suspendTimer();

        // get event data
        CMISEventData data = (CMISEventData) event.getData();
        if (data == null)
        {
            logger.warn("Unable to iterate CMIS properties: no session provided");
            return new EventResult("Unable to iterate CMIS properties: no session provided.", false);
        }

        // get document
        Document document = data.getDocument();
        if (null == document)
        {

            logger.warn("Unable to iterate CMIS properties: no document provided.");
            return new EventResult("Unable to iterate CMIS properties: no document provided.", false);
        }

        // Timer control
        super.resumeTimer();

        // iterate properties
        List<Property<?>> l = document.getProperties();
        Iterator<Property<?>> i = l.iterator();
        int totalProps = 0;
        String docMSg = "";
        if (logger.isDebugEnabled())
        {
            docMSg = "Document '" + document.getName() + " (" + document.getId() + ")' found property '";
        }
        while (i.hasNext())
        {
            Property<?> p = i.next();
            String name = p.getLocalName();
            PropertyType t = p.getType();
            totalProps++;

            if (logger.isDebugEnabled())
            {
                logger.debug(docMSg + name + "', type '" + t.toString() + "'");
            }
        }

        // Timer control
        super.stopTimer();

        // Done
        Event doneEvent = new Event(this.eventNameIterateCompleted, data);
        EventResult result = new EventResult(BasicDBObjectBuilder.start()
                .append("msg", "Successfully iterated document properties.").append("totalProperties", totalProps)
                .push("document").append("id", document.getId()).append("name", document.getName()).push("properties")
                .pop().get(), doneEvent);
        return result;
    }

}

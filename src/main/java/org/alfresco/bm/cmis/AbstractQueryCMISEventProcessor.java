package org.alfresco.bm.cmis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;

import org.alfresco.bm.file.TestFileService;
import org.apache.commons.logging.Log;

/**
 * Base class for CMIS query event processors
 * 
 * @author Frank Becker
 * @since 1.3
 */
public abstract class AbstractQueryCMISEventProcessor extends AbstractCMISEventProcessor
{
    private final static String COMMON_ERROR_MSG = "Unable to get queries from query file or resource!";

    /** Stores the name of the "type" part in the query string */
    public static final String QUERY_TYPE_VALUE_STRING = "::Type=";

    /** Stores the name of the "Folder ID" field in the query string */
    public static final String QUERY_FOLDERID_FIELDNAME = "{{{FolderId}}}";

    /** Stores the name of the "Type Query Name" field in the query string */
    public static final String QUERY_TYPE_FIELDNAME = "{{{TypeQueryName}}}";

    /** Stores the name of the "Type Query Name" field in the query string */
    public static final String QUERY_OBJECT_ID_FIELDNAME = "{{{ObjectIdQueryName}}}";

    /** Name of the next event */
    private String eventNameQueryCompleted;

    /** test file service */
    private TestFileService testFileService;

    /** Name of the query file to get from test file service */
    private String queryFileName;

    public AbstractQueryCMISEventProcessor(TestFileService testFileService_p, String queryFileName_p,
            String eventNameQueryCompleted_p, String defaultEventNameQueryCompleted_p)
    {
        setEventNameQueryCompleted(eventNameQueryCompleted_p, defaultEventNameQueryCompleted_p);
        this.testFileService = testFileService_p;
        this.queryFileName = queryFileName_p;
    }

    /**
     * Sets the event name of the next event.
     * 
     * @param eventNameQueryCompleted_p
     *            (String) event name or null/empty to use default
     * 
     * @param defaultValue_p
     *            (String, required) default value if eventNameQueryCompleted_p is null or empty
     */
    public void setEventNameQueryCompleted(String eventNameQueryCompleted_p, String defaultValue_p)
    {
        // store next event name
        if (null == eventNameQueryCompleted_p || eventNameQueryCompleted_p.isEmpty())
        {
            this.eventNameQueryCompleted = defaultValue_p;
        }
        else
        {
            this.eventNameQueryCompleted = eventNameQueryCompleted_p;
        }
    }

    /**
     * @return (String) event name of next event
     */
    public String getEventNameQueryCompleted()
    {
        return this.eventNameQueryCompleted;
    }

    /**
     * Gets the query strings either from the test file service or from the embedded resource file.
     * 
     * @param resourceFileName_p
     *            (String, required) name with relative path of the embedded resource file
     * 
     * @param logger_p
     *            (Log, required) Log4J logger
     * 
     * @return (String [])
     */
    protected String[] getQueryStrings(String resourceFileName_p, Log logger_p)
    {
        InputStream inputStream = null;

        // check if we have a search file from the test file service
        if (null != testFileService && null != this.queryFileName && !this.queryFileName.isEmpty())
        {
            File file = this.testFileService.getFileByName(this.queryFileName);
            if (null != file && file.isFile())
            {
                try
                {
                    inputStream = new FileInputStream(file);

                    // file found - use this one
                    return readLines(inputStream, logger_p);
                }
                catch (FileNotFoundException e)
                {
                    logger_p.error("Unable to open query file '" + this.queryFileName
                            + "' - will fall-back to ressource file instead!", e);
                }
            }
            else
            {
                logger_p.warn("Unable to get query file '" + this.queryFileName
                        + "' from test file service - will fall-back to ressource query file!");

                // TODO log service
            }
        }
        // no search file from the test file service - try to get the ones from resource
        inputStream = AbstractQueryCMISEventProcessor.class.getClassLoader().getResourceAsStream(resourceFileName_p);

        if (null == inputStream)
        {
            throw new RuntimeException("Unable to open resource file '" + resourceFileName_p + "'!");
        }

        return readLines(inputStream, logger_p);
    }

    /**
     * Returns the lines of a text file as array of strings
     * 
     * @param inputStream_p
     *            (InputStream) input stream
     * @param logger_p
     *            Log4J logger
     * @return lines of the text file as array or a runtime exception
     */
    protected String[] readLines(InputStream inputStream_p, Log logger_p)
    {
        ArrayList<String> strings = new ArrayList<String>(500);

        try
        {
            Reader reader = new InputStreamReader(inputStream_p);
            BufferedReader br = new BufferedReader(reader);
            String line;

            while ((line = br.readLine()) != null)
            {
                // ignore comment lines on reading
                if (null != line && !line.isEmpty() && !line.startsWith("'"))
                {
                    strings.add(line);
                }
            }

        }
        catch (Exception e)
        {
            logger_p.error(COMMON_ERROR_MSG, e);
        }
        finally
        {
            if (null != inputStream_p)
            {
                try
                {
                    inputStream_p.close();
                }
                catch (IOException e)
                {
                }
            }
        }

        // Check
        if (0 == strings.size())
        {
            throw new RuntimeException(COMMON_ERROR_MSG);
        }

        // Push into an array
        return (String[]) strings.toArray(new String[strings.size()]);
    }

    /**
     * Quick string argument check.
     * 
     * @param argumentName_p
     *            (String, required) argument name
     * @param argument_p
     *            (string) for validation
     */
    public void checkStringArgument(String argumentName_p, String argument_p)
    {
        if (null == argument_p || argument_p.isEmpty())
        {
            throw new RuntimeException("Argument '" + argumentName_p + "' is mandatory!");
        }
    }
}

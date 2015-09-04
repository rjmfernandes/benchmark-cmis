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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.file.TestFileService;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.impl.jaxb.CmisException;
import org.apache.commons.lang3.exception.ExceptionUtils;

import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

/**
 * Abstract event processing to handle CMIS-specific behavior e.g. the catch-and-report of {@link CmisException}.
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public abstract class AbstractCMISEventProcessor extends AbstractEventProcessor
{
    /**
     * {@inheritDoc}
     */
    protected abstract EventResult processCMISEvent(Event event) throws Exception;
    
    public final EventResult processEvent(Event event) throws Exception
    {
        try
        {
            return processCMISEvent(event);
        }
        catch (CmisRuntimeException e)
        {
            String error = e.getMessage();
            String stack = ExceptionUtils.getStackTrace(e);
            // Grab the CMIS information
            DBObject data = BasicDBObjectBuilder
                    .start()
                    .append("msg", error)
                    .append("stack", stack)
                    .push("cmisFault")
                        .append("code", "" + e.getCode())               // BigInteger is not Serializable
                        .append("errorContent", e.getErrorContent())
                    .pop()
                    .get();
            
            // Build failure result
            return new EventResult(data, false);
        }
        catch(Exception genEx)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("General exception in CMIS benchmark.", genEx);
            }
            throw genEx;
        }
    }
    
    /** Some default search strings when no file is found with them */
    public static final String[] DEFAULT_SEARCH_STRINGS = new String[] {
            "\"quick\"",
            "Sa*",
            "alfresco",
            "ipsum",
            "\"ipsum lorum\""
            };
    public static final String DEFAULT_SEARCH_TERMS_FILENAME = "searchterms.txt";
    
    /**
     * Static helper to find and extract a search term from the given file
     * 
     * @param testFileService           the test files
     * @param searchTermsFilename       the name of the file to find
     * @return                          a list of search terms (empty if there are none or the remote file does not exist)
     */
    public static String[] getSearchStrings(TestFileService testFileService, String searchTermsFilename)
    {
        File file = testFileService.getFileByName(searchTermsFilename);
        if (file == null)
        {
            return new String[0];
        }
        FileReader fr = null;
        try
        {
            fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            ArrayList<String> strings = new ArrayList<String>(500);
            String line;
            while ((line = br.readLine()) != null)
            {
                strings.add(line);
            }
            // Check
            if (strings.size() == 0)
            {
                throw new RuntimeException("No search strings in file: " + searchTermsFilename);
            }
            // Push into an array
            return (String[]) strings.toArray(new String[strings.size()]);
        }
        catch (IOException e)
        {
            throw new RuntimeException(
                    "Failed to read search strings from file '" + file +
                    "' loaded as '" + searchTermsFilename);
        }
        finally
        {
            if (fr != null)
            {
                try { fr.close(); } catch (IOException e) {}
            }
        }
    }
    
    private static final Random RANDOM = new Random();
    /**
     * Choose a random string from the search strings provided
     */
    public static String getRandomSearchString(String[] searchStrings)
    {
        if (searchStrings == null || searchStrings.length == 0)
        {
            throw new IllegalArgumentException("No search strings to choose from.");
        }
        return searchStrings[RANDOM.nextInt(searchStrings.length)];
    }
}

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
package org.alfresco.bm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventRecord;
import org.alfresco.bm.event.ResultService;
import org.alfresco.bm.test.TestRunServicesCache;
import org.alfresco.bm.tools.BMTestRunner;
import org.alfresco.bm.tools.BMTestRunnerListener;
import org.alfresco.bm.tools.BMTestRunnerListenerAdaptor;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataServiceImpl;
import org.alfresco.mongo.MongoDBFactory;
import org.alfresco.mongo.MongoDBForTestsFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.context.ApplicationContext;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

/**
 * Execute the CMIS load test against no existing server, which will validate that the
 * test is properly structured to start.
 * 
 * @author Derek Hulley
 * @since 1.0
 */
@RunWith(JUnit4.class)
public class BMCmisTest extends BMTestRunnerListenerAdaptor
{
    private static Log logger = LogFactory.getLog(BMCmisTest.class);
    
    private MongoDBForTestsFactory dbFactory;
    private DB testDB;
    private String testDBHost;
    
    /**
     * We need access to the test DB in order to access the users
     */
    @Before
    public void setUp() throws Exception
    {
        dbFactory = new MongoDBForTestsFactory();
        String uriWithoutDB = dbFactory.getMongoURIWithoutDB();
        testDBHost = new MongoClientURI(uriWithoutDB).getHosts().get(0);
        testDB = new MongoDBFactory(new MongoClient(testDBHost), "bm20-data").getObject();
        
        // Create a user for use
        UserDataServiceImpl userDataService = new UserDataServiceImpl(testDB, "mirrors.cmis.alfresco.com.users");
        userDataService.afterPropertiesSet();
        
        UserData user = new UserData();
        user.setUsername("admin");
        user.setPassword("admin");
        user.setCreationState(DataCreationState.Created);
        // The rest is not useful for this specific test
        {
            user.setEmail("bmarley@reggae.com");
            user.setDomain("reggae");
            user.setFirstName("Bob");
            user.setLastName("Marley");
        }
        userDataService.createNewUser(user);
    }
    
    @After
    public void tearDown() throws Exception
    {
        if (dbFactory != null)
        {
            dbFactory.destroy();
        }
    }
    
    @Test
    public void runSample() throws Exception
    {
        BMTestRunner runner = new BMTestRunner(60000L);         // Should be done in 60s
        runner.addListener(this);
        runner.run(null, testDBHost, null);
    }

    /**
     * A listener method that allows the test to check results <b>before</b> the in-memory MongoDB instance
     * is discarded.
     * <p/>
     * Check that the exact number of results are available, as expected
     * 
     * @see BMTestRunnerListener
     */
    @Override
    public void testRunFinished(ApplicationContext testCtx, String test, String run)
    {
        TestRunServicesCache services = testCtx.getBean(TestRunServicesCache.class);
        ResultService resultService = services.getResultService(test, run);
        assertNotNull(resultService);
        // Let's check the results before the DB gets thrown away (we didn't make it ourselves)
        
        // One successful START event
        assertEquals("Incorrect number of start events.", 1, resultService.countResultsByEventName(Event.EVENT_NAME_START));
        List<EventRecord> results = resultService.getResults(0L, Long.MAX_VALUE, false, 0, 1);
        if (results.size() != 1 || !results.get(0).getEvent().getName().equals(Event.EVENT_NAME_START))
        {
            fail(Event.EVENT_NAME_START + " failed: \n" + results.toString());
        }
        
        /*
         * 'start' = 1 result
         * 'cmis.createSessions' = 2 results
         * 'cmis.scenario.01.startSession' = 200 results
         * Successful processing generates a No-op for each 
         */
        Set<String> expectedEventNames = new TreeSet<String>();
        expectedEventNames.add("start");
        expectedEventNames.add("cmis.createSessions");
        expectedEventNames.add("cmis.startSession");
        expectedEventNames.add("cmis.scenario.01.retrieveRootFolder");
        expectedEventNames.add("cmis.scenario.01.listFolderContents");
        Set<String> eventNames = new TreeSet<String>(resultService.getEventNames());
        assertEquals("Unexpected event names. ", expectedEventNames, eventNames);
        assertEquals(
                "Incorrect number of events: " + "cmis.startSession",
                20, resultService.countResultsByEventName("cmis.startSession"));
        
        // Check for failures
        long failures = resultService.countResultsByFailure();
        assertEquals("Did not expect failures (at present). ", 0L, failures);
        
        // Check totals
        long successes = resultService.countResultsBySuccess();
        assertEquals("Incorrect number of successful events. ", 62, successes);
        
        // Let's dump a few of the session results for information
        List<EventRecord> startSessionResults = resultService.getResults("cmis.startSession", 0, 1);
        for (EventRecord startSessionResult : startSessionResults)
        {
            logger.info(startSessionResult);
        }
        List<EventRecord> retrieveRootFolderResults = resultService.getResults("cmis.scenario.01.retrieveRootFolder", 0, 1);
        for (EventRecord retrieveRootFolderResult : retrieveRootFolderResults)
        {
            logger.info(retrieveRootFolderResult);
            assertTrue(
                    "Expected to find 'Company Home' in the root folder description: " + retrieveRootFolderResult.getData(),
                    retrieveRootFolderResult.getData().toString().contains("Company Home"));
        }
        List<EventRecord> listFolderContents = resultService.getResults("cmis.scenario.01.listFolderContents", 0, 1);
        for (EventRecord listFolderContent : listFolderContents)
        {
            logger.info(listFolderContent);
        }
    }
}

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.session.SessionService;
import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.apache.chemistry.opencmis.client.api.OperationContext;
import org.apache.chemistry.opencmis.client.api.Repository;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.RepositoryInfo;
import org.apache.chemistry.opencmis.commons.enums.BindingType;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;

/**
 * Execute an unfinished process
 * 
 * <h1>Input</h1>
 * 
 * Username: <tt>String</tt>
 * 
 * <h1>Actions</h1>
 * 
 * Opens a new CMISEventData instance containing the CMIS session to the target server and repository
 * 
 * <h1>Output</h1>
 * 
 * {@link #EVENT_NAME_SESSION_STARTED}: an event containing the {@link Session CMIS Session} as data.<br/>
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class StartCMISSession extends AbstractCMISEventProcessor
{
    public static final String REPOSITORY_ID_USE_FIRST = "---";
    public static final String EVENT_NAME_SESSION_STARTED = "cmis.sessionStarted";
    
    private final UserDataService userDataService;
    private final SessionService sessionService;
    private final String atomPubUrl;
    private final String repositoryId;
    private final OperationContext ctx;
    
    private String eventNameSessionStarted;

    /**
     * @param userDataService           service to retrieve user authentication details
     * @param sessionService            service to register a load test session
     * @param atomPubUrl                the URL as required by the {@link SessionParameter.ATOMPUB_URL} parameter
     * @param repositoryId              the ID of the repository required by the {@link SessionParameter.REPOSITORY_ID} parameter
     * @param ctx                       the operation context for all calls made by the session.
     *                                  Event processors must not adjust but should copy it if changes are required.
     */
    public StartCMISSession(
            UserDataService userDataService, SessionService sessionService,
            String atomPubUrl, String repositoryId,
            OperationContext ctx)
    {
        super();
        this.userDataService = userDataService;
        this.sessionService = sessionService;
        this.atomPubUrl = atomPubUrl;
        this.repositoryId = repositoryId;
        this.ctx = ctx;
        this.eventNameSessionStarted = EVENT_NAME_SESSION_STARTED;
    }

    /**
     * Override the {@link #EVENT_NAME_SESSION_STARTED default} event name for 'session started'.
     */
    public void setEventNameSessionStarted(String eventNameSessionStarted)
    {
        this.eventNameSessionStarted = eventNameSessionStarted;
    }

    @Override
    protected EventResult processCMISEvent(Event event) throws Exception
    {
        String username = (String) event.getDataObject();
        // A quick double-check
        if (username == null)
        {
            return new EventResult("Unable to start CMIS session without a username.", false);
        }
        UserData user = userDataService.findUserByUsername(username);
        if (user == null)
        {
            return new EventResult("Unable to start CMIS session; user no longer exists: " + username, false);
        }
        String password = user.getPassword();
        
        // Start the CMIS session
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
        parameters.put(SessionParameter.ATOMPUB_URL, atomPubUrl);
        parameters.put(SessionParameter.USER, username);
        parameters.put(SessionParameter.PASSWORD, password);
        
        // First check if we need to choose a repository
        SessionFactory sessionFactory = SessionFactoryImpl.newInstance();
        List<Repository> repositories = sessionFactory.getRepositories(parameters);
        if (repositories.size() == 0)
        {
            return new EventResult("Unable to find any repositories at " + atomPubUrl + " with user " + username, false);
        }
        if (repositoryId.equals(REPOSITORY_ID_USE_FIRST))
        {
            String repositoryIdFirst = repositories.get(0).getId();
            parameters.put(SessionParameter.REPOSITORY_ID, repositoryIdFirst);
        }
        else
        {
            parameters.put(SessionParameter.REPOSITORY_ID, repositoryId);
        }
        
        // Create the session
        Session session = SessionFactoryImpl.newInstance().createSession(parameters);
        session.setDefaultContext(ctx);

        // get repository info
        RepositoryInfo repositoryInfo = session.getRepositoryInfo();
        CMISEventData cmisData = new CMISEventData(session);

        // Start a load test session
        DBObject sessionObj = new BasicDBObject()
                .append("repository", repositoryInfo.toString())
                .append("user", username);
        String sessionId = sessionService.startSession(sessionObj);
        
        // Done
        Event doneEvent = new Event(eventNameSessionStarted, cmisData);
        EventResult result = new EventResult(
                BasicDBObjectBuilder.start()
                    .append("msg", "Successfully created CMIS session.")
                    .append("repository", parameters.get(SessionParameter.REPOSITORY_ID))
                    .append("user", username)
                    .append("sessionId", sessionId)
                    .append("ctx", convertOperationContext(ctx))
                    .get(),
                doneEvent);
        
        // Done
        return result;
    }
    
    /**
     * Convert an operation context into a DBObject for neat, searchable persistence
     */
    public static DBObject convertOperationContext(OperationContext ctx)
    {
        return BasicDBObjectBuilder.start()
            .append("pageSize", ctx.getMaxItemsPerPage())
            .append("orderBy", ctx.getOrderBy())
            .append("cacheEnabled", ctx.isCacheEnabled())
            .append("includeAcls", ctx.isIncludeAcls())
            .append("includeAllowableActions", ctx.isIncludeAllowableActions())
            .append("includePathSegments", ctx.isIncludePathSegments())
            .append("includePolicies", ctx.isIncludePolicies())
            .get();
    }
}

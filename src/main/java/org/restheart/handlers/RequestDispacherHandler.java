/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers;

import org.restheart.handlers.root.GetRootHandler;
import org.restheart.handlers.collection.DeleteCollectionHandler;
import org.restheart.handlers.collection.GetCollectionHandler;
import org.restheart.handlers.collection.PatchCollectionHandler;
import org.restheart.handlers.collection.PostCollectionHandler;
import org.restheart.handlers.collection.PutCollectionHandler;
import org.restheart.handlers.database.DeleteDBHandler;
import org.restheart.handlers.database.GetDBHandler;
import org.restheart.handlers.database.PatchDBHandler;
import org.restheart.handlers.database.PutDBHandler;
import org.restheart.handlers.document.DeleteDocumentHandler;
import org.restheart.handlers.document.GetDocumentHandler;
import org.restheart.handlers.document.PatchDocumentHandler;
import org.restheart.handlers.document.PutDocumentHandler;
import org.restheart.handlers.indexes.DeleteIndexHandler;
import org.restheart.handlers.indexes.GetIndexesHandler;
import org.restheart.handlers.indexes.PutIndexHandler;
import org.restheart.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import java.util.HashMap;
import java.util.Map;
import static org.restheart.handlers.RequestContext.METHOD;
import static org.restheart.handlers.RequestContext.TYPE;
import org.restheart.handlers.files.GetBinaryFileHandler;
import org.restheart.handlers.files.PostBinaryFileHandler;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public final class RequestDispacherHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestDispacherHandler.class);

    private final Map<TYPE, Map<METHOD, PipedHttpHandler>> handlersMultimap;

    /**
     * Creates a new instance of RequestDispacherHandler
     */
    public RequestDispacherHandler() {
        this(true);
    }

    /**
     * Used for testing. By passing a <code>false</code> parameter then handlers
     * are not initialized and you can put your own (e.g. mocks)
     *
     * @param initialize if false then do not initialize the handlersMultimap
     */
    RequestDispacherHandler(boolean initialize) {
        super(null, null);
        this.handlersMultimap = new HashMap<>();
        if (initialize) {
            defaultInit();
        }
    }

    /**
     * Put into handlersMultimap all the default combinations of types, methods
     * and PipedHttpHandler objects
     */
    private void defaultInit() {
        LOGGER.info("Initialize default HTTP handlers:");
        // ROOT handlers
        putPipedHttpHandler(TYPE.ROOT, METHOD.GET, new GetRootHandler());

        // DB handlres
        putPipedHttpHandler(TYPE.DB, METHOD.GET, new GetDBHandler());
        putPipedHttpHandler(TYPE.DB, METHOD.PUT, new PutDBHandler());
        putPipedHttpHandler(TYPE.DB, METHOD.DELETE, new DeleteDBHandler());
        putPipedHttpHandler(TYPE.DB, METHOD.PATCH, new PatchDBHandler());

        // COLLECTION handlres
        final GetCollectionHandler getCollectionHandler = new GetCollectionHandler();
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.GET, getCollectionHandler);
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.POST, new PostCollectionHandler());
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.PUT, new PutCollectionHandler());
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.DELETE, new DeleteCollectionHandler());
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.PATCH, new PatchCollectionHandler());

        // DOCUMENT handlers
        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.GET, new GetDocumentHandler());
        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.PUT, new PutDocumentHandler());
        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.DELETE, new DeleteDocumentHandler());
        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.PATCH, new PatchDocumentHandler());

        // COLLECTION_INDEXES handlers
        putPipedHttpHandler(TYPE.COLLECTION_INDEXES, METHOD.GET, new GetIndexesHandler());

        // INDEX handlers
        putPipedHttpHandler(TYPE.INDEX, METHOD.PUT, new PutIndexHandler());
        putPipedHttpHandler(TYPE.INDEX, METHOD.DELETE, new DeleteIndexHandler());

        // FILES_BUCKET and FILE handlers
        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.GET, getCollectionHandler);
        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.POST, new PostBinaryFileHandler());
        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.PUT, new PutCollectionHandler());
        putPipedHttpHandler(TYPE.FILE, METHOD.GET, new GetBinaryFileHandler());
    }

    /**
     * Given a type and method, return the appropriate PipedHttpHandler which
     * can handle this request
     *
     * @param type
     * @param method
     * @return the PipedHttpHandler
     */
    public PipedHttpHandler getPipedHttpHandler(TYPE type, METHOD method) {
        Map<METHOD, PipedHttpHandler> methodsMap = handlersMultimap.get(type);
        return methodsMap != null ? methodsMap.get(method) : null;
    }

    /**
     * Given a type and method, put in a PipedHttpHandler
     *
     * @param type the DB type
     * @param method the HTTP method
     * @param handler the PipedHttpHandler
     */
    void putPipedHttpHandler(TYPE type, METHOD method, PipedHttpHandler handler) {
        LOGGER.info("putPipedHttpHandler( {}, {}, {} )", type, method, handler.getClass().getCanonicalName());
        Map<METHOD, PipedHttpHandler> methodsMap = handlersMultimap.get(type);
        if (methodsMap == null) {
            methodsMap = new HashMap<>();
            handlersMultimap.put(type, methodsMap);
        }
        methodsMap.put(method, handler);
    }

    /**
     * Handle the request
     *
     * @param exchange the HttpServerExchange
     * @param context the RequestContext
     * @throws Exception
     */
    @Override
    public final void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.getType() == TYPE.ERROR) {
            LOGGER.error("This is a bad request: returning a <{}> HTTP code", HttpStatus.SC_BAD_REQUEST);
            ResponseHelper.endExchange(exchange, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        if (context.getMethod() == METHOD.OTHER) {
            LOGGER.error("This method is not allowed: returning a <{}> HTTP code", HttpStatus.SC_METHOD_NOT_ALLOWED);
            ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (context.isReservedResource()) {
            LOGGER.error("The resource is reserved: returning a <{}> HTTP code", HttpStatus.SC_FORBIDDEN);
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_FORBIDDEN, "reserved resource");
            return;
        }

        final PipedHttpHandler httpHandler = getPipedHttpHandler(context.getType(), context.getMethod());

        if (httpHandler != null) {
            httpHandler.handleRequest(exchange, context);
        } else {
            LOGGER.error("Call to getPipedHttpHandler({}, {}) can't find any PipedHttpHandler", context.getType(), context.getMethod());
            ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

}

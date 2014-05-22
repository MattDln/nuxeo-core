/*
 * Copyright (c) 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.dbs;

import static java.lang.Boolean.FALSE;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.naming.NamingException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;

import org.nuxeo.ecm.core.api.DocumentException;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.core.model.Document;
import org.nuxeo.ecm.core.model.Session;
import org.nuxeo.ecm.core.storage.binary.BinaryManager;
import org.nuxeo.ecm.core.storage.binary.BinaryManagerDescriptor;
import org.nuxeo.ecm.core.storage.binary.BinaryManagerService;
import org.nuxeo.ecm.core.storage.binary.DefaultBinaryManager;
import org.nuxeo.ecm.core.storage.sql.RepositoryDescriptor;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Provides sharing behavior for repository sessions and other basic functions.
 *
 * @since 5.9.4
 */
public abstract class DBSRepositoryBase implements DBSRepository {

    public static final String TYPE_ROOT = "Root";

    protected final String repositoryName;

    protected final BinaryManager binaryManager;

    protected String rootId;

    // change to have deterministic pseudo-UUID generation for debugging
    private final boolean DEBUG_UUIDS = true;

    // for debug
    private final AtomicLong temporaryIdCounter = new AtomicLong(-1);

    public DBSRepositoryBase(String repositoryName) {
        this.repositoryName = repositoryName;
        binaryManager = newBinaryManager();
    }

    @Override
    public String getName() {
        return repositoryName;
    }

    @Override
    public String getRootId() {
        return rootId;
    }

    @Override
    public String generateNewId() {
        if (DEBUG_UUIDS) {
            return "UUID_" + temporaryIdCounter.incrementAndGet();
        } else {
            if (rootId == null) {
                return "00000000-0000-0000-0000-000000000000";
            } else {
                return UUID.randomUUID().toString();
            }
        }
    }

    public void initRoot() {
        try {
            Session session = getSession(null);
            Document root = session.getNullDocument().addChild("", TYPE_ROOT);
            rootId = root.getUUID();
            ACLImpl acl = new ACLImpl();
            acl.add(new ACE(SecurityConstants.ADMINISTRATORS,
                    SecurityConstants.EVERYTHING, true));
            acl.add(new ACE(SecurityConstants.ADMINISTRATOR,
                    SecurityConstants.EVERYTHING, true));
            acl.add(new ACE(SecurityConstants.MEMBERS, SecurityConstants.READ,
                    true));
            ACPImpl acp = new ACPImpl();
            acp.addACL(acl);
            session.setACP(root, acp, true);
            session.save();
            session.close();
            if (TransactionHelper.isTransactionActive()) {
                TransactionHelper.commitOrRollbackTransaction();
                TransactionHelper.startTransaction();
            }
        } catch (DocumentException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BinaryManager getBinaryManager() {
        return binaryManager;
    }

    public BinaryManager newBinaryManager() {
        BinaryManager binaryManager = new DefaultBinaryManager();
        BinaryManagerDescriptor binaryManagerDescriptor = new BinaryManagerDescriptor();
        try {
            File dir = File.createTempFile("dbsBinaryManager", "");
            dir.delete();
            binaryManagerDescriptor.repositoryName = "dbs";
            binaryManagerDescriptor.storePath = dir.getPath();
            binaryManager.initialize(binaryManagerDescriptor);
            BinaryManagerService bms = Framework.getLocalService(BinaryManagerService.class);
            bms.addBinaryManager(binaryManagerDescriptor.repositoryName,
                    binaryManager);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return binaryManager;
    }

    @Override
    public int getActiveSessionsCount() {
        return 0;
    }

    @Override
    public Session getSession(String sessionId) throws DocumentException {
        Transaction transaction;
        try {
            transaction = TransactionHelper.lookupTransactionManager().getTransaction();
            if (transaction.getStatus() != Status.STATUS_ACTIVE) {
                transaction = null;
            }
        } catch (SystemException | NamingException e) {
            transaction = null;
        }

        if (transaction == null) {
            // no active transaction, use a regular session
            return newSession(sessionId);
        }

        TransactionContext context = transactionContexts.get(transaction);
        if (context == null) {
            context = new TransactionContext(transaction, newSession(sessionId));
            context.init();
        }
        return context.newSession(sessionId);
    }

    protected DBSSession newSession(String sessionId) {
        return new DBSSession(this, sessionId);
    }

    public Map<Transaction, TransactionContext> transactionContexts = new ConcurrentHashMap<>();

    public class TransactionContext implements Synchronization {

        protected final Transaction transaction;

        protected final DBSSession baseSession;

        protected final Set<DBSSessionInvoker> invokers;

        public TransactionContext(Transaction transaction,
                DBSSession baseSession) {
            this.transaction = transaction;
            this.baseSession = baseSession;
            invokers = new HashSet<>();
        }

        public void init() {
            transactionContexts.put(transaction, this);
            // make sure it's closed (with handles) at transaction end
            try {
                transaction.registerSynchronization(this);
            } catch (RollbackException | SystemException e) {
                throw new RuntimeException(e);
            }
        }

        public Session newSession(String sessionId) {
            ClassLoader cl = getClass().getClassLoader();
            DBSSessionInvoker invoker = new DBSSessionInvoker(this, sessionId);
            Session proxy = (Session) Proxy.newProxyInstance(cl,
                    new Class[] { Session.class }, invoker);
            add(invoker);
            return proxy;
        }

        public void add(DBSSessionInvoker invoker) {
            invokers.add(invoker);
        }

        public boolean remove(Object invoker) {
            return invokers.remove(invoker);
        }

        @Override
        public void beforeCompletion() {
            try {
                baseSession.commit();
            } catch (DocumentException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void afterCompletion(int status) {
            baseSession.close();
            for (Session proxy : invokers.toArray(new Session[0])) {
                proxy.close();
            }
            transactionContexts.remove(transaction);
        }
    }

    /**
     * An indirection to a {@link DBSSession} that has a different sessionId.
     */
    public static class DBSSessionInvoker implements InvocationHandler {

        private static final String METHOD_GETSESSIONID = "getSessionId";

        private static final String METHOD_CLOSE = "close";

        private static final String METHOD_ISLIVE = "isLive";

        protected final TransactionContext context;

        protected final String sessionId;

        protected boolean closed;

        public DBSSessionInvoker(TransactionContext context, String sessionId) {
            this.context = context;
            this.sessionId = sessionId;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            if (method.getName().equals(METHOD_GETSESSIONID)) {
                return sessionId;
            }
            if (method.getName().equals(METHOD_CLOSE)) {
                closed = true;
                context.remove(this);
                return null;
            }
            if (method.getName().equals(METHOD_ISLIVE)) {
                if (closed) {
                    return FALSE;
                }
                // else fall through
            }
            if (closed) {
                throw new DocumentException(
                        "Cannot use closed connection handle: " + sessionId);
            }

            try {
                return method.invoke(context.baseSession, args);
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException) {
                    Throwable te = ((InvocationTargetException) t).getTargetException();
                    if (te != null) {
                        t = te;
                    }
                }
                if (t instanceof InterruptedException) {
                    // restore interrupted state
                    Thread.currentThread().interrupt();
                }
                throw t;
            }
        }
    }

}

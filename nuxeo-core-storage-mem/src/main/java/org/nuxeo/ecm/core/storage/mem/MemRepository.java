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
package org.nuxeo.ecm.core.storage.mem;

import static java.lang.Boolean.TRUE;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_PROXY;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_NAME;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PARENT_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PROXY_IDS;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PROXY_TARGET_ID;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.nuxeo.ecm.core.api.DocumentException;
import org.nuxeo.ecm.core.model.Repository;
import org.nuxeo.ecm.core.query.sql.model.Expression;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.storage.CopyHelper;
import org.nuxeo.ecm.core.storage.PartialList;
import org.nuxeo.ecm.core.storage.dbs.DBSDocument;
import org.nuxeo.ecm.core.storage.dbs.DBSExpressionEvaluator;
import org.nuxeo.ecm.core.storage.dbs.DBSExpressionEvaluator.OrderByComparator;
import org.nuxeo.ecm.core.storage.dbs.DBSRepositoryBase;

/**
 * In-memory implementation of a {@link Repository}.
 * <p>
 * Internally, the repository is a map from id to document object.
 * <p>
 * A document object is a JSON-like document stored as a Map recursively
 * containing the data, see {@link DBSDocument} for the description of the
 * document.
 *
 * @since 5.9.4
 */
public class MemRepository extends DBSRepositoryBase {

    /**
     * The content of the repository, a map of document id -> object.
     */
    protected Map<String, Map<String, Serializable>> states;

    public MemRepository(String repositoryName) {
        super(repositoryName);
        states = new HashMap<>();
        initRoot();
    }

    @Override
    public void shutdown() {
        states = null;
    }

    @Override
    public Map<String, Serializable> readState(String id) {
        Map<String, Serializable> state = states.get(id);
        // log.error("read   " + id + ": " + state);
        return state;
    }

    @Override
    public List<Map<String, Serializable>> readStates(List<String> ids) {
        List<Map<String, Serializable>> list = new ArrayList<>();
        for (String id : ids) {
            list.add(readState(id));
        }
        return list;
    }

    @Override
    public void createState(Map<String, Serializable> state)
            throws DocumentException {
        String id = (String) state.get(KEY_ID);
        // log.error("create " + id + ": " + state);
        if (states.containsKey(id)) {
            throw new DocumentException("Already exists: " + id);
        }
        states.put(id, state);
    }

    @Override
    public void updateState(Map<String, Serializable> state)
            throws DocumentException {
        String id = (String) state.get(KEY_ID);
        // log.error("update " + id + ": " + state);
        if (!states.containsKey(id)) {
            throw new DocumentException("Missing: " + id);
        }
        states.put(id, state);
    }

    @Override
    public void deleteState(String id) throws DocumentException {
        // log.error("delete " + id);
        if (states.remove(id) == null) {
            throw new DocumentException("Missing: " + id);
        }
    }

    @Override
    public Map<String, Serializable> readChildState(String parentId,
            String name, Set<String> ignored) {
        // TODO optimize by maintaining a parent/child index
        for (Map<String, Serializable> state : states.values()) {
            if (ignored.contains(state.get(KEY_ID))) {
                continue;
            }
            if (!parentId.equals(state.get(KEY_PARENT_ID))) {
                continue;
            }
            if (!name.equals(state.get(KEY_NAME))) {
                continue;
            }
            return state;
        }
        return null;
    }

    @Override
    public boolean hasChild(String parentId, String name, Set<String> ignored) {
        return readChildState(parentId, name, ignored) != null;
    }

    @Override
    public List<Map<String, Serializable>> queryKeyValue(String key,
            String value, Set<String> ignored) {
        List<Map<String, Serializable>> list = new ArrayList<>();
        for (Map<String, Serializable> state : states.values()) {
            String id = (String) state.get(KEY_ID);
            if (ignored.contains(id)) {
                continue;
            }
            if (!value.equals(state.get(key))) {
                continue;
            }
            list.add(state);
        }
        return list;
    }

    @Override
    public void queryKeyValueArray(String key, Object value, Set<String> ids,
            Map<String, String> proxyTargets,
            Map<String, Object[]> targetProxies, Set<String> ignored) {
        STATE: for (Map<String, Serializable> state : states.values()) {
            Object[] array = (Object[]) state.get(key);
            String id = (String) state.get(KEY_ID);
            if (ignored.contains(id)) {
                continue;
            }
            if (array != null) {
                for (Object v : array) {
                    if (value.equals(v)) {
                        ids.add(id);
                        if (proxyTargets != null
                                && TRUE.equals(state.get(KEY_IS_PROXY))) {
                            String targetId = (String) state.get(KEY_PROXY_TARGET_ID);
                            proxyTargets.put(id, targetId);
                        }
                        if (targetProxies != null) {
                            Object[] proxyIds = (Object[]) state.get(KEY_PROXY_IDS);
                            if (proxyIds != null) {
                                targetProxies.put(id, proxyIds);
                            }
                        }
                        continue STATE;
                    }
                }
            }
        }
    }

    @Override
    public boolean queryKeyValuePresence(String key, String value,
            Set<String> ignored) {
        for (Map<String, Serializable> state : states.values()) {
            String id = (String) state.get(KEY_ID);
            if (ignored.contains(id)) {
                continue;
            }
            if (value.equals(state.get(key))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PartialList<Map<String, Serializable>> queryAndFetch(
            Expression expression, DBSExpressionEvaluator evaluator,
            OrderByClause orderByClause, int limit, int offset, int countUpTo,
            boolean deepCopy, Set<String> ignored) {
        List<Map<String, Serializable>> maps = new ArrayList<>();
        for (Entry<String, Map<String, Serializable>> en : states.entrySet()) {
            String id = en.getKey();
            if (ignored.contains(id)) {
                continue;
            }
            Map<String, Serializable> map = en.getValue();
            if (evaluator.matches(map)) {
                if (deepCopy) {
                    map = CopyHelper.deepCopy(map);
                }
                maps.add(map);
            }
        }
        // ORDER BY
        if (orderByClause != null) {
            Collections.sort(maps, new OrderByComparator(orderByClause,
                    evaluator));
        }
        // LIMIT / OFFSET
        int totalSize = maps.size();
        if (countUpTo == -1) {
            // count full size
        } else if (countUpTo == 0) {
            // no count
            totalSize = -1; // not counted
        } else {
            // count only if less than countUpTo
            if (totalSize > countUpTo) {
                totalSize = -2; // truncated
            }
        }
        if (limit != 0) {
            int size = maps.size();
            maps.subList(0, offset > size ? size : offset).clear();
            size = maps.size();
            if (limit < size) {
                maps.subList(limit, size).clear();
            }
        }
        // TODO DISTINCT

        return new PartialList<>(maps, totalSize);
    }

}
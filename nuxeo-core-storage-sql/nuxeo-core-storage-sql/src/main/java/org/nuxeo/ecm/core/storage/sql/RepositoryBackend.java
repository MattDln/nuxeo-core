/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.sql;

import org.nuxeo.ecm.core.storage.StorageException;
import org.nuxeo.ecm.core.storage.sql.Session.PathResolver;

/**
 * Interface for the backend-specific initialization code of a
 * {@link Repository}.
 *
 * @see RepositoryImpl
 */
public interface RepositoryBackend {

    /**
     * Initializer.
     */
    void initialize(RepositoryImpl repository) throws StorageException;

    /**
     * Initializes the {@link ModelSetup}. Called once lazily at repository
     * initialization.
     */
    void initializeModelSetup(ModelSetup modelSetup) throws StorageException;

    /**
     * Initializes what's needed after the {@link Model} has been created.
     * Called once lazily at repository initialization.
     */
    void initializeModel(Model model) throws StorageException;

    /**
     * The kind of mapper to create through {@link #newMapper}.
     */
    enum MapperKind {
        LOCK_MANAGER("lock"), CLUSTER_NODE_HANDLER("cluster"), SESSION("session");

        public final String name;

        MapperKind(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Creates a new instance a {@link Mapper}. Called once for every new
     * session.
     *
     * @param model the model
     * @param pathResolver the path resolver
     * @param kind the kind of mapper to create
     */
    Mapper newMapper(Model model, PathResolver pathResolver, MapperKind kind)
            throws StorageException;

    /**
     * Shuts down the backend.
     */
    void shutdown() throws StorageException;

    LockManager getLockManager();

}

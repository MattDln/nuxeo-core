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

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.Environment;
import org.nuxeo.runtime.api.Framework;

/**
 * @author Florent Guillaume
 */
public class DatabaseH2 extends DatabaseHelper {

    public static final DatabaseHelper INSTANCE = new DatabaseH2();

    private static final Log log = LogFactory.getLog(DatabaseH2.class);

    /** This directory will be deleted and recreated. */
    protected static final String DIRECTORY = "target";

    protected static final String DEF_USER = "sa";

    protected static final String DEF_PASSWORD = "";

    protected static final String CONTRIB_XML = "OSGI-INF/test-repo-repository-h2-contrib.xml";

    protected static final String DRIVER = "org.h2.Driver";

    protected static final String URL_FORMAT = "jdbc:h2:%s/%s";

    protected String h2Path;

    protected String url;

    protected String url2;

    protected String user;

    protected String password;

    protected void setProperties() {
        url = setProperty(URL_PROPERTY, String.format(URL_FORMAT, h2Path, databaseName));

        setProperty(REPOSITORY_PROPERTY,
                repositoryName);
        setProperty(DATABASE_PROPERTY, databaseName);
        user = setProperty(USER_PROPERTY, DEF_USER);
        password = setProperty(PASSWORD_PROPERTY, DEF_PASSWORD);
        // for sql directory tests
        setProperty(DRIVER_PROPERTY, DRIVER);
    }

    protected void setProperties2() {
        url2 = String.format(URL_FORMAT, h2Path, databaseName + "2");
        setProperty(URL_PROPERTY + "2", url2);
        Framework.getProperties().setProperty(REPOSITORY_PROPERTY + "2",
                repositoryName + "2");
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        h2Path = new File(Environment.getDefault().getData(), "h2").getPath();
        Class.forName(DRIVER);
        setProperties();
    }

    public void setUp2() throws Exception {
        setProperties2();
    }

    protected String getId() {
        return "nuxeo";
    }

    @Override
    public void tearDown() throws SQLException {
        try {
            tearDownDatabase(url);
            if (url2 != null) {
                tearDownDatabase(url2);
            }
        } finally {
            super.tearDown();
        }
    }

    protected void tearDownDatabase(String url) throws SQLException {
        Connection connection = DriverManager.getConnection(url,
                user,
                password);
        Statement st = connection.createStatement();
        String sql = "SHUTDOWN";
        log.trace(sql);
        st.execute(sql);
        st.close();
        connection.close();
        FileUtils.deleteQuietly(new File(h2Path));
    }

    @Override
    public String getDeploymentContrib() {
        return CONTRIB_XML;
    }

    @Override
    public RepositoryDescriptor getRepositoryDescriptor() {
        RepositoryDescriptor descriptor = new RepositoryDescriptor();
        descriptor.xaDataSourceName = "org.h2.jdbcx.JdbcDataSource";
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("URL", url);
        properties.put("User", Framework.getProperty(USER_PROPERTY));
        properties.put("Password", Framework.getProperty(PASSWORD_PROPERTY));
        descriptor.properties = properties;
        return descriptor;
    }

    @Override
    public boolean supportsClustering() {
        return true;
    }

}

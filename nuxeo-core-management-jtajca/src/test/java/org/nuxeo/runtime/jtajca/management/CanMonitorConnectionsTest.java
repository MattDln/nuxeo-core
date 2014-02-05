/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     matic
 */
package org.nuxeo.runtime.jtajca.management;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.management.jtajca.StorageConnectionMonitor;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

/**
 * @author matic
 *
 */
@RunWith(FeaturesRunner.class)
@Features( JtajcaManagementFeature.class )
public class CanMonitorConnectionsTest {

    @Inject protected StorageConnectionMonitor monitor;

    @Inject
    CoreSession repository;

    @Test
    public void isMonitorInstalled() {
        assertThat(monitor, notNullValue());
        monitor.getConnectionCount(); // throw exception is monitor not present
    }

    @Test
    public void isConnectionOpened() throws ClientException {
        int count = monitor.getConnectionCount();
        assertThat(count, greaterThan(0));
    }

}

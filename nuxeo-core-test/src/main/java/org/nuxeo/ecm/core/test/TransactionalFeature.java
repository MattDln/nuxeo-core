/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Stephane Lacoin
 */
package org.nuxeo.ecm.core.test;

import java.util.Properties;

import org.junit.runners.model.FrameworkMethod;
import org.nuxeo.ecm.core.test.annotations.TransactionalConfig;
import org.nuxeo.runtime.jtajca.JtaActivator;
import org.nuxeo.runtime.test.runner.Defaults;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

@Deploy({ "org.nuxeo.runtime.jtajca" })
public class TransactionalFeature extends SimpleFeature {

    protected TransactionalConfig config;

    protected String autoactivationValue;

    protected boolean txStarted;

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {
        config = runner.getDescription().getAnnotation(
                TransactionalConfig.class);
        if (config == null) {
            config = Defaults.of(TransactionalConfig.class);
        }
        autoactivationValue = System.getProperty(JtaActivator.AUTO_ACTIVATION);
        System.setProperty(JtaActivator.AUTO_ACTIVATION, "true");
    }

    @Override
    public void stop(FeaturesRunner runner) throws Exception {
        Properties props = System.getProperties();
        if (autoactivationValue != null) {
            props.put(JtaActivator.AUTO_ACTIVATION, autoactivationValue);
        } else {
            props.remove(JtaActivator.AUTO_ACTIVATION);
        }
    }

    @Override
    public void beforeMethodRun(FeaturesRunner runner, FrameworkMethod method,
            Object test) throws Exception {
        if (config.autoStart() == false) {
            return;
        }
        txStarted = TransactionHelper.startTransaction();
    }

    @Override
    public void afterMethodRun(FeaturesRunner runner, FrameworkMethod method,
            Object test) throws Exception {
        if (txStarted == false) {
            return;
        }
        TransactionHelper.commitOrRollbackTransaction();
    }
}

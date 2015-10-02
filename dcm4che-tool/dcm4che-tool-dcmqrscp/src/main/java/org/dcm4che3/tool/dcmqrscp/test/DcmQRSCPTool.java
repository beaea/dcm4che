//
/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4che3.tool.dcmqrscp.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Map.Entry;
import java.util.Properties;

import org.dcm4che3.data.UID;
import org.dcm4che3.media.RecordFactory;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.QueryOption;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.service.DicomService;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.net.service.InstanceLocator;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.tool.common.test.TestResult;
import org.dcm4che3.tool.common.test.TestTool;
import org.dcm4che3.tool.dcmqrscp.DcmQRSCP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexander Hoermandinger <alexander.hoermandinger@agfa.com>
 *
 */
public class DcmQRSCPTool implements TestTool {
    private static final Logger LOG = LoggerFactory.getLogger(DcmQRSCPTool.class);
    
    private TestResult result;
    private ExtDcmQRSCP qrscp;
    
    private final Builder builder;
  
    public static class Builder {
        private DcmQRSCPToolConfig cfg;
        private DicomService cStoreSCP;
        private DicomService stgCmtSCP;
        
        public Builder toolConfig(DcmQRSCPToolConfig cfg) {
            this.cfg = cfg;
            return this;
        }
        
        public Builder cStoreSCP(DicomService cStoreSCP) {
            this.cStoreSCP = cStoreSCP;
            return this;
        }
        
        public Builder stgCmtSCP(DicomService stgCmt) {
            this.stgCmtSCP = stgCmt;
            return this;
        }
        
        public DcmQRSCPTool build() throws IOException {
            return new DcmQRSCPTool(this);
        }
   
    }
    
    private DcmQRSCPTool(Builder builder) throws IOException {
        this.builder = builder;
        qrscp = new ExtDcmQRSCP();
        qrscp.init();
    }
    
    @Override
    public void init(TestResult result) {
        this.result = result;
    }

    @Override
    public TestResult getResult() {
        return result;
    }
    
    public void start() {
        try {
            startInt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void stop() {
        qrscp.device.unbindConnections();
    }
    
    public void startInt() throws IOException {
        qrscp.conn.setPort(builder.cfg.getPort());
        qrscp.ae.setAETitle(builder.cfg.getAeTitle());
        
        qrscp.setFilePathFormat(builder.cfg.getFilePathFormat());
        qrscp.setRecordFactory(new RecordFactory());
        
        ApplicationEntity ae = qrscp.ae;
        EnumSet<QueryOption> queryOptions = EnumSet.noneOf(QueryOption.class);
        boolean storage = true;
        boolean allStorage = false;
        if (storage && allStorage) {
            TransferCapability tc = new TransferCapability(null, "*", TransferCapability.Role.SCP, "*");
            tc.setQueryOptions(queryOptions);
            ae.addTransferCapability(tc);
        } else {
            ae.addTransferCapability(new TransferCapability(null,
                    UID.VerificationSOPClass, TransferCapability.Role.SCP,
                    UID.ImplicitVRLittleEndian));
            Properties storageSOPClasses = loadProperties("storage-sop-classes.properties");
            if (storage)
                addTransferCapabilities(ae, storageSOPClasses,
                        TransferCapability.Role.SCP, null);
            if (true) {
                addTransferCapabilities(ae, storageSOPClasses,
                        TransferCapability.Role.SCU, null);
                Properties p = loadProperties("retrieve-sop-classes.properties");
                addTransferCapabilities(ae, p, TransferCapability.Role.SCP,
                        queryOptions);
            }
            if (true) {
                Properties p = loadProperties("query-sop-classes.properties");
                addTransferCapabilities(ae, p, TransferCapability.Role.SCP,
                        queryOptions);
            }
        }
        
        for(Entry<String,Connection> entry : builder.cfg.getRemoteConnections().entrySet()) {
            qrscp.addRemoteConnection(entry.getKey(), entry.getValue());
        }
        
        qrscp.device.setScheduledExecutor(builder.cfg.getScheduledExecutorService());
        qrscp.device.setExecutor(builder.cfg.getExecutor());
        
        try {
            qrscp.device.bindConnections();
        } catch(Exception e) {
            LOG.error("Error while binding connections", e);
        }
    }
    
    private static Properties loadProperties(String filename) {
        try {
            FileInputStream  in = new FileInputStream(new File("E:/tmp/" + filename));
            Properties p = new Properties();
            p.load(in);
            return p;
        } catch (Exception e) {
            LOG.error("Error while reading properties from file", e);
        }
        
        return null;
    }
    
    private static void addTransferCapabilities(ApplicationEntity ae,
            Properties p, TransferCapability.Role role,
            EnumSet<QueryOption> queryOptions) {
        for (String cuid : p.stringPropertyNames()) {
            String ts = p.getProperty(cuid);
            TransferCapability tc = new TransferCapability(null,
                    CLIUtils.toUID(cuid), role, CLIUtils.toUIDs(ts));
            tc.setQueryOptions(queryOptions);
            ae.addTransferCapability(tc);
        }
    }
    
    private class ExtDcmQRSCP extends DcmQRSCP<InstanceLocator> {
        
        public ExtDcmQRSCP() throws IOException {
            super();
        }
        
        @Override
        protected void addCStoreSCPService(DicomServiceRegistry serviceRegistry) {
            serviceRegistry.addDicomService(builder.cStoreSCP);
        }
        
        @Override
        protected void addStgCmtSCPService(DicomServiceRegistry serviceRegistry) {
            serviceRegistry.addDicomService(builder.stgCmtSCP);
        }
       
    }

}

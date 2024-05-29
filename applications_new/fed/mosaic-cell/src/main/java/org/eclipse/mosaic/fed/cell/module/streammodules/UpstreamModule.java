/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.fed.cell.module.streammodules;

import org.eclipse.mosaic.fed.cell.chain.ChainManager;
import org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties;
import org.eclipse.mosaic.fed.cell.config.model.TransmissionMode;
import org.eclipse.mosaic.fed.cell.message.CellModuleMessage;
import org.eclipse.mosaic.fed.cell.module.CellModuleNames;
import org.eclipse.mosaic.fed.cell.utility.RegionUtility;
import org.eclipse.mosaic.lib.objects.v2x.V2xMessage;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This module simulates the Uplink in the RAN-part (radio access network) of the cellular network.
 * Uplink means the connection between the node and the Geocaster and every cellular
 * message needs to go through the Geocaster. Hence the Uplink always bases on Unicast.
 */
public final class UpstreamModule extends AbstractStreamModule {

    private static final Logger log = LoggerFactory.getLogger(UpstreamModule.class);

    /**
     * Creates a new {@link UpstreamModule} object.
     *
     * @param chainManager Manage the cell chains.
     */
    public UpstreamModule(ChainManager chainManager) {
        super(CellModuleNames.UPSTREAM_MODULE, chainManager, log);
    }

    @Override
    public void processEvent(Event event) {
        // The UpstreamModule is the first module in the chain
        Object resource = event.getResource();
        if (resource == null) {
            throw new RuntimeException("No input message (event resource) for " + moduleName);
        } else if (resource instanceof V2xMessage) {
            // It can start with new V2XMessages to be introduced in the network
            processMessage((V2xMessage) event.getResource(), event.getTime());
        } else if (resource instanceof CellModuleMessage) {
            // or clean up pending messages (generated by itself)
            freeBandwidth((CellModuleMessage) event.getResource());
        } else {
            throw new RuntimeException("Unsupported input message (event resource) for " + moduleName);
        }
    }

    /**
     * Process the Uplink (Unicast) message transmission.
     *
     * @param v2xMessage       message to be transmitted (in this case, the send V2XMessage).
     * @param messageStartTime the time when the transmission starts.
     */
    private void processMessage(V2xMessage v2xMessage, long messageStartTime) {
        final String senderId = v2xMessage.getRouting().getSource().getSourceName();
        final CNetworkProperties region = RegionUtility.getRegionForNode(senderId);
        String nextModule = CellModuleNames.GEOCASTER;

        StreamProcessor.Input processingInput = new StreamProcessor.Input()
                .module(CellModuleNames.UPSTREAM_MODULE, nextModule)
                .node(senderId, region)
                .message(messageStartTime, v2xMessage, TransmissionMode.UplinkUnicast);

        final StreamProcessor.Result processingResult = doStreamProcessing(processingInput);
        final CellModuleMessage cellModuleMessage = processResult(processingInput, processingResult);

        if (processingResult.isAcknowledged()) {
            chainManager.finishEvent(cellModuleMessage);
        }
    }
}
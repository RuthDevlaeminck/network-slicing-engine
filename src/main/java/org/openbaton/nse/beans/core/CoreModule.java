/*
 *
 *  * Copyright (c) 2016 Open Baton (http://www.openbaton.org)
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.openbaton.nse.beans.core;

import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.nse.beans.adapters.NeutronQoSExecutor;
import org.openbaton.nse.beans.adapters.NeutronQoSHandler;
import org.openbaton.nse.properties.NseProperties;
import org.openbaton.nse.properties.NfvoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

/**
 * Created by maa on 02.12.15. modified by lgr on 20.07.17
 */
@Service
public class CoreModule {

  //@Autowired private CmHandler handler;
  private NeutronQoSHandler neutron_handler = new NeutronQoSHandler();
  private final ScheduledExecutorService qtScheduler = Executors.newScheduledThreadPool(1);
  private Logger logger;

  @Autowired private NfvoProperties nfvo_configuration;
  @Autowired private NseProperties nse_configuration;

  @PostConstruct
  private void init() {
    this.logger = LoggerFactory.getLogger(this.getClass());
  }

  /*
   * To allow different types of NFVI besides OpenStack
   * it will be necessary to split up the set of VNFRs
   * here to then create thread for each type of VNFI
   * instead of pushing everything to the thead
   * responsible for OpenStack Neutron..
   */

  public void addQos(Set<VirtualNetworkFunctionRecord> vnfrs, String nsrId) {
    logger.debug("Creating ADD Thread");
    NeutronQoSExecutor aqe =
        new NeutronQoSExecutor(vnfrs, this.nfvo_configuration, neutron_handler);
    qtScheduler.schedule(aqe, 100, TimeUnit.MILLISECONDS);
    logger.debug("ADD Thread created and scheduled");
  }

  public void removeQos(Set<VirtualNetworkFunctionRecord> vnfrs, String nsrId) {
    logger.debug("Creating REMOVE Thread");
    logger.debug(
        "Neutron does delete the ports and the applied QoS on machine deletion, will not create REMOVE Thread");
  }
}

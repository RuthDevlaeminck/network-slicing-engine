package org.openbaton.nse.beans.core;

import org.openbaton.catalogue.mano.common.Ip;
import org.openbaton.catalogue.mano.descriptor.InternalVirtualLink;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.VimInstance;
import org.openbaton.catalogue.security.Project;
import org.openbaton.nse.beans.adapters.openstack.NeutronQoSExecutor;
import org.openbaton.nse.beans.adapters.openstack.NeutronQoSHandler;
import org.openbaton.nse.beans.adapters.openstack.OpenStackTools;
import org.openbaton.nse.openbaton.OpenBatonTools;
import org.openbaton.nse.properties.NfvoProperties;
import org.openbaton.nse.properties.NseProperties;
import org.openbaton.nse.utils.*;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.api.exception.SDKException;
import org.openstack4j.api.OSClient;
import org.openstack4j.model.compute.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Created by lgr on 3/1/18.
 */
@RestController
public class Api {

  private static Logger logger = LoggerFactory.getLogger(Api.class);

  private OpenStackOverview osOverview = new OpenStackOverview();
  private ArrayList<VimInstance> vim_list = new ArrayList<VimInstance>();
  private ArrayList<VirtualNetworkFunctionRecord> vnfr_list =
      new ArrayList<VirtualNetworkFunctionRecord>();

  private NeutronQoSHandler neutron_handler = new NeutronQoSHandler();

  private OpenStackTools osTools = new OpenStackTools();
  private OpenBatonTools obTools = new OpenBatonTools();

  private String curr_hash = UUID.randomUUID().toString();

  // Function to be called if there a NSR has been scaled, deleted or instantiated
  public void notifyChange() {
    curr_hash = UUID.randomUUID().toString();
  }

  @Autowired private NfvoProperties nfvo_configuration;
  @Autowired private NseProperties nse_configuration;

  public ArrayList<VirtualNetworkFunctionRecord> getVnfr_list() {
    return vnfr_list;
  }

  public void setVnfr_list(ArrayList<VirtualNetworkFunctionRecord> vnfr_list) {
    this.vnfr_list = vnfr_list;
  }

  public ArrayList<VimInstance> getVim_list() {
    return vim_list;
  }

  public void setVim_list(ArrayList<VimInstance> vim_list) {
    this.vim_list = vim_list;
  }

  @CrossOrigin(origins = "*")
  @RequestMapping("/overview")
  public OpenStackOverview getOverview() {
    updateOpenStackOverview();
    return this.osOverview;
  }

  // Method to be called by the NSE-GUI to apply bandwidth limitations directly on a whole network
  @CrossOrigin(origins = "*")
  @RequestMapping("/assign-net-policy")
  public void assignNetPolicy(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim,
      @RequestParam(value = "net", defaultValue = "net_id") String net,
      @RequestParam(value = "policy", defaultValue = "no_policy") String policy) {
    logger.debug(
        "Received assign policy request for vim : "
            + vim
            + " in project "
            + project
            + " network : "
            + net
            + " policy : "
            + policy);
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {
          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(v);
            logger.debug("Found OSclient");
            String token = osTools.getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = osTools.getNeutronEndpoint(v, token);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.assignQoSPolicyToNetwork(net, policy);
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    this.notifyChange();
  }

  // Method to be called by the NSE-GUI to apply bandwidth limitations directly on a port
  @CrossOrigin(origins = "*")
  @RequestMapping("/assign-port-policy")
  public void assignPortPolicy(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim,
      @RequestParam(value = "port", defaultValue = "port_id") String port,
      @RequestParam(value = "policy", defaultValue = "no_policy") String policy) {
    logger.debug(
        "Received assign policy request for vim : "
            + vim
            + " in project "
            + project
            + " port : "
            + port
            + " policy : "
            + policy);
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {
          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(v);
            logger.debug("Found OSclient");
            String token = osTools.getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = osTools.getNeutronEndpoint(v, token);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.assignQoSPolicyToPort(port, policy);
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    this.notifyChange();
  }

  // Method to be called by the NSE-GUI to delete QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/policy-delete")
  public void deletePolicy(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "id", defaultValue = "policy_id") String id,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug(
        "Received QoS policy delete request for vim : "
            + vim
            + " in project "
            + project
            + " policy : "
            + id);
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {

          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(v);
            logger.debug("Found OSclient");
            String token = osTools.getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = osTools.getNeutronEndpoint(v, token);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.deleteQoSPolicy(id);
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    this.notifyChange();
  }

  // Method to be called by the NSE-GUI to delete QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/bandwidth-rule-delete")
  public void deleteBandwidthRule(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "policy_id", defaultValue = "policy_id") String policy_id,
      @RequestParam(value = "rule_id", defaultValue = "rule_id") String rule_id,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug(
        "Received bandwidth rule delete request for vim : "
            + vim
            + " in project "
            + project
            + " policy : "
            + policy_id
            + " rule : "
            + rule_id);
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {

          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(v);
            logger.debug("Found OSclient");
            String token = osTools.getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = osTools.getNeutronEndpoint(v, token);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.deleteBandwidthRule(rule_id, policy_id);
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    this.notifyChange();
  }

  // Method to be called by the NSE-GUI to create QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/bandwidth-rule-create")
  public void createBandwidthRule(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "id", defaultValue = "policy_id") String id,
      @RequestParam(value = "type", defaultValue = "bandwidth_limit_rule") String type,
      @RequestParam(value = "burst", defaultValue = "0") String burst,
      @RequestParam(value = "kbps", defaultValue = "0") String kbps,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    if (!burst.matches("[0-9]+")) {
      logger.error(
          "Cannot create bandwidth rule with max_kbps : \""
              + burst
              + "\" please enter a valid number");
      return;
    }
    if (!kbps.matches("[0-9]+")) {
      logger.error(
          "Cannot create bandwidth rule with max_kbps : \""
              + kbps
              + "\" please enter a valid number");
      return;
    }
    logger.debug(
        "Received bandwidth rule create request for vim : " + vim + " in project " + project);
    logger.debug(
        "Policy id : " + id + " type : " + type + " max_kbps : " + kbps + " burst : " + burst);
    OpenStackBandwidthRule rule = new OpenStackBandwidthRule();
    rule.setMax_burst_kbps(new Integer(burst));
    rule.setType(type);
    rule.setMax_kbps(new Integer(kbps));
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {

          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(v);
            logger.debug("Found OSclient");
            String token = osTools.getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = osTools.getNeutronEndpoint(v, token);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.createBandwidthRule(rule, id);
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    this.notifyChange();
  }

  // Method to be called by the NSE-GUI to create QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/policy-create")
  public void createPolicy(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "name", defaultValue = "name") String name,
      @RequestParam(value = "type", defaultValue = "bandwidth_limit_rule") String type,
      @RequestParam(value = "burst", defaultValue = "0") String burst,
      @RequestParam(value = "kbps", defaultValue = "0") String kbps,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    if (!burst.matches("[0-9]+")) {
      logger.error(
          "Cannot create policy with max_burst_kbps : \""
              + burst
              + "\" please enter a valid number");
      return;
    }
    if (!kbps.matches("[0-9]+")) {
      logger.error(
          "Cannot create policy with max_kbps : \"" + kbps + "\" please enter a valid number");
      return;
    }
    logger.debug("Received QoS policy create request for vim : " + vim + " in project " + project);
    logger.debug(
        "Name : " + name + " type : " + type + " max_kbps : " + kbps + " burst : " + burst);
    OpenStackQoSPolicy policy = new OpenStackQoSPolicy();
    policy.setName(name);
    ArrayList<OpenStackBandwidthRule> rules = new ArrayList<OpenStackBandwidthRule>();
    rules.add(new OpenStackBandwidthRule(burst, kbps, type));
    policy.setRules(rules);
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {

          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());
            OSClient tmp_os = osTools.getOSClient(v);
            logger.debug("Found OSclient");
            String token = osTools.getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = osTools.getNeutronEndpoint(v, token);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            neutron_executor.createQoSPolicy(policy);
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    this.notifyChange();
  }

  // Method to be called by the NSE-GUI to list networks
  @CrossOrigin(origins = "*")
  @RequestMapping("/list-ports")
  public ArrayList<OpenStackPort> listPorts(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug("Received port list request for vim : " + vim + " in project " + project);
    ArrayList<OpenStackPort> port_list = new ArrayList<OpenStackPort>();
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {
          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());

            OSClient tmp_os = osTools.getOSClient(v);
            logger.debug("Found OSclient");
            String token = osTools.getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = osTools.getNeutronEndpoint(v, token);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            port_list = neutron_executor.listPorts();
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    return port_list;
  }

  // Method to be called by the NSE-GUI to list networks
  @CrossOrigin(origins = "*")
  @RequestMapping("/list-networks")
  public ArrayList<OpenStackNetwork> listNetworks(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug("Received network list request for vim : " + vim + " in project " + project);
    ArrayList<OpenStackNetwork> net_list = new ArrayList<OpenStackNetwork>();
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {
          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());

            OSClient tmp_os = osTools.getOSClient(v);
            logger.debug("Found OSclient");
            String token = osTools.getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = osTools.getNeutronEndpoint(v, token);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            net_list = neutron_executor.listNetworks();
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    return net_list;
  }

  // Method to be called by the NSE-GUI to list QoS policies
  @CrossOrigin(origins = "*")
  @RequestMapping("/list-qos-policies")
  public ArrayList<OpenStackQoSPolicy> listQoSPolicies(
      @RequestParam(value = "project", defaultValue = "project_id") String project,
      @RequestParam(value = "vim", defaultValue = "vim_id") String vim) {
    logger.debug("Received QoS policy list request for vim : " + vim + " in project " + project);
    //String qos_rules = "[]";
    //JSONArray qos_rules = new JSONArray();
    ArrayList<OpenStackQoSPolicy> qos_policy_list = new ArrayList<OpenStackQoSPolicy>();
    for (VimInstance v : vim_list) {
      if (v.getId().equals(vim)) {
        logger.debug("Found vim-instance to work with");
        if (v.getType().equals("openstack")) {

          NFVORequestor nfvoRequestor = null;
          try {
            nfvoRequestor =
                new NFVORequestor(
                    "nse",
                    project,
                    nfvo_configuration.getIp(),
                    nfvo_configuration.getPort(),
                    "1",
                    false,
                    nse_configuration.getService().getKey());

            OSClient tmp_os = osTools.getOSClient(v);
            logger.debug("Found OSclient");
            String token = osTools.getAuthToken(tmp_os, v);
            logger.debug("Found token");
            String neutron_access = osTools.getNeutronEndpoint(v, token);
            Map<String, String> creds = obTools.getDatacenterCredentials(nfvoRequestor, v.getId());
            creds.put("neutron", neutron_access);
            NeutronQoSExecutor neutron_executor =
                new NeutronQoSExecutor(neutron_handler, token, v, creds);
            qos_policy_list = neutron_executor.getNeutronQosRules();
          } catch (SDKException e) {
            e.printStackTrace();
          }
        } else {
          logger.warn("VIM type " + v.getType() + " not supported yet");
        }
      }
    }
    return qos_policy_list;
  }

  // Method to be called by the NSE-GUI to scale out
  @CrossOrigin(origins = "*")
  @RequestMapping("/scale-out")
  public void scaleOut(
      @RequestParam(value = "project", defaultValue = "project_id") String project_id,
      @RequestParam(value = "vnfr", defaultValue = "vnfr_id") String vnfr_id) {
    logger.debug(
        "Received SCALE out operation for vnfr " + vnfr_id + " belonging to project " + project_id);
    try {
      NFVORequestor nfvoRequestor =
          new NFVORequestor(
              "nse",
              project_id,
              nfvo_configuration.getIp(),
              nfvo_configuration.getPort(),
              "1",
              false,
              nse_configuration.getService().getKey());
      for (VirtualNetworkFunctionRecord vnfr : vnfr_list) {
        if (vnfr.getId().equals(vnfr_id)) {
          boolean scaled = false;
          for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            if (scaled == true) break;
            if (vdu.getVnfc_instance().size() < vdu.getScale_in_out()
                && (vdu.getVnfc().iterator().hasNext())) {
              VNFComponent vnfComponent = vdu.getVnfc().iterator().next();
              nfvoRequestor
                  .getNetworkServiceRecordAgent()
                  .createVNFCInstance(
                      vnfr.getParent_ns_id(),
                      vnfr.getId(),
                      vnfComponent,
                      new ArrayList<String>(vdu.getVimInstanceName()));
              scaled = true;
            }
          }
          return;
        }
      }
    } catch (SDKException e) {
      logger.error("Problem instantiating NFVORequestor");
    } catch (FileNotFoundException e) {
      logger.error("Problem scaling");
    }
  }

  // Method to be called by the NSE-GUI to scale in
  @CrossOrigin(origins = "*")
  @RequestMapping("/scale-in")
  public void scaleIn(
      @RequestParam(value = "project", defaultValue = "project_id") String project_id,
      @RequestParam(value = "vnfci", defaultValue = "vnfci_hostname") String vnfci_hostname,
      @RequestParam(value = "vnfr", defaultValue = "vnfr_id") String vnfr_id) {
    logger.debug(
        "Received SCALE in operation for vnfr " + vnfr_id + " belonging to project " + project_id);
    try {
      NFVORequestor nfvoRequestor =
          new NFVORequestor(
              "nse",
              project_id,
              nfvo_configuration.getIp(),
              nfvo_configuration.getPort(),
              "1",
              false,
              nse_configuration.getService().getKey());
      for (VirtualNetworkFunctionRecord vnfr : vnfr_list) {
        if (vnfr.getId().equals(vnfr_id)) {
          boolean scaled = false;
          for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            if (scaled == true) break;
            Set<VNFCInstance> vnfcInstancesToRemove = new HashSet<>();
            for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
              if (vnfcInstance.getState() == null
                  || vnfcInstance.getState().toLowerCase().equals("active")) {
                vnfcInstancesToRemove.add(vnfcInstance);
              }
            }
            if (vnfcInstancesToRemove.size() > 1 && vnfcInstancesToRemove.iterator().hasNext()) {
              // If no specific vnfci id to remove has been set use the default way and remove a random one..
              VNFCInstance vnfcInstance_remove = null;
              if (vnfci_hostname.equals("vnfci_hostname")) {
                vnfcInstance_remove = vnfcInstancesToRemove.iterator().next();
              } else {
                for (VNFCInstance currVnfci : vnfcInstancesToRemove) {
                  if (currVnfci.getHostname().equals(vnfci_hostname)) {
                    vnfcInstance_remove = currVnfci;
                  }
                }
              }
              if (vnfcInstance_remove == null) {
                logger.warn(
                    "Not found VNFCInstance in VDU " + vdu.getId() + " that could be removed");
                break;
              }
              nfvoRequestor
                  .getNetworkServiceRecordAgent()
                  .deleteVNFCInstance(
                      vnfr.getParent_ns_id(),
                      vnfr.getId(),
                      vdu.getId(),
                      vnfcInstance_remove.getId());
              scaled = true;
            }
          }
          return;
        }
      }
    } catch (SDKException e) {
      logger.error("Problem instantiating NFVORequestor");
    } catch (FileNotFoundException e) {
      logger.error("Problem scaling");
    }
    // Currently the NSE does not receive any SCALE-In events ( A VNF needs to have a relation to itself therefor ), thus we have a workaround here
    finally {
      notifyChange();
    }
  }

  private void updateOpenStackOverview() {
    // Check if the there were any addQos / removeQos tasks in the meanwhile ( relates to any scale, error or instantiate events )
    // to avoid running unnecessary tasks if there were no changes at all...
    if (this.osOverview != null) {
      if (this.osOverview.getCurrent_hash() != null) {
        if (this.osOverview.getCurrent_hash().equals(this.curr_hash)) {
          return;
        }
      }
    }

    this.osOverview = new OpenStackOverview();
    //ArrayList<Map<String, Object>> project_nsr_map = null;
    //ArrayList<Map<String, Object>> complete_computeNodeMap = new ArrayList<>();

    NFVORequestor nfvo_nsr_req = null;
    NFVORequestor nfvo_default_req = null;

    // Set up a variable which contains the already processed vims, we distinguish via the auth_url + user + tenant here
    // to avoid contacting the same infrastructure used in different projects.
    ArrayList<Integer> processed_vims = new ArrayList<Integer>();
    // Set up a map containing all the vim ids listed to the internal generated hash
    HashMap<Integer, Object> vim_map = new HashMap<Integer, Object>();
    // Set up a map containing all external vim ids together with their names
    HashMap<String, String> vim_name_map = new HashMap<String, String>();
    // Set up a map containing all external vim ids together with their names
    HashMap<String, String> vim_type_map = new HashMap<String, String>();
    // Set up a map containing all external vim ids together with their projects in which they are used
    HashMap<String, Object> vim_project_map = new HashMap<String, Object>();
    // Set up a map containing all internal vim hashs and related node information ( openstack only currently)
    HashMap<Integer, Object> node_map = new HashMap<Integer, Object>();
    // Set up a map containing all projects ids together with their names
    HashMap<String, String> project_id_map = new HashMap<String, String>();
    // Set up a map containing all projects together with a list of nsr ids
    HashMap<String, Object> project_nsr_map = new HashMap<String, Object>();
    // Set up a map containing all projects ids together with their names
    HashMap<String, String> nsr_name_map = new HashMap<String, String>();
    // Set up a map containing all projects together with a list of nsr ids
    HashMap<String, Object> nsr_vnfr_map = new HashMap<String, Object>();
    // Set up a map containing all vnf names with their ids
    HashMap<String, String> vnfr_name_map = new HashMap<String, String>();
    // Set up a map containing the vnfs and their networks from Open Baton side ( virtual link records )
    HashMap<String, Object> vnfr_vlr_map = new HashMap<String, Object>();
    // Set up a map containing all vlr ids together with their names
    HashMap<String, String> vlr_name_map = new HashMap<String, String>();
    // Set up a map containing all vlr ids together with their assigned bandwidth qualities
    HashMap<String, String> vlr_quality_map = new HashMap<String, String>();
    // Set up a map containing all vnfs and their vdus
    HashMap<String, Object> vnfr_vdu_map = new HashMap<String, Object>();
    // Set up a a map containing the names of each vdu listed by their ids
    HashMap<String, String> vdu_name_map = new HashMap<String, String>();
    // Set up a map containing the vdu and their vnfcis
    HashMap<String, Object> vdu_vnfci_map = new HashMap<String, Object>();
    // Set up a map containing the vnfci names with their ids ( These are the host names in the end )
    HashMap<String, String> vnfci_name_map = new HashMap<String, String>();
    // Set up a map containing all vnfci hostnames with their related vnf id
    HashMap<String, String> vnfci_vnfr_map = new HashMap<String, String>();
    // Set up a map containing the ips of each vnfci
    HashMap<String, Object> vnfci_ip_map = new HashMap<String, Object>();
    // Set up a map containing the names of the networks for each ip
    HashMap<String, String> ip_name_map = new HashMap<String, String>();
    // Set up a map containing the ips of the networks/ip ids
    HashMap<String, String> ip_addresses_map = new HashMap<String, String>();
    // Set up a map containing the vnfci ids together with their vim ids..
    HashMap<String, Integer> vnfci_vim_map = new HashMap<String, Integer>();
    // Set up a map containing the vdu id together with the maximum number of vnfc instances
    HashMap<String, Integer> vdu_scale_map = new HashMap<String, Integer>();

    // ###### OpenStack related
    // Set up a map containing the OpenStack port ids listed to the internal hash of the vim
    HashMap<Integer, Object> port_id_map = new HashMap<Integer, Object>();
    // Set up a map containing the OpenStack port ids together with all their ip addresses
    HashMap<String, Object> port_ip_map = new HashMap<String, Object>();
    // A list of ips which have to be checked for ports + subnets + nets ( listed by internal hash..)
    HashMap<Integer, Object> ips_to_be_checked = new HashMap<Integer, Object>();
    // A simple map which saves the reference to the osclients for specific nodes
    // HashMap<Integer, OSClient> os_client_map = new HashMap<Integer, OSClient>();
    // A simple map which saves the reference to the osclients ( via a vim instaces )
    HashMap<Integer, VimInstance> os_vim_map = new HashMap<Integer, VimInstance>();

    // Set up a map containing the OpenStack port ids listed with their parent network id
    HashMap<String, String> port_net_map = new HashMap<String, String>();
    // Set up a map containing the OpenStack network ids listed with their names
    HashMap<String, String> net_name_map = new HashMap<String, String>();
    // Set up a map containing the vnfci ids listed with their related hypervisor/ compute node
    HashMap<String, String> vnfci_hypervisor_map = new HashMap<String, String>();

    // Set up a map containing the vlr id together with the external network id
    HashMap<String, String> vlr_ext_net_map = new HashMap<String, String>();

    try {
      nfvo_default_req =
          new NFVORequestor(
              nfvo_configuration.getUsername(),
              nfvo_configuration.getPassword(),
              "*",
              false,
              nfvo_configuration.getIp(),
              nfvo_configuration.getPort(),
              "1");
      // Iterate over all projects and collect all NSRs
      for (Project project : nfvo_default_req.getProjectAgent().findAll()) {
        //logger.debug("Checking project : " + project.getName());
        nfvo_nsr_req =
            new NFVORequestor(
                "nse",
                project.getId(),
                nfvo_configuration.getIp(),
                nfvo_configuration.getPort(),
                "1",
                false,
                nse_configuration.getService().getKey());
        if (nfvo_nsr_req != null) {
          List<NetworkServiceRecord> nsr_list =
              nfvo_nsr_req.getNetworkServiceRecordAgent().findAll();
          if (!nsr_list.isEmpty()) {
            project_id_map.put(project.getId(), project.getName());
          }
          // ###################################################
          logger.debug(String.valueOf(nsr_list));
          for (NetworkServiceRecord nsr : nsr_list) {
            nsr_name_map.put(nsr.getId(), nsr.getName());
            ArrayList<String> tmp_nsrs;
            if (project_nsr_map.containsKey(project.getId())) {
              tmp_nsrs = ((ArrayList<String>) project_nsr_map.get(project.getId()));
              tmp_nsrs.add(nsr.getId());
            } else {
              tmp_nsrs = new ArrayList<String>();
              tmp_nsrs.add(nsr.getId());
              project_nsr_map.put(project.getId(), tmp_nsrs);
            }
            for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
              // Remove all occurences matching the old id
              for (int x = 0; x < vnfr_list.size(); x++) {
                VirtualNetworkFunctionRecord int_vnfr = vnfr_list.get(x);
                if (int_vnfr.getId().equals(vnfr.getId())) {
                  vnfr_list.remove(int_vnfr);
                }
              }
              vnfr_list.add(vnfr);
              vnfr_name_map.put(vnfr.getId(), vnfr.getName());
              ArrayList<String> tmp_vnfs;
              if (nsr_vnfr_map.containsKey(nsr.getId())) {
                tmp_vnfs = ((ArrayList<String>) nsr_vnfr_map.get(nsr.getId()));
                tmp_vnfs.add(vnfr.getId());
              } else {
                tmp_vnfs = new ArrayList<String>();
                tmp_vnfs.add(vnfr.getId());
                nsr_vnfr_map.put(nsr.getId(), tmp_vnfs);
              }
              for (InternalVirtualLink vlr : vnfr.getVirtual_link()) {
                ArrayList<String> tmp_vlrs;
                if (vnfr_vlr_map.containsKey(vnfr.getId())) {
                  tmp_vlrs = ((ArrayList<String>) vnfr_vlr_map.get(vnfr.getId()));
                  tmp_vlrs.add(vlr.getId());
                } else {
                  tmp_vlrs = new ArrayList<String>();
                  tmp_vlrs.add(vlr.getId());
                  vnfr_vlr_map.put(vnfr.getId(), tmp_vlrs);
                }
                vlr_name_map.put(vlr.getId(), vlr.getName());
                for (String qosParam : vlr.getQos()) {
                  if (qosParam.contains("minimum")
                      || qosParam.contains("maximum")
                      || qosParam.contains("policy")) {
                    vlr_quality_map.put(vlr.getId(), vlr.getQos().toString());
                  }
                }
              }
              for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                vdu_scale_map.put(vdu.getId(), vdu.getScale_in_out());
                ArrayList<String> tmp_vdus;
                if (vnfr_vdu_map.containsKey(vnfr.getId())) {
                  tmp_vdus = ((ArrayList<String>) vnfr_vdu_map.get(vnfr.getId()));
                  tmp_vdus.add(vdu.getId());
                } else {
                  tmp_vdus = new ArrayList<String>();
                  tmp_vdus.add(vdu.getId());
                  vnfr_vdu_map.put(vnfr.getId(), tmp_vdus);
                }
                vdu_name_map.put(vdu.getId(), vdu.getName());
                for (VNFCInstance vnfci : vdu.getVnfc_instance()) {
                  vnfci_name_map.put(vnfci.getId(), vnfci.getHostname());
                  vnfci_vnfr_map.put(vnfci.getHostname(), vnfr.getId());
                  ArrayList<String> tmp_vnfcis;
                  if (vdu_vnfci_map.containsKey(vdu.getId())) {
                    tmp_vnfcis = ((ArrayList<String>) vdu_vnfci_map.get(vdu.getId()));
                    tmp_vnfcis.add(vnfci.getId());
                  } else {
                    tmp_vnfcis = new ArrayList<String>();
                    tmp_vnfcis.add(vnfci.getId());
                    vdu_vnfci_map.put(vdu.getId(), tmp_vnfcis);
                  }
                  VimInstance tmp_vim = obTools.getVimInstance(nfvo_nsr_req, vnfci.getVim_id());
                  //if (!vim_list.contains(tmp_vim)) {
                  //  vim_list.add(tmp_vim);
                  //}
                  // Remove all occurences matching the old id
                  for (int x = 0; x < vim_list.size(); x++) {
                    VimInstance vim = vim_list.get(x);
                    if (vim.getId().equals(tmp_vim.getId())) {
                      vim_list.remove(vim);
                    }
                  }
                  vim_list.add(tmp_vim);
                  ArrayList<String> tmp_list;
                  if (vim_project_map.containsKey(tmp_vim.getId())) {
                    tmp_list = (ArrayList<String>) vim_project_map.get(tmp_vim.getId());
                    if (!tmp_list.contains(project.getId())) {
                      tmp_list.add(project.getId());
                    }
                  } else {
                    tmp_list = new ArrayList<String>();
                    tmp_list.add(project.getId());
                    vim_project_map.put(tmp_vim.getId(), tmp_list);
                  }
                  // Generate an identifier internally to not distinguish vims by their internal id but at other crucial information to avoid contacting the same infrastructure
                  int vim_identifier =
                      (tmp_vim.getAuthUrl() + tmp_vim.getUsername() + tmp_vim.getTenant())
                              .hashCode()
                          & 0xfffffff;
                  if (!vim_name_map.containsKey(tmp_vim.getId())) {
                    vim_name_map.put(tmp_vim.getId(), tmp_vim.getName());
                    vim_type_map.put(tmp_vim.getId(), tmp_vim.getType());
                    //ArrayList<String> vlrs = (ArrayList<String>) vnfr_vlr_map.get(vnfr.getId());
                    for (org.openbaton.catalogue.nfvo.Network n : tmp_vim.getNetworks()) {
                      net_name_map.put(n.getExtId(), n.getName());
                      //for (String vlr_id : vlrs) {
                      //  if (vlr_name_map.get(vlr_id).equals(n.getName())) {
                      //    vlr_ext_net_map.put(vlr_id, n.getExtId());
                      //  }
                      //}
                    }
                  }
                  ArrayList<String> vlrs = (ArrayList<String>) vnfr_vlr_map.get(vnfr.getId());
                  for (org.openbaton.catalogue.nfvo.Network n : tmp_vim.getNetworks()) {
                    for (String vlr_id : vlrs) {
                      if (vlr_name_map.get(vlr_id).equals(n.getName())) {
                        vlr_ext_net_map.put(vlr_id, n.getExtId());
                      }
                    }
                  }
                  vnfci_vim_map.put(vnfci.getId(), vim_identifier);
                  for (Ip ip : vnfci.getIps()) {
                    ip_name_map.put(ip.getId(), ip.getNetName());
                    ip_addresses_map.put(ip.getId(), ip.getIp());
                    ArrayList<String> tmp_ips;
                    if (vnfci_ip_map.containsKey(vnfci.getId())) {
                      tmp_ips = ((ArrayList<String>) vnfci_ip_map.get(vnfci.getId()));
                      tmp_ips.add(ip.getId());
                    } else {
                      tmp_ips = new ArrayList<String>();
                      tmp_ips.add(ip.getId());
                      vnfci_ip_map.put(vnfci.getId(), tmp_ips);
                    }
                    ArrayList<String> tmp_ip_list;
                    if (ips_to_be_checked.containsKey(vim_identifier)) {
                      tmp_ip_list = ((ArrayList<String>) ips_to_be_checked.get(vim_identifier));
                      tmp_ip_list.add(ip.getIp());
                    } else {
                      tmp_ip_list = new ArrayList<String>();
                      tmp_ip_list.add(ip.getIp());
                      ips_to_be_checked.put(vim_identifier, tmp_ip_list);
                    }
                  }
                  if (!processed_vims.contains(vim_identifier)) {
                    processed_vims.add(vim_identifier);
                    ArrayList<String> tmp_vim_ids = new ArrayList<String>();
                    tmp_vim_ids.add(tmp_vim.getId());
                    vim_map.put(vim_identifier, tmp_vim_ids);

                    if (tmp_vim.getType().equals("openstack")) {
                      OSClient tmp_os = osTools.getOSClient(tmp_vim);
                      //if (!os_client_map.containsKey(vim_identifier)) {
                      //  os_client_map.put(vim_identifier, tmp_os);
                      //}
                      if (!os_vim_map.containsKey(vim_identifier)) {
                        os_vim_map.put(vim_identifier, tmp_vim);
                      }
                      Map<String, String> tmp_computeNodeMap = osTools.getComputeNodeMap(tmp_os);
                      if (tmp_computeNodeMap != null) {
                        // We collect all involved compute nodes
                        ArrayList<String> tmp_node_names = new ArrayList<String>();
                        for (String key : tmp_computeNodeMap.keySet()) {

                          tmp_node_names.add(key);
                        }
                        node_map.put(vim_identifier, tmp_node_names);
                      }
                    }
                  } else {
                    // in this case we already found the vim via the internal generated hash and only need to append the vim id to the hash in the map
                    ArrayList<String> vim_ids = ((ArrayList<String>) vim_map.get(vim_identifier));
                    if (!vim_ids.contains(tmp_vim.getId())) {
                      vim_ids.add(tmp_vim.getId());
                    }
                  }
                }
              }
            }
          }
        }
      }
      this.osOverview.setCurrent_hash(new String(this.curr_hash));
      this.osOverview.setVims(vim_map);
      this.osOverview.setVim_names(vim_name_map);
      this.osOverview.setVim_types(vim_type_map);
      this.osOverview.setVim_projects(vim_project_map);
      this.osOverview.setOs_nodes(node_map);
      this.osOverview.setProjects(project_id_map);
      this.osOverview.setNsrs(project_nsr_map);
      this.osOverview.setNsr_names(nsr_name_map);
      this.osOverview.setVnfr_names(vnfr_name_map);
      this.osOverview.setNsr_vnfrs(nsr_vnfr_map);
      this.osOverview.setVnfr_vlrs(vnfr_vlr_map);
      this.osOverview.setVlr_names(vlr_name_map);
      this.osOverview.setVlr_qualities(vlr_quality_map);
      this.osOverview.setVnfr_vdus(vnfr_vdu_map);
      this.osOverview.setVdu_names(vdu_name_map);
      this.osOverview.setVdu_vnfcis(vdu_vnfci_map);
      this.osOverview.setVnfci_names(vnfci_name_map);
      this.osOverview.setVnfci_vnfr(vnfci_vnfr_map);
      this.osOverview.setVnfci_ips(vnfci_ip_map);
      this.osOverview.setVdu_scale(vdu_scale_map);
      this.osOverview.setIp_names(ip_name_map);
      this.osOverview.setIp_addresses(ip_addresses_map);
      this.osOverview.setVnfci_vims(vnfci_vim_map);

      // TODO : Switch to threads to collect information of the infrastructure ( should become way faster )
      for (Integer i : node_map.keySet()) {
        OSClient os_client = osTools.getOSClient(os_vim_map.get(i));
        HashMap<String, Object> tmp_portMap =
            osTools.getPortIps(os_client, (ArrayList<String>) ips_to_be_checked.get(i));
        if (tmp_portMap != null) {
          for (String p_id : tmp_portMap.keySet()) {
            ArrayList<String> tmp_port_ids;
            if (port_id_map.containsKey(i)) {
              tmp_port_ids = ((ArrayList<String>) port_id_map.get(i));
              if (!tmp_port_ids.contains(p_id)) {
                tmp_port_ids.add(p_id);
              }
            } else {
              tmp_port_ids = new ArrayList<String>();
              tmp_port_ids.add(p_id);
              port_id_map.put(i, tmp_port_ids);
            }
          }
        }
        for (String key : tmp_portMap.keySet()) {
          port_ip_map.put(key, tmp_portMap.get(key));
        }
        //port_ip_map = tmp_portMap;
        // Collect information about the compute nodes...
        for (Server s : os_client.compute().servers().list()) {
          for (String vnfci_id : vnfci_name_map.keySet()) {
            if (vnfci_name_map.get(vnfci_id).equals(s.getName())) {
              //vnf_host_compute_map.put(vnfr.getName(), s.getHypervisorHostname());
              vnfci_hypervisor_map.put(s.getName(), s.getHypervisorHostname());
            }
          }
        }
      }
      // TODO : collect information about the os networks, to be able to integrate with the Open Baton view on resources
      for (Integer i : port_id_map.keySet()) {
        OSClient os_client = osTools.getOSClient(os_vim_map.get(i));
        for (String p_id : ((ArrayList<String>) port_id_map.get(i))) {
          // TODO : avoid contacting the infrastructure to often, maybe there is a better way of collecting all information in before
          port_net_map.put(p_id, os_client.networking().port().get(p_id).getNetworkId());
        }
        //for(Network n : tmp_os.networking().network().list()){
        //  net_name_map.put(n.getId(),n.getId());
        //}
      }
      // Well we should collect the network names together with their id's

      this.osOverview.setOs_port_ids(port_id_map);
      this.osOverview.setOs_port_ips(port_ip_map);
      this.osOverview.setOs_port_net_map(port_net_map);
      this.osOverview.setOs_net_names(net_name_map);
      this.osOverview.setVnfci_hypervisors(vnfci_hypervisor_map);
      this.osOverview.setVlr_ext_networks(vlr_ext_net_map);

      //logger.debug(vnfr_list.toString());

      // In the very end add the hosts and hypervisors which did not belong to any NSR
      //this.osOverview.setNodes(complete_computeNodeMap);
      //this.osOverview.setProjects(project_nsr_map);
      //logger.debug("updated overview");
    } catch (SDKException e) {
      logger.error("Problem instantiating NFVORequestor");
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }
}
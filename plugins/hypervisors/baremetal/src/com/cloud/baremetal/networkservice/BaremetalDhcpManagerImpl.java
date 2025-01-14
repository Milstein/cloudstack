// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//
// Automatically generated by addcopyright.py at 01/29/2013
// Apache License, Version 2.0 (the "License"); you may not use this
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//
// Automatically generated by addcopyright.py at 04/03/2012
package com.cloud.baremetal.networkservice;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import org.apache.cloudstack.api.AddBaremetalDhcpCmd;
import org.apache.cloudstack.api.ListBaremetalDhcpCmd;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupExternalDhcpCommand;
import com.cloud.agent.api.routing.DhcpEntryCommand;
import com.cloud.baremetal.database.BaremetalDhcpDao;
import com.cloud.baremetal.database.BaremetalDhcpVO;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.NetworkModel;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkServiceProviderDao;
import com.cloud.network.dao.PhysicalNetworkServiceProviderVO;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceStateAdapter;
import com.cloud.resource.ServerResource;
import com.cloud.resource.UnableDeleteHostException;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.QueryBuilder;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.UserVmDao;

@Local(value = { BaremetalDhcpManager.class })
public class BaremetalDhcpManagerImpl extends ManagerBase implements BaremetalDhcpManager, ResourceStateAdapter {
    private static final org.apache.log4j.Logger s_logger = Logger.getLogger(BaremetalDhcpManagerImpl.class);
    protected String _name;
    @Inject
    DataCenterDao _dcDao;
    @Inject
    HostDao _hostDao;
    @Inject
    AgentManager _agentMgr;
    @Inject
    HostPodDao _podDao;
    @Inject
    UserVmDao _userVmDao;
    @Inject
    ResourceManager _resourceMgr;
    @Inject
    NicDao _nicDao;
    @Inject
    PhysicalNetworkDao _physicalNetworkDao;
    @Inject
    PhysicalNetworkServiceProviderDao _physicalNetworkServiceProviderDao;
    @Inject
    BaremetalDhcpDao _extDhcpDao;
    @Inject
    NetworkModel _ntwkModel;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        _resourceMgr.registerResourceStateAdapter(this.getClass().getSimpleName(), this);
        return true;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        _resourceMgr.unregisterResourceStateAdapter(this.getClass().getSimpleName());
        return true;
    }

    @Override
    public String getName() {
        return _name;
    }

    protected String getDhcpServerGuid(String zoneId, String name, String ip) {
        return zoneId + "-" + name + "-" + ip;
    }

    @Override
    public boolean addVirtualMachineIntoNetwork(Network network, NicProfile nic, VirtualMachineProfile profile,
            DeployDestination dest, ReservationContext context) throws ResourceUnavailableException {
        Long zoneId = profile.getVirtualMachine().getDataCenterId();
        List<HostVO> hosts = _resourceMgr.listAllUpAndEnabledHosts(Type.BaremetalDhcp, null, null, zoneId);
        if (hosts.size() == 0) {
            throw new CloudRuntimeException("No external Dhcp found in zone " + zoneId);
        }

        if (hosts.size() > 1) {
            throw new CloudRuntimeException("Something wrong, more than 1 external Dhcp found in zone " + zoneId);
        }

        HostVO h = hosts.get(0);
        String dns = nic.getDns1();
        if (dns == null) {
            dns = nic.getDns2();
        }
        DhcpEntryCommand dhcpCommand = new DhcpEntryCommand(nic.getMacAddress(), nic.getIp4Address(), profile.getVirtualMachine().getHostName(), null, dns,
                nic.getGateway(), null, _ntwkModel.getExecuteInSeqNtwkElmtCmd());
        String errMsg = String.format("Set dhcp entry on external DHCP %1$s failed(ip=%2$s, mac=%3$s, vmname=%4$s)", h.getPrivateIpAddress(),
                nic.getIp4Address(), nic.getMacAddress(), profile.getVirtualMachine().getHostName());
        // prepareBareMetalDhcpEntry(nic, dhcpCommand);
        try {
            Answer ans = _agentMgr.send(h.getId(), dhcpCommand);
            if (ans.getResult()) {
                s_logger.debug(String.format("Set dhcp entry on external DHCP %1$s successfully(ip=%2$s, mac=%3$s, vmname=%4$s)", h.getPrivateIpAddress(),
                        nic.getIp4Address(), nic.getMacAddress(), profile.getVirtualMachine().getHostName()));
                return true;
            } else {
                s_logger.debug(errMsg + " " + ans.getDetails());
                throw new ResourceUnavailableException(errMsg, DataCenter.class, zoneId);
            }
        } catch (Exception e) {
            s_logger.debug(errMsg, e);
            throw new ResourceUnavailableException(errMsg + e.getMessage(), DataCenter.class, zoneId);
        }
    }

    @Override
    public HostVO createHostVOForConnectedAgent(HostVO host, StartupCommand[] cmd) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public HostVO createHostVOForDirectConnectAgent(HostVO host, StartupCommand[] startup, ServerResource resource, Map<String, String> details,
            List<String> hostTags) {
        if (!(startup[0] instanceof StartupExternalDhcpCommand)) {
            return null;
        }

        host.setType(Host.Type.BaremetalDhcp);
        return host;
    }

    @Override
    public DeleteHostAnswer deleteHost(HostVO host, boolean isForced, boolean isForceDeleteStorage) throws UnableDeleteHostException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    @DB
    public BaremetalDhcpVO addDchpServer(AddBaremetalDhcpCmd cmd) {
        PhysicalNetworkVO pNetwork = null;
        long zoneId;

        if (cmd.getPhysicalNetworkId() == null || cmd.getUrl() == null || cmd.getUsername() == null || cmd.getPassword() == null) {
            throw new IllegalArgumentException("At least one of the required parameters(physical network id, url, username, password) is null");
        }

        pNetwork = _physicalNetworkDao.findById(cmd.getPhysicalNetworkId());
        if (pNetwork == null) {
            throw new IllegalArgumentException("Could not find phyical network with ID: " + cmd.getPhysicalNetworkId());
        }
        zoneId = pNetwork.getDataCenterId();
        DataCenterVO zone = _dcDao.findById(zoneId);

        PhysicalNetworkServiceProviderVO ntwkSvcProvider = _physicalNetworkServiceProviderDao.findByServiceProvider(pNetwork.getId(),
        		BaremetalDhcpManager.BAREMETAL_DHCP_SERVICE_PROVIDER.getName());
        if (ntwkSvcProvider == null) {
            throw new CloudRuntimeException("Network Service Provider: " + BaremetalDhcpManager.BAREMETAL_DHCP_SERVICE_PROVIDER.getName() + " is not enabled in the physical network: "
                    + cmd.getPhysicalNetworkId() + "to add this device");
        } else if (ntwkSvcProvider.getState() == PhysicalNetworkServiceProvider.State.Shutdown) {
            throw new CloudRuntimeException("Network Service Provider: " + ntwkSvcProvider.getProviderName()
                    + " is in shutdown state in the physical network: " + cmd.getPhysicalNetworkId() + "to add this device");
        }

        List<HostVO> dhcps = _resourceMgr.listAllUpAndEnabledHosts(Host.Type.BaremetalDhcp, null, null, zoneId);
        if (dhcps.size() != 0) {
            throw new IllegalArgumentException("Already had a DHCP server in zone: " + zoneId);
        }

        URI uri;
        try {
            uri = new URI(cmd.getUrl());
        } catch (Exception e) {
            s_logger.debug(e);
            throw new IllegalArgumentException(e.getMessage());
        }

        String ipAddress = uri.getHost();
        if (ipAddress == null) {
            ipAddress = cmd.getUrl(); // the url is raw ip. For backforward compatibility, we have to support http://ip format as well
        }
        String guid = getDhcpServerGuid(Long.toString(zoneId), "ExternalDhcp", ipAddress);
        Map params = new HashMap<String, String>();
        params.put("type", cmd.getDhcpType());
        params.put("zone", Long.toString(zoneId));
        params.put("ip", ipAddress);
        params.put("username", cmd.getUsername());
        params.put("password", cmd.getPassword());
        params.put("guid", guid);
        String dns = zone.getDns1();
        if (dns == null) {
            dns = zone.getDns2();
        }
        params.put("dns", dns);

        ServerResource resource = null;
        try {
            if (cmd.getDhcpType().equalsIgnoreCase(BaremetalDhcpType.DNSMASQ.toString())) {
                resource = new BaremetalDnsmasqResource();
                resource.configure("Dnsmasq resource", params);
            } else if (cmd.getDhcpType().equalsIgnoreCase(BaremetalDhcpType.DHCPD.toString())) {
                resource = new BaremetalDhcpdResource();
                resource.configure("Dhcpd resource", params);
            } else {
                throw new CloudRuntimeException("Unsupport DHCP server type: " + cmd.getDhcpType());
            }
        } catch (Exception e) {
            s_logger.debug(e);
            throw new CloudRuntimeException(e.getMessage());
        }

        Host dhcpServer = _resourceMgr.addHost(zoneId, resource, Host.Type.BaremetalDhcp, params);
        if (dhcpServer == null) {
            throw new CloudRuntimeException("Cannot add external Dhcp server as a host");
        }

        BaremetalDhcpVO vo = new BaremetalDhcpVO();
        vo.setDeviceType(cmd.getDhcpType());
        vo.setHostId(dhcpServer.getId());
        vo.setNetworkServiceProviderId(ntwkSvcProvider.getId());
        vo.setPhysicalNetworkId(cmd.getPhysicalNetworkId());
        _extDhcpDao.persist(vo);
        return vo;
    }

    @Override
    public BaremetalDhcpResponse generateApiResponse(BaremetalDhcpVO vo) {
        BaremetalDhcpResponse response = new BaremetalDhcpResponse();
        response.setDeviceType(vo.getDeviceType());
        response.setId(vo.getUuid());
        HostVO host = _hostDao.findById(vo.getHostId());
        response.setUrl(host.getPrivateIpAddress());
        PhysicalNetworkVO nwVO = _physicalNetworkDao.findById(vo.getPhysicalNetworkId());
        response.setPhysicalNetworkId(nwVO.getUuid());
        PhysicalNetworkServiceProviderVO providerVO = _physicalNetworkServiceProviderDao.findById(vo.getNetworkServiceProviderId());
        response.setProviderId(providerVO.getUuid());
        response.setObjectName("baremetaldhcp");
        return response;
    }

    @Override
    public List<BaremetalDhcpResponse> listBaremetalDhcps(ListBaremetalDhcpCmd cmd) {
        List<BaremetalDhcpResponse> responses = new ArrayList<BaremetalDhcpResponse>();
        if (cmd.getId() != null) {
            BaremetalDhcpVO vo = _extDhcpDao.findById(cmd.getId());
            responses.add(generateApiResponse(vo));
            return responses;
        }

        QueryBuilder<BaremetalDhcpVO> sc = QueryBuilder.create(BaremetalDhcpVO.class);
        if (cmd.getDeviceType() != null) {
        	sc.and(sc.entity().getDeviceType(), Op.EQ, cmd.getDeviceType());
        }

        List<BaremetalDhcpVO> vos = sc.list();
        for (BaremetalDhcpVO vo : vos) {
            responses.add(generateApiResponse(vo));
        }
        return responses;
    }

	@Override
	public List<Class<?>> getCommands() {
	    List<Class<?>> cmds = new ArrayList<Class<?>>();
	    cmds.add(AddBaremetalDhcpCmd.class);
	    cmds.add(ListBaremetalDhcpCmd.class);
		return cmds;
	}
}

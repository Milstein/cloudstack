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

package com.cloud.network.router;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.api.command.admin.router.RebootRouterCmd;
import org.apache.cloudstack.api.command.admin.router.UpgradeRouterCmd;
import org.apache.cloudstack.api.command.admin.router.UpgradeRouterTemplateCmd;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.framework.config.ConfigDepot;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.jobs.AsyncJobManager;
import org.apache.cloudstack.framework.jobs.impl.AsyncJobVO;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.Listener;
import com.cloud.agent.api.AgentControlAnswer;
import com.cloud.agent.api.AgentControlCommand;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.BumpUpPriorityCommand;
import com.cloud.agent.api.CheckRouterAnswer;
import com.cloud.agent.api.CheckRouterCommand;
import com.cloud.agent.api.CheckS2SVpnConnectionsAnswer;
import com.cloud.agent.api.CheckS2SVpnConnectionsCommand;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.GetDomRVersionAnswer;
import com.cloud.agent.api.GetDomRVersionCmd;
import com.cloud.agent.api.ModifySshKeysCommand;
import com.cloud.agent.api.NetworkUsageAnswer;
import com.cloud.agent.api.NetworkUsageCommand;
import com.cloud.agent.api.PvlanSetupCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.check.CheckSshAnswer;
import com.cloud.agent.api.check.CheckSshCommand;
import com.cloud.agent.api.routing.CreateIpAliasCommand;
import com.cloud.agent.api.routing.DeleteIpAliasCommand;
import com.cloud.agent.api.routing.DhcpEntryCommand;
import com.cloud.agent.api.routing.DnsMasqConfigCommand;
import com.cloud.agent.api.routing.IpAliasTO;
import com.cloud.agent.api.routing.IpAssocCommand;
import com.cloud.agent.api.routing.LoadBalancerConfigCommand;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.agent.api.routing.RemoteAccessVpnCfgCommand;
import com.cloud.agent.api.routing.SavePasswordCommand;
import com.cloud.agent.api.routing.SetFirewallRulesCommand;
import com.cloud.agent.api.routing.SetMonitorServiceCommand;
import com.cloud.agent.api.routing.SetPortForwardingRulesCommand;
import com.cloud.agent.api.routing.SetPortForwardingRulesVpcCommand;
import com.cloud.agent.api.routing.SetStaticNatRulesCommand;
import com.cloud.agent.api.routing.VmDataCommand;
import com.cloud.agent.api.routing.VpnUsersCfgCommand;
import com.cloud.agent.api.to.DhcpTO;
import com.cloud.agent.api.to.FirewallRuleTO;
import com.cloud.agent.api.to.IpAddressTO;
import com.cloud.agent.api.to.LoadBalancerTO;
import com.cloud.agent.api.to.MonitorServiceTO;
import com.cloud.agent.api.to.PortForwardingRuleTO;
import com.cloud.agent.api.to.StaticNatRuleTO;
import com.cloud.agent.manager.Commands;
import com.cloud.alert.AlertManager;
import com.cloud.api.ApiAsyncJobDispatcher;
import com.cloud.api.ApiDispatcher;
import com.cloud.api.ApiGsonHelper;
import com.cloud.cluster.ClusterManager;
import com.cloud.cluster.ManagementServerHostVO;
import com.cloud.cluster.dao.ManagementServerHostDao;
import com.cloud.configuration.Config;
import com.cloud.configuration.ConfigurationManager;
import com.cloud.configuration.ZoneConfig;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.Pod;
import com.cloud.dc.Vlan;
import com.cloud.dc.VlanVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.dc.dao.VlanDao;
import com.cloud.deploy.DataCenterDeployment;
import com.cloud.deploy.DeployDestination;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.deploy.DeploymentPlanner.ExcludeList;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.ConnectionException;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InsufficientServerCapacityException;
import com.cloud.exception.InsufficientVirtualNetworkCapcityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.exception.StorageUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.maint.Version;
import com.cloud.network.IpAddress;
import com.cloud.network.IpAddressManager;
import com.cloud.network.MonitoringService;
import com.cloud.network.Network;
import com.cloud.network.Network.GuestType;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkService;
import com.cloud.network.Networks.BroadcastDomainType;
import com.cloud.network.Networks.IsolationType;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.PublicIpAddress;
import com.cloud.network.RemoteAccessVpn;
import com.cloud.network.Site2SiteCustomerGateway;
import com.cloud.network.Site2SiteVpnConnection;
import com.cloud.network.SshKeysDistriMonitor;
import com.cloud.network.VirtualNetworkApplianceService;
import com.cloud.network.VirtualRouterProvider;
import com.cloud.network.VirtualRouterProvider.Type;
import com.cloud.network.VpnUser;
import com.cloud.network.VpnUserVO;
import com.cloud.network.addr.PublicIp;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.LoadBalancerDao;
import com.cloud.network.dao.LoadBalancerVMMapDao;
import com.cloud.network.dao.LoadBalancerVO;
import com.cloud.network.dao.MonitoringServiceDao;
import com.cloud.network.dao.MonitoringServiceVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkServiceProviderDao;
import com.cloud.network.dao.RemoteAccessVpnDao;
import com.cloud.network.dao.Site2SiteCustomerGatewayDao;
import com.cloud.network.dao.Site2SiteVpnConnectionDao;
import com.cloud.network.dao.Site2SiteVpnConnectionVO;
import com.cloud.network.dao.Site2SiteVpnGatewayDao;
import com.cloud.network.dao.UserIpv6AddressDao;
import com.cloud.network.dao.VirtualRouterProviderDao;
import com.cloud.network.dao.VpnUserDao;
import com.cloud.network.lb.LoadBalancingRule;
import com.cloud.network.lb.LoadBalancingRule.LbDestination;
import com.cloud.network.lb.LoadBalancingRule.LbHealthCheckPolicy;
import com.cloud.network.lb.LoadBalancingRule.LbSslCert;
import com.cloud.network.lb.LoadBalancingRule.LbStickinessPolicy;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.network.router.VirtualRouter.RedundantState;
import com.cloud.network.router.VirtualRouter.Role;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.FirewallRule.Purpose;
import com.cloud.network.rules.FirewallRuleVO;
import com.cloud.network.rules.LoadBalancerContainer.Scheme;
import com.cloud.network.rules.PortForwardingRule;
import com.cloud.network.rules.RulesManager;
import com.cloud.network.rules.StaticNat;
import com.cloud.network.rules.StaticNatImpl;
import com.cloud.network.rules.StaticNatRule;
import com.cloud.network.rules.dao.PortForwardingRulesDao;
import com.cloud.network.vpn.Site2SiteVpnManager;
import com.cloud.offering.NetworkOffering;
import com.cloud.offering.ServiceOffering;
import com.cloud.offerings.NetworkOfferingVO;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.resource.ResourceManager;
import com.cloud.server.ConfigurationServer;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.GuestOSDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.User;
import com.cloud.user.UserStatisticsVO;
import com.cloud.user.UserStatsLogVO;
import com.cloud.user.UserVO;
import com.cloud.user.dao.UserDao;
import com.cloud.user.dao.UserStatisticsDao;
import com.cloud.user.dao.UserStatsLogDao;
import com.cloud.uservm.UserVm;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.PasswordGenerator;
import com.cloud.utils.StringUtils;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.JoinBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.Ip;
import com.cloud.utils.net.MacAddress;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.DomainRouterVO;
import com.cloud.vm.Nic;
import com.cloud.vm.NicIpAlias;
import com.cloud.vm.NicProfile;
import com.cloud.vm.NicVO;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.ReservationContextImpl;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.State;
import com.cloud.vm.VirtualMachineGuru;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.VirtualMachineName;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.VirtualMachineProfile.Param;
import com.cloud.vm.dao.DomainRouterDao;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.NicIpAliasDao;
import com.cloud.vm.dao.NicIpAliasVO;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.UserVmDetailsDao;
import com.cloud.vm.dao.VMInstanceDao;

/**
 * VirtualNetworkApplianceManagerImpl manages the different types of virtual network appliances available in the Cloud Stack.
 */
@Local(value = { VirtualNetworkApplianceManager.class, VirtualNetworkApplianceService.class })
public class VirtualNetworkApplianceManagerImpl extends ManagerBase implements VirtualNetworkApplianceManager, VirtualNetworkApplianceService,
        VirtualMachineGuru, Listener, Configurable {
    private static final Logger s_logger = Logger.getLogger(VirtualNetworkApplianceManagerImpl.class);

    @Inject
    EntityManager _entityMgr;
    @Inject
    DataCenterDao _dcDao = null;
    @Inject
    VlanDao _vlanDao = null;
    @Inject
    FirewallRulesDao _rulesDao = null;
    @Inject
    LoadBalancerDao _loadBalancerDao = null;
    @Inject
    LoadBalancerVMMapDao _loadBalancerVMMapDao = null;
    @Inject
    IPAddressDao _ipAddressDao = null;
    @Inject
    VMTemplateDao _templateDao = null;
    @Inject
    DomainRouterDao _routerDao = null;
    @Inject
    UserDao _userDao = null;
    @Inject
    UserStatisticsDao _userStatsDao = null;
    @Inject
    HostDao _hostDao = null;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    HostPodDao _podDao = null;
    @Inject
    UserStatsLogDao _userStatsLogDao = null;
    @Inject
    AgentManager _agentMgr;
    @Inject
    AlertManager _alertMgr;
    @Inject
    AccountManager _accountMgr;
    @Inject
    ConfigurationManager _configMgr;
    @Inject
    ConfigurationServer _configServer;
    @Inject
    ServiceOfferingDao _serviceOfferingDao = null;
    @Inject
    UserVmDao _userVmDao;
    @Inject VMInstanceDao _vmDao;
    @Inject
    NetworkOfferingDao _networkOfferingDao = null;
    @Inject
    GuestOSDao _guestOSDao = null;
    @Inject
    NetworkOrchestrationService _networkMgr;
    @Inject
    NetworkModel _networkModel;
    @Inject
    VirtualMachineManager _itMgr;
    @Inject
    VpnUserDao _vpnUsersDao;
    @Inject
    RulesManager _rulesMgr;
    @Inject
    NetworkDao _networkDao;
    @Inject
    LoadBalancingRulesManager _lbMgr;
    @Inject
    PortForwardingRulesDao _pfRulesDao;
    @Inject
    RemoteAccessVpnDao _vpnDao;
    @Inject
    NicDao _nicDao;
    @Inject
    NicIpAliasDao _nicIpAliasDao;
    @Inject
    VolumeDao _volumeDao = null;
    @Inject
    UserVmDetailsDao _vmDetailsDao;
    @Inject
    ClusterDao _clusterDao;
    @Inject
    ResourceManager _resourceMgr;
    @Inject
    PhysicalNetworkServiceProviderDao _physicalProviderDao;
    @Inject
    VirtualRouterProviderDao _vrProviderDao;
    @Inject
    ManagementServerHostDao _msHostDao;
    @Inject
    Site2SiteCustomerGatewayDao _s2sCustomerGatewayDao;
    @Inject
    Site2SiteVpnGatewayDao _s2sVpnGatewayDao;
    @Inject
    Site2SiteVpnConnectionDao _s2sVpnConnectionDao;
    @Inject
    Site2SiteVpnManager _s2sVpnMgr;
    @Inject
    UserIpv6AddressDao _ipv6Dao;
    @Inject
    NetworkService _networkSvc;
    @Inject
    IpAddressManager _ipAddrMgr;
    @Inject
    ConfigDepot _configDepot;
    @Inject
    MonitoringServiceDao _monitorServiceDao;
    @Inject
    AsyncJobManager _asyncMgr;
    @Inject
    protected ApiAsyncJobDispatcher _asyncDispatcher;

    int _routerRamSize;
    int _routerCpuMHz;
    int _retry = 2;
    String _instance;
    String _mgmt_cidr;

    int _routerStatsInterval = 300;
    int _routerCheckInterval = 30;
    int _rvrStatusUpdatePoolSize = 10;
    protected ServiceOfferingVO _offering;
    private String _dnsBasicZoneUpdates = "all";
    private final Set<String> _guestOSNeedGatewayOnNonDefaultNetwork = new HashSet<String>();

    private boolean _disable_rp_filter = false;
    int _routerExtraPublicNics = 2;
    private int _usageAggregationRange = 1440;
    private String _usageTimeZone = "GMT";
    private final long mgmtSrvrId = MacAddress.getMacAddress().toLong();
    private static final int ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_COOPERATION = 5;    // 5 seconds
    private static final int USAGE_AGGREGATION_RANGE_MIN = 10; // 10 minutes, same as com.cloud.usage.UsageManagerImpl.USAGE_AGGREGATION_RANGE_MIN
    private boolean _dailyOrHourly = false;

    ScheduledExecutorService _executor;
    ScheduledExecutorService _checkExecutor;
    ScheduledExecutorService _networkStatsUpdateExecutor;
    ExecutorService _rvrStatusUpdateExecutor;

    Account _systemAcct;

    BlockingQueue<Long> _vrUpdateQueue = null;

    @Override
    public boolean sendSshKeysToHost(Long hostId, String pubKey, String prvKey) {
        ModifySshKeysCommand cmd = new ModifySshKeysCommand(pubKey, prvKey);
        final Answer answer = _agentMgr.easySend(hostId, cmd);

        if (answer != null) {
            return true;
        } else {
            return false;
        }
    }

    

    @Override
    public VirtualRouter destroyRouter(final long routerId, Account caller, Long callerUserId) throws ResourceUnavailableException, ConcurrentOperationException {

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Attempting to destroy router " + routerId);
        }

        DomainRouterVO router = _routerDao.findById(routerId);
        if (router == null) {
            return null;
        }

        _accountMgr.checkAccess(caller, null, true, router);

        _itMgr.expunge(router.getUuid());
        _routerDao.remove(router.getId());
        return router;
    }

    @Override
    @DB
    public VirtualRouter upgradeRouter(UpgradeRouterCmd cmd) {
        Long routerId = cmd.getId();
        Long serviceOfferingId = cmd.getServiceOfferingId();
        Account caller = CallContext.current().getCallingAccount();

        DomainRouterVO router = _routerDao.findById(routerId);
        if (router == null) {
            throw new InvalidParameterValueException("Unable to find router with id " + routerId);
        }

        _accountMgr.checkAccess(caller, null, true, router);

        if (router.getServiceOfferingId() == serviceOfferingId) {
            s_logger.debug("Router: " + routerId + "already has service offering: " + serviceOfferingId);
            return _routerDao.findById(routerId);
        }

        ServiceOffering newServiceOffering = _entityMgr.findById(ServiceOffering.class, serviceOfferingId);
        if (newServiceOffering == null) {
            throw new InvalidParameterValueException("Unable to find service offering with id " + serviceOfferingId);
        }

        // check if it is a system service offering, if yes return with error as it cannot be used for user vms
        if (!newServiceOffering.getSystemUse()) {
            throw new InvalidParameterValueException("Cannot upgrade router vm to a non system service offering " + serviceOfferingId);
        }

        // Check that the router is stopped
        if (!router.getState().equals(State.Stopped)) {
            s_logger.warn("Unable to upgrade router " + router.toString() + " in state " + router.getState());
            throw new InvalidParameterValueException("Unable to upgrade router " + router.toString() + " in state " + router.getState()
                    + "; make sure the router is stopped and not in an error state before upgrading.");
        }

        ServiceOfferingVO currentServiceOffering = _serviceOfferingDao.findById(router.getServiceOfferingId());

        // Check that the service offering being upgraded to has the same storage pool preference as the VM's current service
        // offering
        if (currentServiceOffering.getUseLocalStorage() != newServiceOffering.getUseLocalStorage()) {
            throw new InvalidParameterValueException("Can't upgrade, due to new local storage status : " +
        newServiceOffering.getUseLocalStorage() + " is different from "
                    + "curruent local storage status: " + currentServiceOffering.getUseLocalStorage());
        }

        router.setServiceOfferingId(serviceOfferingId);
        if (_routerDao.update(routerId, router)) {
            return _routerDao.findById(routerId);
        } else {
            throw new CloudRuntimeException("Unable to upgrade router " + routerId);
        }

    }

    @Override
    public boolean savePasswordToRouter(Network network, final NicProfile nic, VirtualMachineProfile profile, List<? extends VirtualRouter> routers) throws ResourceUnavailableException {
        _userVmDao.loadDetails((UserVmVO) profile.getVirtualMachine());

        final VirtualMachineProfile updatedProfile = profile;

        return applyRules(network, routers, "save password entry", false, null, false, new RuleApplier() {
            @Override
            public boolean execute(Network network, VirtualRouter router) throws ResourceUnavailableException {
                // for basic zone, send vm data/password information only to the router in the same pod
                Commands cmds = new Commands(Command.OnError.Stop);
                NicVO nicVo = _nicDao.findById(nic.getId());
                createPasswordCommand(router, updatedProfile, nicVo, cmds);
                return sendCommandsToRouter(router, cmds);
            }
        });
    }

    @Override
    public boolean saveSSHPublicKeyToRouter(Network network, final NicProfile nic, VirtualMachineProfile profile, List<? extends VirtualRouter> routers, final String SSHPublicKey) throws ResourceUnavailableException {
        final UserVmVO vm = _userVmDao.findById(profile.getVirtualMachine().getId());
        _userVmDao.loadDetails(vm);

        final VirtualMachineProfile updatedProfile = profile;

        return applyRules(network, routers, "save SSHkey entry", false, null, false, new RuleApplier() {
            @Override
            public boolean execute(Network network, VirtualRouter router) throws ResourceUnavailableException {
                // for basic zone, send vm data/password information only to the router in the same pod
                Commands cmds = new Commands(Command.OnError.Stop);
                NicVO nicVo = _nicDao.findById(nic.getId());
                VMTemplateVO template = _templateDao.findByIdIncludingRemoved(updatedProfile.getTemplateId());
                if(template != null && template.getEnablePassword()) {
			createPasswordCommand(router, updatedProfile, nicVo, cmds);
                }
                createVmDataCommand(router, vm, nicVo, SSHPublicKey, cmds);
                return sendCommandsToRouter(router, cmds);
            }
        });
    }

    @Override
    public boolean saveUserDataToRouter(Network network, final NicProfile nic, VirtualMachineProfile profile, List<? extends VirtualRouter> routers) throws ResourceUnavailableException {
        final UserVmVO vm = _userVmDao.findById(profile.getVirtualMachine().getId());
        _userVmDao.loadDetails(vm);

        return applyRules(network, routers, "save userdata entry", false, null, false, new RuleApplier() {
            @Override
            public boolean execute(Network network, VirtualRouter router) throws ResourceUnavailableException {
                // for basic zone, send vm data/password information only to the router in the same pod
                Commands cmds = new Commands(Command.OnError.Stop);
                NicVO nicVo = _nicDao.findById(nic.getId());
                createVmDataCommand(router, vm, nicVo, null, cmds);
                return sendCommandsToRouter(router, cmds);
            }
        });
    }

    @Override @ActionEvent(eventType = EventTypes.EVENT_ROUTER_STOP, eventDescription = "stopping router Vm", async = true)
    public VirtualRouter stopRouter(long routerId, boolean forced) throws ResourceUnavailableException, ConcurrentOperationException {
        CallContext context = CallContext.current();
        Account account = context.getCallingAccount();

        // verify parameters
        DomainRouterVO router = _routerDao.findById(routerId);
        if (router == null) {
            throw new InvalidParameterValueException("Unable to find router by id " + routerId + ".");
        }

        _accountMgr.checkAccess(account, null, true, router);

        UserVO user = _userDao.findById(CallContext.current().getCallingUserId());

        VirtualRouter virtualRouter = stop(router, forced, user, account);
        if(virtualRouter == null){
            throw new CloudRuntimeException("Failed to stop router with id " + routerId);
        }
        
        // Clear stop pending flag after stopped successfully
        if (router.isStopPending()) {
            s_logger.info("Clear the stop pending flag of router " + router.getHostName() + " after stop router successfully");
            router.setStopPending(false);
            router = _routerDao.persist(router);
            virtualRouter.setStopPending(false);
        }
        return virtualRouter;
    }

    @DB
    public void processStopOrRebootAnswer(final DomainRouterVO router, Answer answer) {
        Transaction.execute(new TransactionCallbackNoReturn() {
            @Override
            public void doInTransactionWithoutResult(TransactionStatus status) {
                //FIXME!!! - UserStats command should grab bytesSent/Received for all guest interfaces of the VR
                List<Long> routerGuestNtwkIds = _routerDao.getRouterNetworks(router.getId());
                for (Long guestNtwkId : routerGuestNtwkIds) {
                    final UserStatisticsVO userStats = _userStatsDao.lock(router.getAccountId(), router.getDataCenterId(),
                            guestNtwkId, null, router.getId(), router.getType().toString());
                    if (userStats != null) {
                        final long currentBytesRcvd = userStats.getCurrentBytesReceived();
                        userStats.setCurrentBytesReceived(0);
                        userStats.setNetBytesReceived(userStats.getNetBytesReceived() + currentBytesRcvd);
        
                        final long currentBytesSent = userStats.getCurrentBytesSent();
                        userStats.setCurrentBytesSent(0);
                        userStats.setNetBytesSent(userStats.getNetBytesSent() + currentBytesSent);
                        _userStatsDao.update(userStats.getId(), userStats);
                        s_logger.debug("Successfully updated user statistics as a part of domR " + router + " reboot/stop");
                    } else {
                        s_logger.warn("User stats were not created for account " + router.getAccountId() + " and dc " + router.getDataCenterId());
                    }
                }
            }
        });
    }

    @Override @ActionEvent(eventType = EventTypes.EVENT_ROUTER_REBOOT, eventDescription = "rebooting router Vm", async = true)
    public VirtualRouter rebootRouter(long routerId, boolean reprogramNetwork) throws ConcurrentOperationException,
    ResourceUnavailableException, InsufficientCapacityException {
        Account caller = CallContext.current().getCallingAccount();

        // verify parameters
        DomainRouterVO router = _routerDao.findById(routerId);
        if (router == null) {
            throw new InvalidParameterValueException("Unable to find domain router with id " + routerId + ".");
        }

        _accountMgr.checkAccess(caller, null, true, router);

        // Can reboot domain router only in Running state
        if (router == null || router.getState() != State.Running) {
            s_logger.warn("Unable to reboot, virtual router is not in the right state " + router.getState());
            throw new ResourceUnavailableException("Unable to reboot domR, it is not in right state " + router.getState(),
                    DataCenter.class, router.getDataCenterId());
        }

        UserVO user = _userDao.findById(CallContext.current().getCallingUserId());
        s_logger.debug("Stopping and starting router " + router + " as a part of router reboot");

        if (stop(router, false, user, caller) != null) {
            return startRouter(routerId, reprogramNetwork);
        } else {
            throw new CloudRuntimeException("Failed to reboot router " + router);
        }
    }
    static final ConfigKey<Boolean> UseExternalDnsServers = new ConfigKey<Boolean>(Boolean.class, "use.external.dns", "Advanced", "false",
        "Bypass internal dns, use external dns1 and dns2", true, ConfigKey.Scope.Zone, null);

    static final ConfigKey<Boolean> routerVersionCheckEnabled = new ConfigKey<Boolean>("Advanced", Boolean.class, "router.version.check", "true",
            "If true, router minimum required version is checked before sending command", false);

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {

        _executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("RouterMonitor"));
        _checkExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("RouterStatusMonitor"));
        _networkStatsUpdateExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("NetworkStatsUpdater"));

        final Map<String, String> configs = _configDao.getConfiguration("AgentManager", params);

        _routerRamSize = NumbersUtil.parseInt(configs.get("router.ram.size"), DEFAULT_ROUTER_VM_RAMSIZE);
        _routerCpuMHz = NumbersUtil.parseInt(configs.get("router.cpu.mhz"), DEFAULT_ROUTER_CPU_MHZ);

        _routerExtraPublicNics = NumbersUtil.parseInt(_configDao.getValue(Config.RouterExtraPublicNics.key()), 2);

        String guestOSString = configs.get("network.dhcp.nondefaultnetwork.setgateway.guestos");
        if (guestOSString != null) {
            String[] guestOSList = guestOSString.split(",");
            for (String os : guestOSList) {
                _guestOSNeedGatewayOnNonDefaultNetwork.add(os);
            }
        }
        
        String value = configs.get("start.retry");
        _retry = NumbersUtil.parseInt(value, 2);

        value = configs.get("router.stats.interval");
        _routerStatsInterval = NumbersUtil.parseInt(value, 300);

        value = configs.get("router.check.interval");
        _routerCheckInterval = NumbersUtil.parseInt(value, 30);
        
        value = configs.get("router.check.poolsize");
        _rvrStatusUpdatePoolSize = NumbersUtil.parseInt(value, 10);

        /*
         * We assume that one thread can handle 20 requests in 1 minute in normal situation, so here we give the queue size up to 50 minutes.
         * It's mostly for buffer, since each time CheckRouterTask running, it would add all the redundant networks in the queue immediately
         */
        _vrUpdateQueue = new LinkedBlockingQueue<Long>(_rvrStatusUpdatePoolSize * 1000);

        _rvrStatusUpdateExecutor = Executors.newFixedThreadPool(_rvrStatusUpdatePoolSize, new NamedThreadFactory("RedundantRouterStatusMonitor"));

        _instance = configs.get("instance.name");
        if (_instance == null) {
            _instance = "DEFAULT";
        }

        String rpValue = configs.get("network.disable.rpfilter");
        if (rpValue != null && rpValue.equalsIgnoreCase("true")) {
            _disable_rp_filter = true;
        }

        _dnsBasicZoneUpdates = String.valueOf(_configDao.getValue(Config.DnsBasicZoneUpdates.key()));

        s_logger.info("Router configurations: " + "ramsize=" + _routerRamSize);

        _agentMgr.registerForHostEvents(new SshKeysDistriMonitor(_agentMgr, _hostDao, _configDao), true, false, false);

        boolean useLocalStorage = Boolean.parseBoolean(configs.get(Config.SystemVMUseLocalStorage.key()));
        _offering = new ServiceOfferingVO("System Offering For Software Router", 1, _routerRamSize, _routerCpuMHz, null,
                null, true, null, useLocalStorage, true, null, true, VirtualMachine.Type.DomainRouter, true);
        _offering.setUniqueName(ServiceOffering.routerDefaultOffUniqueName);
        _offering = _serviceOfferingDao.persistSystemServiceOffering(_offering);

        // this can sometimes happen, if DB is manually or programmatically manipulated
        if(_offering == null) {
            String msg = "Data integrity problem : System Offering For Software router VM has been removed?";
            s_logger.error(msg);
            throw new ConfigurationException(msg);
        }

        _systemAcct = _accountMgr.getSystemAccount();

        String aggregationRange = configs.get("usage.stats.job.aggregation.range");
        _usageAggregationRange  = NumbersUtil.parseInt(aggregationRange, 1440);
        _usageTimeZone = configs.get("usage.aggregation.timezone");
        if(_usageTimeZone == null){
            _usageTimeZone = "GMT";
        }

        _agentMgr.registerForHostEvents(this, true, false, false);

        s_logger.info("DomainRouterManager is configured.");

        return true;
    }

    @Override
    public boolean start() {
        if (_routerStatsInterval > 0){
            _executor.scheduleAtFixedRate(new NetworkUsageTask(), _routerStatsInterval, _routerStatsInterval, TimeUnit.SECONDS);
        }else{
            s_logger.debug("router.stats.interval - " + _routerStatsInterval+ " so not scheduling the router stats thread");
        }

        //Schedule Network stats update task
        TimeZone usageTimezone = TimeZone.getTimeZone(_usageTimeZone);
        Calendar cal = Calendar.getInstance(usageTimezone);
        cal.setTime(new Date());
        long endDate = 0;
        int HOURLY_TIME = 60;
        final int DAILY_TIME = 60 * 24;
        if (_usageAggregationRange == DAILY_TIME) {
            cal.roll(Calendar.DAY_OF_YEAR, false);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.roll(Calendar.DAY_OF_YEAR, true);
            cal.add(Calendar.MILLISECOND, -1);
            endDate = cal.getTime().getTime();
            _dailyOrHourly = true;
        } else if (_usageAggregationRange == HOURLY_TIME) {
            cal.roll(Calendar.HOUR_OF_DAY, false);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.roll(Calendar.HOUR_OF_DAY, true);
            cal.add(Calendar.MILLISECOND, -1);
            endDate = cal.getTime().getTime();
            _dailyOrHourly = true;
        } else {
            endDate = cal.getTime().getTime();
            _dailyOrHourly = false;
        }

        if (_usageAggregationRange < USAGE_AGGREGATION_RANGE_MIN) {
            s_logger.warn("Usage stats job aggregation range is to small, using the minimum value of " + USAGE_AGGREGATION_RANGE_MIN);
            _usageAggregationRange = USAGE_AGGREGATION_RANGE_MIN;
        }

        _networkStatsUpdateExecutor.scheduleAtFixedRate(new NetworkStatsUpdateTask(), (endDate - System.currentTimeMillis()),
                (_usageAggregationRange * 60 * 1000), TimeUnit.MILLISECONDS);
        
        if (_routerCheckInterval > 0) {
            _checkExecutor.scheduleAtFixedRate(new CheckRouterTask(), _routerCheckInterval, _routerCheckInterval, TimeUnit.SECONDS);
            for (int i = 0; i < _rvrStatusUpdatePoolSize; i++) {
                _rvrStatusUpdateExecutor.execute(new RvRStatusUpdateTask());
            }
        } else {
            s_logger.debug("router.check.interval - " + _routerCheckInterval+ " so not scheduling the redundant router checking thread");
        }

        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    protected VirtualNetworkApplianceManagerImpl() {
    }

    private VmDataCommand generateVmDataCommand(VirtualRouter router, String vmPrivateIpAddress, String userData,
            String serviceOffering, String zoneName, String guestIpAddress, String vmName,
            String vmInstanceName, long vmId, String vmUuid, String publicKey, long guestNetworkId) {
        VmDataCommand cmd = new VmDataCommand(vmPrivateIpAddress, vmName, _networkModel.getExecuteInSeqNtwkElmtCmd());

        cmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, getRouterIpInNetwork(guestNetworkId, router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());

        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        cmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());

        cmd.addVmData("userdata", "user-data", userData);
        cmd.addVmData("metadata", "service-offering", StringUtils.unicodeEscape(serviceOffering));
        cmd.addVmData("metadata", "availability-zone", StringUtils.unicodeEscape(zoneName));
        cmd.addVmData("metadata", "local-ipv4", guestIpAddress);
        cmd.addVmData("metadata", "local-hostname", StringUtils.unicodeEscape(vmName));
        if (dcVo.getNetworkType() == NetworkType.Basic) {
            cmd.addVmData("metadata", "public-ipv4", guestIpAddress);
            cmd.addVmData("metadata", "public-hostname",  StringUtils.unicodeEscape(vmName));
        } else{
            if (router.getPublicIpAddress() == null) {
                cmd.addVmData("metadata", "public-ipv4", guestIpAddress);
            } else {
                cmd.addVmData("metadata", "public-ipv4", router.getPublicIpAddress());
            }
            cmd.addVmData("metadata", "public-hostname", router.getPublicIpAddress());
        }
        if (vmUuid == null) {
            setVmInstanceId(vmInstanceName, vmId, cmd);
        } else {
            setVmInstanceId(vmUuid, cmd);
        }
        cmd.addVmData("metadata", "public-keys", publicKey);

        String cloudIdentifier = _configDao.getValue("cloud.identifier");
        if (cloudIdentifier == null) {
            cloudIdentifier = "";
        } else {
            cloudIdentifier = "CloudStack-{" + cloudIdentifier + "}";
        }
        cmd.addVmData("metadata", "cloud-identifier", cloudIdentifier);

        return cmd;
    }

        private void setVmInstanceId(String vmUuid, VmDataCommand cmd) {
            cmd.addVmData("metadata", "instance-id", vmUuid);
            cmd.addVmData("metadata", "vm-id", vmUuid);
        }

        private void setVmInstanceId(String vmInstanceName, long vmId,VmDataCommand cmd) {
            cmd.addVmData("metadata", "instance-id", vmInstanceName);
            cmd.addVmData("metadata", "vm-id", String.valueOf(vmId));
        }


    protected class NetworkUsageTask extends ManagedContextRunnable {

        public NetworkUsageTask() {
        }

        @Override
        protected void runInContext() {
            try{
                final List<DomainRouterVO> routers = _routerDao.listByStateAndNetworkType(State.Running, GuestType.Isolated, mgmtSrvrId);
                s_logger.debug("Found " + routers.size() + " running routers. ");

                for (final DomainRouterVO router : routers) {
                    String privateIP = router.getPrivateIpAddress();

                    if (privateIP != null) {
                        final boolean forVpc = router.getVpcId() != null;
                        List<? extends Nic> routerNics = _nicDao.listByVmId(router.getId());
                        for (final Nic routerNic : routerNics) {
                            final Network network = _networkModel.getNetwork(routerNic.getNetworkId());
                            //Send network usage command for public nic in VPC VR
                            //Send network usage command for isolated guest nic of non VPC VR
                            if ((forVpc && network.getTrafficType() == TrafficType.Public) || (!forVpc && network.getTrafficType() == TrafficType.Guest && network.getGuestType() == Network.GuestType.Isolated)) {
                                final NetworkUsageCommand usageCmd = new NetworkUsageCommand(privateIP, router.getHostName(),
                                        forVpc, routerNic.getIp4Address());
                                final String routerType = router.getType().toString();
                                final UserStatisticsVO previousStats = _userStatsDao.findBy(router.getAccountId(),
                                        router.getDataCenterId(), network.getId(), (forVpc ? routerNic.getIp4Address() : null), router.getId(), routerType);
                                NetworkUsageAnswer answer = null;
                                try {
                                    answer = (NetworkUsageAnswer) _agentMgr.easySend(router.getHostId(), usageCmd);
                                } catch (Exception e) {
                                    s_logger.warn("Error while collecting network stats from router: " + router.getInstanceName() + " from host: " + router.getHostId(), e);
                                    continue;
                                }

                                if (answer != null) {
                                    if (!answer.getResult()) {
                                        s_logger.warn("Error while collecting network stats from router: " + router.getInstanceName() + " from host: " + router.getHostId() + "; details: " + answer.getDetails());
                                        continue;
                                    }
                                    try {
                                        if ((answer.getBytesReceived() == 0) && (answer.getBytesSent() == 0)) {
                                            s_logger.debug("Recieved and Sent bytes are both 0. Not updating user_statistics");
                                            continue;
                                        }
                                        final NetworkUsageAnswer answerFinal = answer;
                                        Transaction.execute(new TransactionCallbackNoReturn() {
                                            @Override
                                            public void doInTransactionWithoutResult(TransactionStatus status) {
                                                UserStatisticsVO stats = _userStatsDao.lock(router.getAccountId(),
                                                        router.getDataCenterId(), network.getId(), (forVpc ? routerNic.getIp4Address() : null), router.getId(), routerType);
                                                if (stats == null) {
                                                    s_logger.warn("unable to find stats for account: " + router.getAccountId());
                                                    return;
                                                }
        
                                                if (previousStats != null
                                                        && ((previousStats.getCurrentBytesReceived() != stats.getCurrentBytesReceived())
                                                        || (previousStats.getCurrentBytesSent() != stats.getCurrentBytesSent()))) {
                                                    s_logger.debug("Router stats changed from the time NetworkUsageCommand was sent. " +
                                                            "Ignoring current answer. Router: " + answerFinal.getRouterName() + " Rcvd: " +
                                                            answerFinal.getBytesReceived() + "Sent: " + answerFinal.getBytesSent());
                                                    return;
                                                }
        
                                                if (stats.getCurrentBytesReceived() > answerFinal.getBytesReceived()) {
                                                    if (s_logger.isDebugEnabled()) {
                                                        s_logger.debug("Received # of bytes that's less than the last one.  " +
                                                                "Assuming something went wrong and persisting it. Router: " +
                                                                answerFinal.getRouterName() + " Reported: " + answerFinal.getBytesReceived()
                                                                + " Stored: " + stats.getCurrentBytesReceived());
                                                    }
                                                    stats.setNetBytesReceived(stats.getNetBytesReceived() + stats.getCurrentBytesReceived());
                                                }
                                                stats.setCurrentBytesReceived(answerFinal.getBytesReceived());
                                                if (stats.getCurrentBytesSent() > answerFinal.getBytesSent()) {
                                                    if (s_logger.isDebugEnabled()) {
                                                        s_logger.debug("Received # of bytes that's less than the last one.  " +
                                                                "Assuming something went wrong and persisting it. Router: " +
                                                                answerFinal.getRouterName() + " Reported: " + answerFinal.getBytesSent()
                                                                + " Stored: " + stats.getCurrentBytesSent());
                                                    }
                                                    stats.setNetBytesSent(stats.getNetBytesSent() + stats.getCurrentBytesSent());
                                                }
                                                stats.setCurrentBytesSent(answerFinal.getBytesSent());
                                                if (! _dailyOrHourly) {
                                                    //update agg bytes
                                                    stats.setAggBytesSent(stats.getNetBytesSent() + stats.getCurrentBytesSent());
                                                    stats.setAggBytesReceived(stats.getNetBytesReceived() + stats.getCurrentBytesReceived());
                                                }
                                                _userStatsDao.update(stats.getId(), stats);
                                            }
                                        });

                                    } catch (Exception e) {
                                        s_logger.warn("Unable to update user statistics for account: " + router.getAccountId()
                                                + " Rx: " + answer.getBytesReceived() + "; Tx: " + answer.getBytesSent());
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                s_logger.warn("Error while collecting network stats", e);
            }
        }
    }

    protected class NetworkStatsUpdateTask extends ManagedContextRunnable {

        public NetworkStatsUpdateTask() {
        }

        @Override
        protected void runInContext() {
            GlobalLock scanLock = GlobalLock.getInternLock("network.stats");
            try {
                if(scanLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_COOPERATION)) {
                    //Check for ownership
                    //msHost in UP state with min id should run the job
                    ManagementServerHostVO msHost = _msHostDao.findOneInUpState(new Filter(ManagementServerHostVO.class, "id", true, 0L, 1L));
                    if(msHost == null || (msHost.getMsid() != mgmtSrvrId)){
                        s_logger.debug("Skipping aggregate network stats update");
                        scanLock.unlock();
                        return;
                    }
                    try {
                        Transaction.execute(new TransactionCallbackNoReturn() {
                            @Override
                            public void doInTransactionWithoutResult(TransactionStatus status) {
                                //get all stats with delta > 0
                                List<UserStatisticsVO> updatedStats = _userStatsDao.listUpdatedStats();
                                Date updatedTime = new Date();
                                for(UserStatisticsVO stat : updatedStats){
                                    //update agg bytes
                                    stat.setAggBytesReceived(stat.getCurrentBytesReceived() + stat.getNetBytesReceived());
                                    stat.setAggBytesSent(stat.getCurrentBytesSent() + stat.getNetBytesSent());
                                    _userStatsDao.update(stat.getId(), stat);
                                    //insert into op_user_stats_log
                                    UserStatsLogVO statsLog = new UserStatsLogVO(stat.getId(), stat.getNetBytesReceived(), stat.getNetBytesSent(), stat.getCurrentBytesReceived(),
                                                                                 stat.getCurrentBytesSent(), stat.getAggBytesReceived(), stat.getAggBytesSent(), updatedTime);
                                    _userStatsLogDao.persist(statsLog);
                                }
                                s_logger.debug("Successfully updated aggregate network stats");
                            }
                        });
                    } catch (Exception e){
                        s_logger.debug("Failed to update aggregate network stats", e);
                    } finally {
                        scanLock.unlock();
                    }
                }
            } catch (Exception e){
                s_logger.debug("Exception while trying to acquire network stats lock", e);
            }  finally {
                scanLock.releaseRef();
            }
        }
    }

    @DB
    protected void updateSite2SiteVpnConnectionState(List<DomainRouterVO> routers) {
        for (DomainRouterVO router : routers) {
            List<Site2SiteVpnConnectionVO> conns = _s2sVpnMgr.getConnectionsForRouter(router);
            if (conns == null || conns.isEmpty()) {
                continue;
            }
            if (router.getState() != State.Running) {
                for (Site2SiteVpnConnectionVO conn : conns) {
                    if (conn.getState() != Site2SiteVpnConnection.State.Error) {
                        conn.setState(Site2SiteVpnConnection.State.Disconnected);
                        _s2sVpnConnectionDao.persist(conn);
                    }
                }
                continue;
            }
            List<String> ipList = new ArrayList<String>();
            for (Site2SiteVpnConnectionVO conn : conns) {
                if (conn.getState() != Site2SiteVpnConnection.State.Connected &&
                        conn.getState() != Site2SiteVpnConnection.State.Disconnected) {
                    continue;
                }
                Site2SiteCustomerGateway gw = _s2sCustomerGatewayDao.findById(conn.getCustomerGatewayId());
                ipList.add(gw.getGatewayIp());
            }
            String privateIP = router.getPrivateIpAddress();
            HostVO host = _hostDao.findById(router.getHostId());
            if (host == null || host.getState() != Status.Up) {
                continue;
            } else if (host.getManagementServerId() != ManagementServerNode.getManagementServerId()) {
                /* Only cover hosts managed by this management server */
                continue;
            } else if (privateIP != null) {
                final CheckS2SVpnConnectionsCommand command = new CheckS2SVpnConnectionsCommand(ipList);
                command.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
                command.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
                command.setWait(30);
                final Answer origAnswer = _agentMgr.easySend(router.getHostId(), command);
                CheckS2SVpnConnectionsAnswer answer = null;
                if (origAnswer instanceof CheckS2SVpnConnectionsAnswer) {
                    answer = (CheckS2SVpnConnectionsAnswer)origAnswer;
                } else {
                    s_logger.warn("Unable to update router " + router.getHostName() + "'s VPN connection status");
                    continue;
                }
                if (!answer.getResult()) {
                    s_logger.warn("Unable to update router " + router.getHostName() + "'s VPN connection status");
                    continue;
                }
                for (Site2SiteVpnConnectionVO conn : conns) {
                    Site2SiteVpnConnectionVO lock = _s2sVpnConnectionDao.acquireInLockTable(conn.getId());
                    if (lock == null) {
                        throw new CloudRuntimeException("Unable to acquire lock on " + lock);
                    }
                    try {
                        if (conn.getState() != Site2SiteVpnConnection.State.Connected &&
                                conn.getState() != Site2SiteVpnConnection.State.Disconnected) {
                            continue;
                        }
                        Site2SiteVpnConnection.State oldState = conn.getState();
                        Site2SiteCustomerGateway gw = _s2sCustomerGatewayDao.findById(conn.getCustomerGatewayId());
                        if (answer.isConnected(gw.getGatewayIp())) {
                            conn.setState(Site2SiteVpnConnection.State.Connected);
                        } else {
                            conn.setState(Site2SiteVpnConnection.State.Disconnected);
                        }
                        _s2sVpnConnectionDao.persist(conn);
                        if (oldState != conn.getState()) {
                            String title = "Site-to-site Vpn Connection to " + gw.getName() +
                                    " just switch from " + oldState + " to " + conn.getState();
                            String context = "Site-to-site Vpn Connection to " + gw.getName() + " on router " + router.getHostName() +
                                    "(id: " + router.getId() + ") " + " just switch from " + oldState + " to " + conn.getState();
                            s_logger.info(context);
                            _alertMgr.sendAlert(AlertManager.AlertType.ALERT_TYPE_DOMAIN_ROUTER,
                                    router.getDataCenterId(), router.getPodIdToDeployIn(), title, context);
                        }
                    } finally {
                        _s2sVpnConnectionDao.releaseFromLockTable(lock.getId());
                    }
                }
            }
        }
    }
    
    protected void updateRoutersRedundantState(List<DomainRouterVO> routers) {
        boolean updated = false;
        for (DomainRouterVO router : routers) {
            updated = false;
            if (!router.getIsRedundantRouter()) {
                continue;
            }
            RedundantState prevState = router.getRedundantState();
            if (router.getState() != State.Running) {
                router.setRedundantState(RedundantState.UNKNOWN);
                router.setIsPriorityBumpUp(false);
                updated = true;
            } else {
                String privateIP = router.getPrivateIpAddress();
                HostVO host = _hostDao.findById(router.getHostId());
                if (host == null || host.getState() != Status.Up) {
                    router.setRedundantState(RedundantState.UNKNOWN);
                    updated = true;
                } else if (privateIP != null) {
                    final CheckRouterCommand command = new CheckRouterCommand();
                    command.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
                    command.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
                    command.setWait(30);
                    final Answer origAnswer = _agentMgr.easySend(router.getHostId(), command);
                    CheckRouterAnswer answer = null;
                    if (origAnswer instanceof CheckRouterAnswer) {
                        answer = (CheckRouterAnswer)origAnswer;
                    } else {
                        s_logger.warn("Unable to update router " + router.getHostName() + "'s status");
                    }
                    RedundantState state = RedundantState.UNKNOWN;
                    boolean isBumped = router.getIsPriorityBumpUp();
                    if (answer != null && answer.getResult()) {
                        state = answer.getState();
                        isBumped = answer.isBumped();
                    }
                    router.setRedundantState(state);
                    router.setIsPriorityBumpUp(isBumped);
                    updated = true;
                }
            }
            if (updated) {
                _routerDao.update(router.getId(), router);
            }
            RedundantState currState = router.getRedundantState();
            if (prevState != currState) {
                String title = "Redundant virtual router " + router.getInstanceName() +
                        " just switch from " + prevState + " to " + currState;
                String context =  "Redundant virtual router (name: " + router.getHostName() + ", id: " + router.getId() + ") " +
                        " just switch from " + prevState + " to " + currState;
                s_logger.info(context);
                if (currState == RedundantState.MASTER) {
                    _alertMgr.sendAlert(AlertManager.AlertType.ALERT_TYPE_DOMAIN_ROUTER,
                            router.getDataCenterId(), router.getPodIdToDeployIn(), title, context);
                }
            }
        }
    }

    //Ensure router status is update to date before execute this function. The function would try best to recover all routers except MASTER
    protected void recoverRedundantNetwork(DomainRouterVO masterRouter, DomainRouterVO backupRouter) {
        if (masterRouter.getState() == State.Running && backupRouter.getState() == State.Running) {
            HostVO masterHost = _hostDao.findById(masterRouter.getHostId());
            HostVO backupHost = _hostDao.findById(backupRouter.getHostId());
            if (masterHost.getState() == Status.Up && backupHost.getState() == Status.Up) {
                String title =  "Reboot " + backupRouter.getInstanceName() + " to ensure redundant virtual routers work";
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug(title);
                }
                _alertMgr.sendAlert(AlertManager.AlertType.ALERT_TYPE_DOMAIN_ROUTER,
                        backupRouter.getDataCenterId(), backupRouter.getPodIdToDeployIn(), title, title);
                try {
                    rebootRouter(backupRouter.getId(), true);
                } catch (ConcurrentOperationException e) {
                    s_logger.warn("Fail to reboot " + backupRouter.getInstanceName(), e);
                } catch (ResourceUnavailableException e) {
                    s_logger.warn("Fail to reboot " + backupRouter.getInstanceName(), e);
                } catch (InsufficientCapacityException e) {
                    s_logger.warn("Fail to reboot " + backupRouter.getInstanceName(), e);
                }
            }
        }
    }

    private int getRealPriority(DomainRouterVO router) {
        int priority = router.getPriority();
        if (router.getIsPriorityBumpUp()) {
            priority += DEFAULT_DELTA;
        }
        return priority;
    }

    protected class RvRStatusUpdateTask extends ManagedContextRunnable {

        public RvRStatusUpdateTask() {
        }

        /*
         * In order to make fail-over works well at any time, we have to ensure:
         * 1. Backup router's priority = Master's priority - DELTA + 1
         * 2. Backup router's priority hasn't been bumped up.
         */
        private void checkSanity(List<DomainRouterVO> routers) {
            Set<Long> checkedNetwork = new HashSet<Long>();
            for (DomainRouterVO router : routers) {
                if (!router.getIsRedundantRouter()) {
                    continue;
                }
                
                List<Long> routerGuestNtwkIds = _routerDao.getRouterNetworks(router.getId());
                
                for (Long routerGuestNtwkId : routerGuestNtwkIds) {
                    if (checkedNetwork.contains(routerGuestNtwkId)) {
                    continue;
                }
                    checkedNetwork.add(routerGuestNtwkId);
                    List<DomainRouterVO> checkingRouters = _routerDao.listByNetworkAndRole(routerGuestNtwkId, Role.VIRTUAL_ROUTER);
                if (checkingRouters.size() != 2) {
                    continue;
                }
                DomainRouterVO masterRouter = null;
                DomainRouterVO backupRouter = null;
                for (DomainRouterVO r : checkingRouters) {
                    if (r.getRedundantState() == RedundantState.MASTER) {
                        if (masterRouter == null) {
                            masterRouter = r;
                        } else {
                            //Duplicate master! We give up, until the admin fix duplicate MASTER issue
                            break;
                        }
                    } else if (r.getRedundantState() == RedundantState.BACKUP) {
                        if (backupRouter == null) {
                            backupRouter = r;
                        } else {
                            break;
                        }
                    }
                }
                if (masterRouter != null && backupRouter != null) {
                    if (getRealPriority(masterRouter) - DEFAULT_DELTA + 1 != getRealPriority(backupRouter) || backupRouter.getIsPriorityBumpUp()) {
                        recoverRedundantNetwork(masterRouter, backupRouter);
                    }
                }
            }
        }
        }

        private void checkDuplicateMaster(List <DomainRouterVO> routers) {
            Map<Long, DomainRouterVO> networkRouterMaps = new HashMap<Long, DomainRouterVO>();
            for (DomainRouterVO router : routers) {
                List<Long> routerGuestNtwkIds = _routerDao.getRouterNetworks(router.getId());
                 
                for (Long routerGuestNtwkId : routerGuestNtwkIds) {
                if (router.getRedundantState() == RedundantState.MASTER) {
                        if (networkRouterMaps.containsKey(routerGuestNtwkId)) {
                            DomainRouterVO dupRouter = networkRouterMaps.get(routerGuestNtwkId);
                        String title = "More than one redundant virtual router is in MASTER state! Router " + router.getHostName() + " and router " + dupRouter.getHostName();
                        String context =  "Virtual router (name: " + router.getHostName() + ", id: " + router.getId() + " and router (name: "
                                + dupRouter.getHostName() + ", id: " + router.getId() + ") are both in MASTER state! If the problem persist, restart both of routers. ";
                        _alertMgr.sendAlert(AlertManager.AlertType.ALERT_TYPE_DOMAIN_ROUTER, router.getDataCenterId(), router.getPodIdToDeployIn(), title, context);
                        _alertMgr.sendAlert(AlertManager.AlertType.ALERT_TYPE_DOMAIN_ROUTER, dupRouter.getDataCenterId(), dupRouter.getPodIdToDeployIn(), title, context);
                        s_logger.warn(context);
                    } else {
                            networkRouterMaps.put(routerGuestNtwkId, router);
                        }
                    }
                }
            }
        }

        @Override
        protected void runInContext() {
            while (true) {
                try {
                        Long networkId = _vrUpdateQueue.take();  // This is a blocking call so this thread won't run all the time if no work item in queue.
                        List <DomainRouterVO> routers = _routerDao.listByNetworkAndRole(networkId, Role.VIRTUAL_ROUTER);
    
                        if (routers.size() != 2) {
                            continue;
                        }
                        /*
                         * We update the router pair which the lower id router owned by this mgmt server, in order
                         * to prevent duplicate update of router status from cluster mgmt servers
                         */
                        DomainRouterVO router0 = routers.get(0);
                        DomainRouterVO router1 = routers.get(1);
                        DomainRouterVO router = router0;
                        if ((router0.getId() < router1.getId()) && router0.getHostId() != null) {
                        	router = router0;
                        } else {
                        	router = router1;
                        }
                        if (router.getHostId() == null) {
                        	s_logger.debug("Skip router pair (" + router0.getInstanceName() + "," + router1.getInstanceName() + ") due to can't find host");
                        	continue;
                        }
                        HostVO host = _hostDao.findById(router.getHostId());
                        if (host == null || host.getManagementServerId() == null ||
                                host.getManagementServerId() != ManagementServerNode.getManagementServerId()) {
                        	s_logger.debug("Skip router pair (" + router0.getInstanceName() + "," + router1.getInstanceName() + ") due to not belong to this mgmt server");
                            continue;
                        }
                    updateRoutersRedundantState(routers);
                    checkDuplicateMaster(routers);
                    checkSanity(routers);
                } catch (Exception ex) {
                    s_logger.error("Fail to complete the RvRStatusUpdateTask! ", ex);
                }
            }
        }
    }
    
    protected class CheckRouterTask extends ManagedContextRunnable {

        public CheckRouterTask() {
        }

        @Override
        protected void runInContext() {
            try {
                final List<DomainRouterVO> routers = _routerDao.listIsolatedByHostId(null);
                s_logger.debug("Found " + routers.size() + " routers to update status. ");

                updateSite2SiteVpnConnectionState(routers);

                final List<NetworkVO> networks = _networkDao.listRedundantNetworks();
                s_logger.debug("Found " + networks.size() + " networks to update RvR status. ");
                for (NetworkVO network : networks) {
                    if (!_vrUpdateQueue.offer(network.getId(), 500, TimeUnit.MILLISECONDS)) {
                        s_logger.warn("Cannot insert into virtual router update queue! Adjustment of router.check.interval and router.check.poolsize maybe needed.");
                        break;
                    }
                }
            } catch (Exception ex) {
                s_logger.error("Fail to complete the CheckRouterTask! ", ex);
            }
        }
    }


    private final int DEFAULT_PRIORITY = 100;
    private final int DEFAULT_DELTA = 2;

    protected int getUpdatedPriority(Network guestNetwork, List<DomainRouterVO> routers, DomainRouterVO exclude) throws InsufficientVirtualNetworkCapcityException {
        int priority;
        if (routers.size() == 0) {
            priority = DEFAULT_PRIORITY;
        } else {
            int maxPriority = 0;
            for (DomainRouterVO r : routers) {
                if (!r.getIsRedundantRouter()) {
                    throw new CloudRuntimeException("Redundant router is mixed with single router in one network!");
                }
                //FIXME Assume the maxPriority one should be running or just created.
                if (r.getId() != exclude.getId() && getRealPriority(r) > maxPriority) {
                    maxPriority = getRealPriority(r);
                }
            }
            if (maxPriority == 0) {
                return DEFAULT_PRIORITY;
            }
            if (maxPriority < 20) {
                s_logger.error("Current maximum priority is too low!");
                throw new InsufficientVirtualNetworkCapcityException("Current maximum priority is too low as " + maxPriority + "!",
                        guestNetwork.getId());
            } else if (maxPriority > 200) {
                s_logger.error("Too many times fail-over happened! Current maximum priority is too high as " + maxPriority + "!");
                throw new InsufficientVirtualNetworkCapcityException("Too many times fail-over happened! Current maximum priority is too high as "
                        + maxPriority + "!", guestNetwork.getId());
            }
            priority = maxPriority - DEFAULT_DELTA + 1;
        }
        return priority;
    }

    /*
     * Ovm won't support any system. So we have to choose a partner cluster in the same pod to start domain router for us
     */
    private HypervisorType getClusterToStartDomainRouterForOvm(long podId) {
        List<ClusterVO> clusters = _clusterDao.listByPodId(podId);
        for (ClusterVO cv : clusters) {
            if (cv.getHypervisorType() == HypervisorType.Ovm || cv.getHypervisorType() == HypervisorType.BareMetal) {
                continue;
            }

            List<HostVO> hosts = _resourceMgr.listAllHostsInCluster(cv.getId());
            if (hosts == null || hosts.isEmpty()) {
                continue;
            }

            for (HostVO h : hosts) {
                if (h.getState() == Status.Up) {
                    s_logger.debug("Pick up host that has hypervisor type " + h.getHypervisorType() + " in cluster " +
                                cv.getId() + " to start domain router for OVM");
                    return h.getHypervisorType();
                }
            }
        }

        String errMsg = "Cannot find an available cluster in Pod "
                + podId
                + " to start domain router for Ovm. \n Ovm won't support any system vm including domain router, " +
                "please make sure you have a cluster with hypervisor type of any of xenserver/KVM/Vmware in the same pod" +
                " with Ovm cluster. And there is at least one host in UP status in that cluster.";
        throw new CloudRuntimeException(errMsg);
    }

    private void checkAndResetPriorityOfRedundantRouter(List<DomainRouterVO> routers) {
    	boolean allStopped = true;
    	for (DomainRouterVO router : routers) {
    		if (!router.getIsRedundantRouter() || router.getState() != VirtualMachine.State.Stopped) {
    			allStopped = false;
    			break;
    		}
    	}
    	if (!allStopped) {
    		return;
    	}
    	
    	for (DomainRouterVO router : routers) {
    		// getUpdatedPriority() would update the value later
    		router.setPriority(0);
    		router.setIsPriorityBumpUp(false);
    		_routerDao.update(router.getId(), router);
    	}
    }
    
    @DB
    protected List<DomainRouterVO> findOrDeployVirtualRouterInGuestNetwork(Network guestNetwork, DeployDestination dest, Account owner,
            boolean isRedundant, Map<Param, Object> params) throws ConcurrentOperationException,
            InsufficientCapacityException, ResourceUnavailableException {

        List<DomainRouterVO> routers = new ArrayList<DomainRouterVO>();
        Network lock = _networkDao.acquireInLockTable(guestNetwork.getId(), NetworkOrchestrationService.NetworkLockTimeout.value());
        if (lock == null) {
            throw new ConcurrentOperationException("Unable to lock network " + guestNetwork.getId());
        }
        
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Lock is acquired for network id " + lock.getId() + " as a part of router startup in " + dest);
        }
        
        try {

            assert guestNetwork.getState() == Network.State.Implemented || guestNetwork.getState() == Network.State.Setup ||
                    guestNetwork.getState() == Network.State.Implementing : "Network is not yet fully implemented: "
                    + guestNetwork;
            assert guestNetwork.getTrafficType() == TrafficType.Guest;
        
            // 1) Get deployment plan and find out the list of routers
            boolean isPodBased = (dest.getDataCenter().getNetworkType() == NetworkType.Basic);
        
            // dest has pod=null, for Basic Zone findOrDeployVRs for all Pods
            List<DeployDestination> destinations = new ArrayList<DeployDestination>();

            // for basic zone, if 'dest' has pod set to null then this is network restart scenario otherwise it is a vm deployment scenario
            if (dest.getDataCenter().getNetworkType() == NetworkType.Basic && dest.getPod() == null) {
                // Find all pods in the data center with running or starting user vms
                long dcId = dest.getDataCenter().getId();
                List<HostPodVO> pods = listByDataCenterIdVMTypeAndStates(dcId, VirtualMachine.Type.User, VirtualMachine.State.Starting, VirtualMachine.State.Running);

                // Loop through all the pods skip those with running or starting VRs
                for (HostPodVO pod: pods) {
                    // Get list of VRs in starting or running state
                    long podId = pod.getId();
                    List<DomainRouterVO> virtualRouters = _routerDao.listByPodIdAndStates(podId, VirtualMachine.State.Starting, VirtualMachine.State.Running);

                    assert (virtualRouters.size() <= 1) : "Pod can have utmost one VR in Basic Zone, please check!";

                    // Add virtualRouters to the routers, this avoids the situation when
                    // all routers are skipped and VirtualRouterElement throws exception
                    routers.addAll(virtualRouters);

                    // If List size is one, we already have a starting or running VR, skip deployment
                    if (virtualRouters.size() == 1) {
                        s_logger.debug("Skipping VR deployment: Found a running or starting VR in Pod "
                                + pod.getName() + " id=" + podId);
                        continue;
                    }
                    // Add new DeployDestination for this pod
                    destinations.add(new DeployDestination(dest.getDataCenter(), pod, null, null));
                }
            }
            else {
                // Else, just add the supplied dest
                destinations.add(dest);
            }

            // Except for Basic Zone, the for loop will iterate only once
            for (DeployDestination destination: destinations) {
                Pair<DeploymentPlan, List<DomainRouterVO>> planAndRouters = getDeploymentPlanAndRouters(isPodBased, destination, guestNetwork.getId());
            routers = planAndRouters.second();
        
            // 2) Figure out required routers count
            int routerCount = 1;
            if (isRedundant) {
                routerCount = 2;
                //Check current redundant routers, if possible(all routers are stopped), reset the priority
                if (routers.size() != 0) {
              	    checkAndResetPriorityOfRedundantRouter(routers);
                }
            }
        
                // If old network is redundant but new is single router, then routers.size() = 2 but routerCount = 1
            if (routers.size() >= routerCount) {
                return routers;
            }
        
            if (routers.size() >= 5) {
                s_logger.error("Too much redundant routers!");
            }

            // Check if providers are supported in the physical networks
            Type type = Type.VirtualRouter;
                Long physicalNetworkId = _networkModel.getPhysicalNetworkId(guestNetwork);
            PhysicalNetworkServiceProvider provider = _physicalProviderDao.findByServiceProvider(physicalNetworkId, type.toString());
            if (provider == null) {
                throw new CloudRuntimeException("Cannot find service provider " + type.toString() + " in physical network " + physicalNetworkId);
            }
            VirtualRouterProvider vrProvider = _vrProviderDao.findByNspIdAndType(provider.getId(), type);
            if (vrProvider == null) {
                    throw new CloudRuntimeException("Cannot find virtual router provider " + type.toString() + " as service provider " + provider.getId());
            }

                if (_networkModel.isNetworkSystem(guestNetwork) || guestNetwork.getGuestType() == Network.GuestType.Shared) {
                owner = _accountMgr.getAccount(Account.ACCOUNT_ID_SYSTEM);
            }

                // Check if public network has to be set on VR
            boolean publicNetwork = false;
                if (_networkModel.isProviderSupportServiceInNetwork(guestNetwork.getId(), Service.SourceNat, Provider.VirtualRouter)) {
                publicNetwork = true;
            }
            if (isRedundant && !publicNetwork) {
                s_logger.error("Didn't support redundant virtual router without public network!");
                return null;
            }

            Long offeringId = _networkOfferingDao.findById(guestNetwork.getNetworkOfferingId()).getServiceOfferingId();
            if (offeringId == null) {
                offeringId = _offering.getId();
            }

            PublicIp sourceNatIp = null;
            if (publicNetwork) {
                    sourceNatIp = _ipAddrMgr.assignSourceNatIpAddressToGuestNetwork(owner, guestNetwork);
            }

                // 3) deploy virtual router(s)
                int count = routerCount - routers.size();
                DeploymentPlan plan = planAndRouters.first();
                for (int i = 0; i < count; i++) {
                    LinkedHashMap<Network, List<? extends NicProfile>> networks = createRouterNetworks(owner, isRedundant, plan, guestNetwork, new Pair<Boolean, PublicIp>(
                            publicNetwork, sourceNatIp));
                    //don't start the router as we are holding the network lock that needs to be released at the end of router allocation
                    DomainRouterVO router = deployRouter(owner, destination, plan, params, isRedundant, vrProvider, offeringId, null, networks, false, null);

                    if (router != null) {
                        _routerDao.addRouterToGuestNetwork(router, guestNetwork);
                        routers.add(router);
                }
            }
            }
        } finally {
            if (lock != null) {
                _networkDao.releaseFromLockTable(lock.getId());
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Lock is released for network id " + lock.getId() + " as a part of router startup in " + dest);
                }
            }
        }
        return routers;
    }
    
    protected List<HostPodVO> listByDataCenterIdVMTypeAndStates(long id, VirtualMachine.Type type, VirtualMachine.State... states) {
        SearchBuilder<VMInstanceVO> vmInstanceSearch = _vmDao.createSearchBuilder();
        vmInstanceSearch.and("type", vmInstanceSearch.entity().getType(), SearchCriteria.Op.EQ);
        vmInstanceSearch.and("states", vmInstanceSearch.entity().getState(), SearchCriteria.Op.IN);

        SearchBuilder<HostPodVO> podIdSearch = _podDao.createSearchBuilder();
        podIdSearch.and("dc", podIdSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        podIdSearch.select(null, SearchCriteria.Func.DISTINCT, podIdSearch.entity().getId());
        podIdSearch.join("vmInstanceSearch", vmInstanceSearch, podIdSearch.entity().getId(),
                vmInstanceSearch.entity().getPodIdToDeployIn(), JoinBuilder.JoinType.INNER);
        podIdSearch.done();

        SearchCriteria<HostPodVO> sc = podIdSearch.create();
        sc.setParameters("dc", id);
        sc.setJoinParameters("vmInstanceSearch", "type", type);
        sc.setJoinParameters("vmInstanceSearch", "states", (Object[]) states);
        return _podDao.search(sc, null);
    }
 

    protected DomainRouterVO deployRouter(Account owner,
        DeployDestination dest,
        DeploymentPlan plan,
        Map<Param, Object> params,
        boolean isRedundant,
        VirtualRouterProvider vrProvider,
        long svcOffId,
        Long vpcId,
        LinkedHashMap<Network, List<? extends NicProfile>> networks,
        boolean startRouter,
        List<HypervisorType> supportedHypervisors) throws ConcurrentOperationException,
        InsufficientAddressCapacityException,
        InsufficientServerCapacityException,
        InsufficientCapacityException,
        StorageUnavailableException,
        ResourceUnavailableException {

        ServiceOfferingVO routerOffering = _serviceOfferingDao.findById(svcOffId);

        // Router is the network element, we don't know the hypervisor type yet.
        // Try to allocate the domR twice using diff hypervisors, and when failed both times, throw the exception up
        List<HypervisorType> hypervisors = getHypervisors(dest, plan, supportedHypervisors);

        int allocateRetry = 0;
        int startRetry = 0;
        DomainRouterVO router = null;
        for (Iterator<HypervisorType> iter = hypervisors.iterator(); iter.hasNext();) {
            HypervisorType hType = iter.next();
            try {
                long id = _routerDao.getNextInSequence(Long.class, "id");
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Allocating the VR i="+ id + " in datacenter "  + dest.getDataCenter() + "with the hypervisor type " + hType);
                }
                
                String templateName = null;
                switch (hType) {
                    case XenServer:
                        templateName = RouterTemplateXen.valueIn(dest.getDataCenter().getId());
                        break;
                    case KVM:
                        templateName = RouterTemplateKvm.valueIn(dest.getDataCenter().getId());
                        break;
                    case VMware:
                        templateName = RouterTemplateVmware.valueIn(dest.getDataCenter().getId());
                        break;
                    case Hyperv:
                        templateName = RouterTemplateHyperV.valueIn(dest.getDataCenter().getId());
                        break;
                    case LXC:
                        templateName = RouterTemplateLxc.valueIn(dest.getDataCenter().getId());
                        break;
                    default: break;
                }
                VMTemplateVO template = _templateDao.findRoutingTemplate(hType, templateName);

                if (template == null) {
                    s_logger.debug(hType + " won't support system vm, skip it");
                    continue;
                }
                
                boolean offerHA = routerOffering.getOfferHA();
                /* We don't provide HA to redundant router VMs, admin should own it all, and redundant router themselves are HA */
                if (isRedundant) {
                    offerHA = false;
                }

                router = new DomainRouterVO(id, routerOffering.getId(), vrProvider.getId(),
                VirtualMachineName.getRouterName(id, _instance), template.getId(), template.getHypervisorType(),
                template.getGuestOSId(), owner.getDomainId(), owner.getId(), isRedundant, 0, false,
                RedundantState.UNKNOWN, offerHA, false, vpcId);
                router.setDynamicallyScalable(template.isDynamicallyScalable());
                router.setRole(Role.VIRTUAL_ROUTER);
                router = _routerDao.persist(router);
                _itMgr.allocate(router.getInstanceName(), template, routerOffering, networks, plan, null);
                router = _routerDao.findById(router.getId());
            } catch (InsufficientCapacityException ex) {
                if (allocateRetry < 2 && iter.hasNext()) {
                    s_logger.debug("Failed to allocate the VR with hypervisor type " + hType + ", retrying one more time");
                    continue;
                } else {
                    throw ex;
                }
            } finally {
                allocateRetry++;
            }


            if (startRouter) {
                try {
                    router = startVirtualRouter(router, _accountMgr.getSystemUser(), _accountMgr.getSystemAccount(), params);
                    break;
                } catch (InsufficientCapacityException ex) {
                    if (startRetry < 2 && iter.hasNext()) {
                        s_logger.debug("Failed to start the VR  " + router + " with hypervisor type " + hType + ", " +
                                "destroying it and recreating one more time");
                        // destroy the router
                        destroyRouter(router.getId(), _accountMgr.getAccount(Account.ACCOUNT_ID_SYSTEM), User.UID_SYSTEM);
                        continue;
                    } else {
                        throw ex;
                    }
                } finally {
                    startRetry++;
                }
            } else {
                //return stopped router
                return router;
            }
        }
                
        return router;
    }


    protected List<HypervisorType> getHypervisors(DeployDestination dest, DeploymentPlan plan,
            List<HypervisorType> supportedHypervisors) throws InsufficientServerCapacityException {
        List<HypervisorType> hypervisors = new ArrayList<HypervisorType>();

        if (dest.getCluster() != null) {
            if (dest.getCluster().getHypervisorType() == HypervisorType.Ovm) {
                hypervisors.add(getClusterToStartDomainRouterForOvm(dest.getCluster().getPodId()));
            } else {
                hypervisors.add(dest.getCluster().getHypervisorType());
            }
        } else {
            HypervisorType defaults = _resourceMgr.getDefaultHypervisor(dest.getDataCenter().getId());
            if (defaults != HypervisorType.None) {
                hypervisors.add(defaults);
            } else {
                //if there is no default hypervisor, get it from the cluster
            hypervisors = _resourceMgr.getSupportedHypervisorTypes(dest.getDataCenter().getId(), true,
                    plan.getPodId());
        }
        }

        //keep only elements defined in supported hypervisors
        StringBuilder hTypesStr = new StringBuilder();
        if (supportedHypervisors != null && !supportedHypervisors.isEmpty()) {
            hypervisors.retainAll(supportedHypervisors);
            for (HypervisorType hType : supportedHypervisors) {
                hTypesStr.append(hType).append(" ");
            }
        }

        if (hypervisors.isEmpty()) {
            String errMsg = (hTypesStr.capacity() > 0) ? "supporting hypervisors " + hTypesStr.toString() : "";
            if (plan.getPodId() != null) {
                throw new InsufficientServerCapacityException("Unable to create virtual router, " +
                        "there are no clusters in the pod " + errMsg, Pod.class, plan.getPodId());
            }
            throw new InsufficientServerCapacityException("Unable to create virtual router, " +
                    "there are no clusters in the zone " + errMsg, DataCenter.class, dest.getDataCenter().getId());
        }
        return hypervisors;
    }

    protected LinkedHashMap<Network, List<? extends NicProfile>> createRouterNetworks(Account owner,
        boolean isRedundant,
            DeploymentPlan plan, Network guestNetwork, Pair<Boolean, PublicIp> publicNetwork) throws ConcurrentOperationException,
            InsufficientAddressCapacityException {

        
        boolean setupPublicNetwork = false;
        if (publicNetwork != null) {
            setupPublicNetwork = publicNetwork.first();
        }
        
        //Form networks
        LinkedHashMap<Network, List<? extends NicProfile>> networks = new LinkedHashMap<Network, List<? extends NicProfile>>(3);
        
        //1) Guest network
        boolean hasGuestNetwork = false;
        if (guestNetwork != null) {
            s_logger.debug("Adding nic for Virtual Router in Guest network " + guestNetwork);
            String defaultNetworkStartIp = null, defaultNetworkStartIpv6 = null;
            if (!setupPublicNetwork) {
            	    Nic placeholder = _networkModel.getPlaceholderNicForRouter(guestNetwork, plan.getPodId());
            	if (guestNetwork.getCidr() != null) {
            		if (placeholder != null && placeholder.getIp4Address() != null) {
            			s_logger.debug("Requesting ipv4 address " + placeholder.getIp4Address() + " stored in placeholder nic for the network " + guestNetwork);
            	        defaultNetworkStartIp = placeholder.getIp4Address();
            	    } else {
            	        String startIp = _networkModel.getStartIpAddress(guestNetwork.getId());
                        if (startIp != null && _ipAddressDao.findByIpAndSourceNetworkId(guestNetwork.getId(), startIp).getAllocatedTime() == null) {
                            defaultNetworkStartIp = startIp;
                        } else if (s_logger.isDebugEnabled()){
            				s_logger.debug("First ipv4 " + startIp + " in network id=" + guestNetwork.getId() +
                                    " is already allocated, can't use it for domain router; will get random ip address from the range");
                        }
            	    }
            	}
            	
            	if (guestNetwork.getIp6Cidr() != null) {
            		if (placeholder != null && placeholder.getIp6Address() != null) {
            			s_logger.debug("Requesting ipv6 address " + placeholder.getIp6Address() + " stored in placeholder nic for the network " + guestNetwork);
            			defaultNetworkStartIpv6 = placeholder.getIp6Address();
            		} else {
            		String startIpv6 = _networkModel.getStartIpv6Address(guestNetwork.getId());
            		if (startIpv6 != null && _ipv6Dao.findByNetworkIdAndIp(guestNetwork.getId(), startIpv6) == null) {
            			defaultNetworkStartIpv6 = startIpv6;
            		} else if (s_logger.isDebugEnabled()){
            			s_logger.debug("First ipv6 " + startIpv6 + " in network id=" + guestNetwork.getId() +
            					" is already allocated, can't use it for domain router; will get random ipv6 address from the range");
            		}
            	}
            }
            }

            NicProfile gatewayNic = new NicProfile(defaultNetworkStartIp, defaultNetworkStartIpv6);
            if (setupPublicNetwork) {
                if (isRedundant) {
                    gatewayNic.setIp4Address(_ipAddrMgr.acquireGuestIpAddress(guestNetwork, null));
                } else {
                    gatewayNic.setIp4Address(guestNetwork.getGateway());
                }
                gatewayNic.setBroadcastUri(guestNetwork.getBroadcastUri());
                gatewayNic.setBroadcastType(guestNetwork.getBroadcastDomainType());
                gatewayNic.setIsolationUri(guestNetwork.getBroadcastUri());
                gatewayNic.setMode(guestNetwork.getMode());
                String gatewayCidr = guestNetwork.getCidr();
                gatewayNic.setNetmask(NetUtils.getCidrNetmask(gatewayCidr));
            } else {
                gatewayNic.setDefaultNic(true);
            }
            
            networks.put(guestNetwork, new ArrayList<NicProfile>(Arrays.asList(gatewayNic)));
            hasGuestNetwork = true;
        }

        //2) Control network
        s_logger.debug("Adding nic for Virtual Router in Control network ");
        List<? extends NetworkOffering> offerings = _networkModel.getSystemAccountNetworkOfferings(NetworkOffering.SystemControlNetwork);
        NetworkOffering controlOffering = offerings.get(0);
        Network controlConfig = _networkMgr.setupNetwork(_systemAcct, controlOffering, plan, null, null, false).get(0);
        networks.put(controlConfig, new ArrayList<NicProfile>());
        
        
        //3) Public network
        if (setupPublicNetwork) {
            PublicIp sourceNatIp = publicNetwork.second();
            s_logger.debug("Adding nic for Virtual Router in Public network ");
            //if source nat service is supported by the network, get the source nat ip address
            NicProfile defaultNic = new NicProfile();
            defaultNic.setDefaultNic(true);
            defaultNic.setIp4Address(sourceNatIp.getAddress().addr());
            defaultNic.setGateway(sourceNatIp.getGateway());
            defaultNic.setNetmask(sourceNatIp.getNetmask());
            defaultNic.setMacAddress(sourceNatIp.getMacAddress());
            // get broadcast from public network
            Network pubNet = _networkDao.findById(sourceNatIp.getNetworkId());
            if (pubNet.getBroadcastDomainType() == BroadcastDomainType.Vxlan) {
                defaultNic.setBroadcastType(BroadcastDomainType.Vxlan);
                defaultNic.setBroadcastUri(BroadcastDomainType.Vxlan.toUri(sourceNatIp.getVlanTag()));
                defaultNic.setIsolationUri(BroadcastDomainType.Vxlan.toUri(sourceNatIp.getVlanTag()));
            } else {
                defaultNic.setBroadcastType(BroadcastDomainType.Vlan);
                defaultNic.setBroadcastUri(BroadcastDomainType.Vlan.toUri(sourceNatIp.getVlanTag()));
                defaultNic.setIsolationUri(IsolationType.Vlan.toUri(sourceNatIp.getVlanTag()));
            }
            if (hasGuestNetwork) {
                defaultNic.setDeviceId(2);
            }
            NetworkOffering publicOffering = _networkModel.getSystemAccountNetworkOfferings(NetworkOffering.SystemPublicNetwork).get(0);
            List<? extends Network> publicNetworks = _networkMgr.setupNetwork(_systemAcct, publicOffering, plan, null, null, false);
            String publicIp = defaultNic.getIp4Address();
            // We want to use the identical MAC address for RvR on public interface if possible
            NicVO peerNic = _nicDao.findByIp4AddressAndNetworkId(publicIp, publicNetworks.get(0).getId());
            if (peerNic != null) {
                s_logger.info("Use same MAC as previous RvR, the MAC is " + peerNic.getMacAddress());
                defaultNic.setMacAddress(peerNic.getMacAddress());
            }
            networks.put(publicNetworks.get(0), new ArrayList<NicProfile>(Arrays.asList(defaultNic)));
        }

        return networks;
    }

    
    protected Pair<DeploymentPlan, List<DomainRouterVO>> getDeploymentPlanAndRouters(boolean isPodBased,
            DeployDestination dest, long guestNetworkId) {
        long dcId = dest.getDataCenter().getId();
        List<DomainRouterVO> routers = null;
        DeploymentPlan plan = new DataCenterDeployment(dcId);
        if (isPodBased) {
            Pod pod = dest.getPod();
            Long podId = null;
            if (pod != null) {
                podId = pod.getId();
            } else {
                throw new CloudRuntimeException("Pod id is expected in deployment destination");
            }
            routers = _routerDao.listByNetworkAndPodAndRole(guestNetworkId, podId, Role.VIRTUAL_ROUTER);
            plan = new DataCenterDeployment(dcId, podId, null, null, null, null);
        } else {
            routers = _routerDao.listByNetworkAndRole(guestNetworkId, Role.VIRTUAL_ROUTER);
        }
        
        return new Pair<DeploymentPlan, List<DomainRouterVO>>(plan, routers);
    }
    
    
    private DomainRouterVO startVirtualRouter(DomainRouterVO router, User user, Account caller, Map<Param, Object> params)
            throws StorageUnavailableException, InsufficientCapacityException,
    ConcurrentOperationException, ResourceUnavailableException {
        
        if (router.getRole() != Role.VIRTUAL_ROUTER || !router.getIsRedundantRouter()) {
            return this.start(router, user, caller, params, null);
        }

        if (router.getState() == State.Running) {
            s_logger.debug("Redundant router " + router.getInstanceName() + " is already running!");
            return router;
        }

        DataCenterDeployment plan = new DataCenterDeployment(0, null, null, null, null, null);
        DomainRouterVO result = null;
        assert router.getIsRedundantRouter();
        List<Long> networkIds = _routerDao.getRouterNetworks(router.getId());
        //Not support VPC now
        if (networkIds.size() > 1) {
            throw new ResourceUnavailableException("Unable to support more than one guest network for redundant router now!",
                    DataCenter.class, router.getDataCenterId());
        }
        DomainRouterVO routerToBeAvoid = null;
        if (networkIds.size() != 0)  {
            List<DomainRouterVO> routerList = _routerDao.findByNetwork(networkIds.get(0));
            for (DomainRouterVO rrouter : routerList) {
                if (rrouter.getHostId() != null && rrouter.getIsRedundantRouter() && rrouter.getState() == State.Running) {
                    if (routerToBeAvoid != null) {
                        throw new ResourceUnavailableException("Try to start router " + router.getInstanceName() + "(" + router.getId() + ")"
                                + ", but there are already two redundant routers with IP " + router.getPublicIpAddress()
                                + ", they are " + rrouter.getInstanceName() + "(" + rrouter.getId() + ") and "
                                + routerToBeAvoid.getInstanceName() + "(" + routerToBeAvoid.getId() + ")",
                                DataCenter.class, rrouter.getDataCenterId());
                    }
                    routerToBeAvoid = rrouter;
                }
            }
        }
        if (routerToBeAvoid == null) {
            return this.start(router, user, caller, params, null);
        }
        // We would try best to deploy the router to another place
        int retryIndex = 5;
        ExcludeList[] avoids = new ExcludeList[5];
        avoids[0] = new ExcludeList();
        avoids[0].addPod(routerToBeAvoid.getPodIdToDeployIn());
        avoids[1] = new ExcludeList();
        avoids[1].addCluster(_hostDao.findById(routerToBeAvoid.getHostId()).getClusterId());
        avoids[2] = new ExcludeList();
        List<VolumeVO> volumes = _volumeDao.findByInstanceAndType(routerToBeAvoid.getId(), Volume.Type.ROOT);
        if (volumes != null && volumes.size() != 0) {
            avoids[2].addPool(volumes.get(0).getPoolId());
        }
        avoids[2].addHost(routerToBeAvoid.getHostId());
        avoids[3] = new ExcludeList();
        avoids[3].addHost(routerToBeAvoid.getHostId());
        avoids[4] = new ExcludeList();

        for (int i = 0; i < retryIndex; i++) {
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("Try to deploy redundant virtual router:" + router.getHostName() + ", for " + i + " time");
            }
            plan.setAvoids(avoids[i]);
            try {
                result = this.start(router, user, caller, params, plan);
            } catch (InsufficientServerCapacityException ex) {
                result = null;
            }
            if (result != null) {
                break;
            }
        }
        return result;
    }

    @Override
    public List<DomainRouterVO> deployVirtualRouterInGuestNetwork(Network guestNetwork, DeployDestination dest, Account owner,
            Map<Param, Object> params, boolean isRedundant) throws InsufficientCapacityException,
    ConcurrentOperationException, ResourceUnavailableException {

        List<DomainRouterVO> routers = findOrDeployVirtualRouterInGuestNetwork
                (guestNetwork, dest, owner, isRedundant, params);
        
        return startRouters(params, routers);
    }

    protected List<DomainRouterVO> startRouters(Map<Param, Object> params, List<DomainRouterVO> routers) throws StorageUnavailableException, InsufficientCapacityException, ConcurrentOperationException,
            ResourceUnavailableException {
        List<DomainRouterVO> runningRouters = null;

        if (routers != null) {
            runningRouters = new ArrayList<DomainRouterVO>();
        }

        for (DomainRouterVO router : routers) {
            boolean skip = false;
            State state = router.getState();
            if (router.getHostId() != null && state != State.Running) {
                HostVO host = _hostDao.findById(router.getHostId());
                if (host == null || host.getState() != Status.Up) {
                    skip = true;
                }
            }
            if (!skip) {
                if (state != State.Running) {
                    router = startVirtualRouter(router, _accountMgr.getSystemUser(), _accountMgr.getSystemAccount(), params);
                }
                if (router != null) {
                    runningRouters.add(router);
                }
            }
        }
        return runningRouters;
    }

    @Override
    public boolean finalizeVirtualMachineProfile(VirtualMachineProfile profile, DeployDestination dest,
            ReservationContext context) {
        
        boolean dnsProvided = true;
        boolean dhcpProvided = true;
        boolean publicNetwork = false;
        DataCenterVO dc = _dcDao.findById(dest.getDataCenter().getId());
        _dcDao.loadDetails(dc);

        //1) Set router details
        DomainRouterVO router = _routerDao.findById(profile.getVirtualMachine().getId());
        Map<String, String> details = _vmDetailsDao.listDetailsKeyPairs(router.getId());
        router.setDetails(details);

        //2) Prepare boot loader elements related with Control network

        StringBuilder buf = profile.getBootArgsBuilder();
        buf.append(" template=domP");
        buf.append(" name=").append(profile.getHostName());

        if (Boolean.valueOf(_configDao.getValue("system.vm.random.password"))) {
            buf.append(" vmpassword=").append(_configDao.getValue("system.vm.password"));
        }
        
        NicProfile controlNic = null;
        String defaultDns1 = null;
        String defaultDns2 = null;
        String defaultIp6Dns1 = null;
        String defaultIp6Dns2 = null;
        for (NicProfile nic : profile.getNics()) {
            int deviceId = nic.getDeviceId();
            boolean ipv4 = false, ipv6 = false;
            if (nic.getIp4Address() != null) {
            	ipv4 = true;
            buf.append(" eth").append(deviceId).append("ip=").append(nic.getIp4Address());
            buf.append(" eth").append(deviceId).append("mask=").append(nic.getNetmask());
            }
            if (nic.getIp6Address() != null) {
            	ipv6 = true;
            	buf.append(" eth").append(deviceId).append("ip6=").append(nic.getIp6Address());
            	buf.append(" eth").append(deviceId).append("ip6prelen=").append(NetUtils.getIp6CidrSize(nic.getIp6Cidr()));
            }
            
            if (nic.isDefaultNic()) {
            	if (ipv4) {
                buf.append(" gateway=").append(nic.getGateway());
            	}
            	if (ipv6) {
            		buf.append(" ip6gateway=").append(nic.getIp6Gateway());
            	}
                defaultDns1 = nic.getDns1();
                defaultDns2 = nic.getDns2();
                defaultIp6Dns1 = nic.getIp6Dns1();
                defaultIp6Dns2 = nic.getIp6Dns2();
            }

            if (nic.getTrafficType() == TrafficType.Management) {
                buf.append(" localgw=").append(dest.getPod().getGateway());
            } else if (nic.getTrafficType() == TrafficType.Control) {
                controlNic = nic;
                // DOMR control command is sent over management server in VMware
                if (dest.getHost().getHypervisorType() == HypervisorType.VMware || dest.getHost().getHypervisorType() == HypervisorType.Hyperv) {
                    s_logger.info("Check if we need to add management server explicit route to DomR. pod cidr: " + dest.getPod().getCidrAddress() + "/" +
                                  dest.getPod().getCidrSize() + ", pod gateway: " + dest.getPod().getGateway() + ", management host: " + ClusterManager.ManagementHostIPAdr.value());

                    if (s_logger.isInfoEnabled()) {
                        s_logger.info("Add management server explicit route to DomR.");
                    }

                    // always add management explicit route, for basic networking setup, DomR may have two interfaces while both
                    // are on the same subnet
                    _mgmt_cidr = _configDao.getValue(Config.ManagementNetwork.key());
                    if (NetUtils.isValidCIDR(_mgmt_cidr)) {
                        buf.append(" mgmtcidr=").append(_mgmt_cidr);
                        buf.append(" localgw=").append(dest.getPod().getGateway());
                    }


                    if (dc.getNetworkType() == NetworkType.Basic) {
                        // ask domR to setup SSH on guest network
                        buf.append(" sshonguest=true");
                    }

                }
            }  else if (nic.getTrafficType() == TrafficType.Guest) {
                dnsProvided = _networkModel.isProviderSupportServiceInNetwork(nic.getNetworkId(), Service.Dns, Provider.VirtualRouter);
                dhcpProvided = _networkModel.isProviderSupportServiceInNetwork(nic.getNetworkId(), Service.Dhcp, Provider.VirtualRouter);
                //build bootloader parameter for the guest
                buf.append(createGuestBootLoadArgs(nic, defaultDns1, defaultDns2, router));
            } else if (nic.getTrafficType() == TrafficType.Public) {
                publicNetwork = true;
            }
        }

        if (controlNic == null) {
            throw new CloudRuntimeException("Didn't start a control port");
        }
        
        String rpValue = _configDao.getValue(Config.NetworkRouterRpFilter.key());
        if (rpValue != null && rpValue.equalsIgnoreCase("true")) {
            _disable_rp_filter = true;
        }else {
            _disable_rp_filter = false;
        }

        String rpFilter = " ";
        String type = null;
        if (router.getVpcId() != null) {
            type = "vpcrouter";
            if (_disable_rp_filter) {
                rpFilter=" disable_rp_filter=true";
        }
        } else if (!publicNetwork) {
            type = "dhcpsrvr";
        } else {
            type = "router";
            if (_disable_rp_filter) {
                rpFilter=" disable_rp_filter=true";
        }
        }
        
        if (_disable_rp_filter) {
            rpFilter=" disable_rp_filter=true";
        }
        
        buf.append(" type=" + type + rpFilter);

        String domain_suffix = dc.getDetail(ZoneConfig.DnsSearchOrder.getName());
        if (domain_suffix != null) {
            buf.append(" dnssearchorder=").append(domain_suffix);
        }

        if (profile.getHypervisorType() == HypervisorType.VMware) {
            buf.append(" extra_pubnics=" + _routerExtraPublicNics);
        }
        
        /* If virtual router didn't provide DNS service but provide DHCP service, we need to override the DHCP response
         * to return DNS server rather than
         * virtual router itself. */
        if (dnsProvided || dhcpProvided) {
            if (defaultDns1 != null) {
                buf.append(" dns1=").append(defaultDns1);
            }
            if (defaultDns2 != null) {
                buf.append(" dns2=").append(defaultDns2);
            }
            if (defaultIp6Dns1 != null) {
                buf.append(" ip6dns1=").append(defaultIp6Dns1);
            }
            if (defaultIp6Dns2 != null) {
                buf.append(" ip6dns2=").append(defaultIp6Dns2);
            }

            boolean useExtDns = !dnsProvided;
            /* For backward compatibility */
            useExtDns = useExtDns || UseExternalDnsServers.valueIn(dc.getId());

            if (useExtDns) {
                buf.append(" useextdns=true");
            }
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Boot Args for " + profile + ": " + buf.toString());
        }

        return true;
        }

    
    protected StringBuilder createGuestBootLoadArgs(NicProfile guestNic, String defaultDns1,
            String defaultDns2, DomainRouterVO router) {
        long guestNetworkId = guestNic.getNetworkId();
        NetworkVO guestNetwork = _networkDao.findById(guestNetworkId);
        String dhcpRange = null;
        DataCenterVO dc = _dcDao.findById(guestNetwork.getDataCenterId());

        StringBuilder buf = new StringBuilder();
        
        boolean isRedundant = router.getIsRedundantRouter();
        if (isRedundant) {
            buf.append(" redundant_router=1");
            List<DomainRouterVO> routers = _routerDao.listByNetworkAndRole(guestNetwork.getId(), Role.VIRTUAL_ROUTER);
            try {
                int priority = getUpdatedPriority(guestNetwork, routers, router);
                router.setPriority(priority);
                router = _routerDao.persist(router);
            } catch (InsufficientVirtualNetworkCapcityException e) {
                s_logger.error("Failed to get update priority!", e);
                throw new CloudRuntimeException("Failed to get update priority!");
            }
            Network net = _networkModel.getNetwork(guestNic.getNetworkId());
            buf.append(" guestgw=").append(net.getGateway());
            String brd = NetUtils.long2Ip(NetUtils.ip2Long(guestNic.getIp4Address()) | ~NetUtils.ip2Long(guestNic.getNetmask()));
            buf.append(" guestbrd=").append(brd);
            buf.append(" guestcidrsize=").append(NetUtils.getCidrSize(guestNic.getNetmask()));
            buf.append(" router_pr=").append(router.getPriority());
        }

        //setup network domain
        String domain = guestNetwork.getNetworkDomain();
        if (domain != null) {
            buf.append(" domain=" + domain);
        }
        
        //setup dhcp range
        if (dc.getNetworkType() == NetworkType.Basic) {
            if (guestNic.isDefaultNic()) {
                long cidrSize = NetUtils.getCidrSize(guestNic.getNetmask());
                String cidr = NetUtils.getCidrSubNet(guestNic.getGateway(), cidrSize);
                if (cidr != null) {
                    dhcpRange = NetUtils.getIpRangeStartIpFromCidr(cidr, cidrSize);
                }
            }
        } else if (dc.getNetworkType() == NetworkType.Advanced) {
            String cidr = guestNetwork.getCidr();
            if (cidr != null) {
                dhcpRange = NetUtils.getDhcpRange(cidr);
            }
        }
        
        if (dhcpRange != null) {
            buf.append(" dhcprange=" + dhcpRange);
        }
        
        return buf;
    }


    protected String getGuestDhcpRange(NicProfile guestNic, Network guestNetwork, DataCenter dc) {
        String dhcpRange = null;
        //setup dhcp range
        if (dc.getNetworkType() == NetworkType.Basic) {
            long cidrSize = NetUtils.getCidrSize(guestNic.getNetmask());
            String cidr = NetUtils.getCidrSubNet(guestNic.getGateway(), cidrSize);
            if (cidr != null) {
                dhcpRange = NetUtils.getIpRangeStartIpFromCidr(cidr, cidrSize);
            }
        } else if (dc.getNetworkType() == NetworkType.Advanced) {
            String cidr = guestNetwork.getCidr();
            if (cidr != null) {
                dhcpRange = NetUtils.getDhcpRange(cidr);
            }
        }
        return dhcpRange;
    }

    @Override
    public boolean setupDhcpForPvlan(boolean add, DomainRouterVO router, Long hostId, NicProfile nic) {
    	if (!nic.getBroadCastUri().getScheme().equals("pvlan")) {
    		return false;
    	}
    	String op = "add";
    	if (!add) {
    		op = "delete";
    	}
    	Network network = _networkDao.findById(nic.getNetworkId());
    	String networkTag = _networkModel.getNetworkTag(router.getHypervisorType(), network);
    	PvlanSetupCommand cmd = PvlanSetupCommand.createDhcpSetup(op, nic.getBroadCastUri(), networkTag, router.getInstanceName(), nic.getMacAddress(), nic.getIp4Address());
    	// In fact we send command to the host of router, we're not programming router but the host
        Answer answer = null;
    	try {
            answer = _agentMgr.send(hostId, cmd);
        } catch (OperationTimedoutException e) {
            s_logger.warn("Timed Out", e);
            return false;
		} catch (AgentUnavailableException e) {
            s_logger.warn("Agent Unavailable ", e);
			return false;
		}

        if (answer == null || !answer.getResult()) {
        	return false;
        }
    	return true;
    }
    
    @Override
    public boolean finalizeDeployment(Commands cmds, VirtualMachineProfile profile,
            DeployDestination dest, ReservationContext context) throws ResourceUnavailableException {
        DomainRouterVO router = _routerDao.findById(profile.getId());

        List<NicProfile> nics = profile.getNics();
        for (NicProfile nic : nics) {
            if (nic.getTrafficType() == TrafficType.Public) {
                router.setPublicIpAddress(nic.getIp4Address());
                router.setPublicNetmask(nic.getNetmask());
                router.setPublicMacAddress(nic.getMacAddress());
            } else if (nic.getTrafficType() == TrafficType.Control) {
                router.setPrivateIpAddress(nic.getIp4Address());
                router.setPrivateMacAddress(nic.getMacAddress());
            }
        }
        _routerDao.update(router.getId(), router);

        finalizeCommandsOnStart(cmds, profile);
        return true;
    }

    @Override
    public boolean finalizeCommandsOnStart(Commands cmds, VirtualMachineProfile profile) {
        DomainRouterVO router = _routerDao.findById(profile.getId());
        NicProfile controlNic = getControlNic(profile);

        if (controlNic == null) {
            s_logger.error("Control network doesn't exist for the router " + router);
            return false;
        }

        finalizeSshAndVersionAndNetworkUsageOnStart(cmds, profile, router, controlNic);

        // restart network if restartNetwork = false is not specified in profile parameters
        boolean reprogramGuestNtwks = true;
        if (profile.getParameter(Param.ReProgramGuestNetworks) != null
                && (Boolean) profile.getParameter(Param.ReProgramGuestNetworks) == false) {
            reprogramGuestNtwks = false;
        }

        VirtualRouterProvider vrProvider = _vrProviderDao.findById(router.getElementId());
        if (vrProvider == null) {
            throw new CloudRuntimeException("Cannot find related virtual router provider of router: " + router.getHostName());
        }
        Provider provider = Network.Provider.getProvider(vrProvider.getType().toString());
        if (provider == null) {
            throw new CloudRuntimeException("Cannot find related provider of virtual router provider: " + vrProvider.getType().toString());
        }

        List<Long> routerGuestNtwkIds = _routerDao.getRouterNetworks(router.getId());
        for (Long guestNetworkId : routerGuestNtwkIds) {
            if (reprogramGuestNtwks) {
                finalizeIpAssocForNetwork(cmds, router, provider, guestNetworkId, null);
                finalizeNetworkRulesForNetwork(cmds, router, provider, guestNetworkId);
            }

            finalizeUserDataAndDhcpOnStart(cmds, router, provider, guestNetworkId);
        }

        String serviceMonitringSet = _configDao.getValue(Config.EnableServiceMonitoring.key());

        if (serviceMonitringSet != null && serviceMonitringSet.equalsIgnoreCase("true")) {
            finalizeMonitorServiceOnStrat(cmds, profile, router, provider, routerGuestNtwkIds.get(0), true);
         } else {
            finalizeMonitorServiceOnStrat(cmds, profile, router, provider, routerGuestNtwkIds.get(0), false);
        }


        return true;
    }

    private void finalizeMonitorServiceOnStrat(Commands cmds, VirtualMachineProfile profile, DomainRouterVO router, Provider provider, long networkId, Boolean add) {

        NetworkVO network = _networkDao.findById(networkId);

        s_logger.debug("Creating  monitoring services on "+ router +" start...");


        // get the list of sevices for this network to monitor
        List <MonitoringServiceVO> services = new ArrayList<MonitoringServiceVO>();
        if (_networkModel.isProviderSupportServiceInNetwork(network.getId(), Service.Dhcp, Provider.VirtualRouter) ||
                _networkModel.isProviderSupportServiceInNetwork(network.getId(), Service.Dns, Provider.VirtualRouter)) {
            MonitoringServiceVO dhcpService = _monitorServiceDao.getServiceByName(MonitoringService.Service.Dhcp.toString());
            if (dhcpService != null) {
                services.add(dhcpService);
            }
        }

        if (_networkModel.isProviderSupportServiceInNetwork(network.getId(), Service.Lb, Provider.VirtualRouter)) {
            MonitoringServiceVO lbService = _monitorServiceDao.getServiceByName(MonitoringService.Service.LoadBalancing.toString());
            if (lbService != null) {
                services.add(lbService);
            }
        }
        List<MonitoringServiceVO> defaultServices = _monitorServiceDao.listDefaultServices(true);
        services.addAll(defaultServices);

        List<MonitorServiceTO> servicesTO = new ArrayList<MonitorServiceTO>();
        for (MonitoringServiceVO service: services) {
            MonitorServiceTO serviceTO = new MonitorServiceTO( service.getService(), service.getProcessname(), service.getServiceName(), service.getServicePath(),
                    service.getPidFile(), service.isDefaultService());
            servicesTO.add(serviceTO);
        }

        // TODO : This is a hacking fix
        // at VR startup time, information in VirtualMachineProfile may not updated to DB yet, 
        // getRouterControlIp() may give wrong IP under basic network mode in VMware environment
        NicProfile controlNic = getControlNic(profile);
        SetMonitorServiceCommand command = new SetMonitorServiceCommand(servicesTO);
        command.setAccessDetail(NetworkElementCommand.ROUTER_IP, controlNic.getIp4Address());
        command.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, getRouterIpInNetwork(networkId, router.getId()));
        command.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());

        if (!add) {
            command.setAccessDetail(NetworkElementCommand.ROUTER_MONITORING_DISABLE, add.toString());
        }

        cmds.addCommand("monitor", command);
    }

    protected NicProfile getControlNic(VirtualMachineProfile profile) {
        DomainRouterVO router = _routerDao.findById(profile.getId());
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        NicProfile controlNic = null;
        if (profile.getHypervisorType() == HypervisorType.VMware && dcVo.getNetworkType() == NetworkType.Basic) {
            // TODO this is a ugly to test hypervisor type here
            // for basic network mode, we will use the guest NIC for control NIC
            for (NicProfile nic : profile.getNics()) {
                if (nic.getTrafficType() == TrafficType.Guest && nic.getIp4Address() != null) {
                    controlNic = nic;
                }
            }
        } else {
            for (NicProfile nic : profile.getNics()) {
                if (nic.getTrafficType() == TrafficType.Control && nic.getIp4Address() != null) {
                    controlNic = nic;
                }
            }
        }
        return controlNic;
    }

    protected void finalizeSshAndVersionAndNetworkUsageOnStart(Commands cmds, VirtualMachineProfile profile, DomainRouterVO router, NicProfile controlNic) {
        DomainRouterVO vr = _routerDao.findById(profile.getId());
        cmds.addCommand("checkSsh", new CheckSshCommand(profile.getInstanceName(), controlNic.getIp4Address(), 3922));

        // Update router template/scripts version
        final GetDomRVersionCmd command = new GetDomRVersionCmd();
        command.setAccessDetail(NetworkElementCommand.ROUTER_IP, controlNic.getIp4Address());
        command.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
        cmds.addCommand("getDomRVersion", command);

        // Network usage command to create iptables rules
        boolean forVpc = vr.getVpcId() != null;
        if (!forVpc)
            cmds.addCommand("networkUsage", new NetworkUsageCommand(controlNic.getIp4Address(), router.getHostName(), "create", forVpc));
    }

    protected void finalizeUserDataAndDhcpOnStart(Commands cmds, DomainRouterVO router, Provider provider, Long guestNetworkId) {
        if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.Dhcp, provider)) {
            // Resend dhcp
            s_logger.debug("Reapplying dhcp entries as a part of domR " + router + " start...");
            createDhcpEntryCommandsForVMs(router, cmds, guestNetworkId);
        }
   
        if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.UserData, provider)) {
            // Resend user data
            s_logger.debug("Reapplying vm data (userData and metaData) entries as a part of domR " + router + " start...");
            createVmDataCommandForVMs(router, cmds, guestNetworkId);
        }
    }

    protected void finalizeNetworkRulesForNetwork(Commands cmds, DomainRouterVO router, Provider provider, Long guestNetworkId) {
        s_logger.debug("Resending ipAssoc, port forwarding, load balancing rules as a part of Virtual router start");
      
        ArrayList<? extends PublicIpAddress> publicIps = getPublicIpsToApply(router, provider, guestNetworkId);
        List<FirewallRule> firewallRulesEgress = new ArrayList<FirewallRule>();

        //  Fetch firewall Egress rules.
        if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.Firewall, provider)) {
            firewallRulesEgress.addAll(_rulesDao.listByNetworkPurposeTrafficType(guestNetworkId, Purpose.Firewall,FirewallRule.TrafficType.Egress));
        }

        // Re-apply firewall Egress rules
        s_logger.debug("Found " + firewallRulesEgress.size() + " firewall Egress rule(s) to apply as a part of domR " + router + " start.");
        if (!firewallRulesEgress.isEmpty()) {
            createFirewallRulesCommands(firewallRulesEgress, router, cmds, guestNetworkId);
        }

        if (publicIps != null && !publicIps.isEmpty()) {
            List<RemoteAccessVpn> vpns = new ArrayList<RemoteAccessVpn>();
            List<PortForwardingRule> pfRules = new ArrayList<PortForwardingRule>();
            List<FirewallRule> staticNatFirewallRules = new ArrayList<FirewallRule>();
            List<StaticNat> staticNats = new ArrayList<StaticNat>();
            List<FirewallRule> firewallRulesIngress = new ArrayList<FirewallRule>();
      
            //Get information about all the rules (StaticNats and StaticNatRules; PFVPN to reapply on domR start)
            for (PublicIpAddress ip : publicIps) {
                if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.PortForwarding, provider)) {
                    pfRules.addAll(_pfRulesDao.listForApplication(ip.getId()));
                }
                if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.StaticNat, provider)) {
                    staticNatFirewallRules.addAll(_rulesDao.listByIpAndPurpose(ip.getId(), Purpose.StaticNat));
                }
                if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.Firewall, provider)) {
                    firewallRulesIngress.addAll(_rulesDao.listByIpAndPurpose(ip.getId(), Purpose.Firewall));
                }
      
                if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.Vpn, provider)) {
                    RemoteAccessVpn vpn = _vpnDao.findByPublicIpAddress(ip.getId());
                    if (vpn != null) {
                        vpns.add(vpn);
                    }
                }
      
                if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.StaticNat, provider)) {
                    if (ip.isOneToOneNat()) {
                            StaticNatImpl staticNat = new StaticNatImpl(ip.getAccountId(), ip.getDomainId(), guestNetworkId, ip.getId(), ip.getVmIp(), false);
                        staticNats.add(staticNat);
                    }
                }
            }
   
            // Re-apply static nats
            s_logger.debug("Found " + staticNats.size() + " static nat(s) to apply as a part of domR " + router + " start.");
            if (!staticNats.isEmpty()) {
                createApplyStaticNatCommands(staticNats, router, cmds, guestNetworkId);
            }
       
            // Re-apply firewall Ingress rules
            s_logger.debug("Found " + firewallRulesIngress.size() + " firewall Ingress rule(s) to apply as a part of domR " + router + " start.");
            if (!firewallRulesIngress.isEmpty()) {
                createFirewallRulesCommands(firewallRulesIngress, router, cmds, guestNetworkId);
            }
       
            // Re-apply port forwarding rules
            s_logger.debug("Found " + pfRules.size() + " port forwarding rule(s) to apply as a part of domR " + router + " start.");
            if (!pfRules.isEmpty()) {
                createApplyPortForwardingRulesCommands(pfRules, router, cmds, guestNetworkId);
            }
       
            // Re-apply static nat rules
            s_logger.debug("Found " + staticNatFirewallRules.size() + " static nat rule(s) to apply as a part of domR " + router + " start.");
            if (!staticNatFirewallRules.isEmpty()) {
                List<StaticNatRule> staticNatRules = new ArrayList<StaticNatRule>();
                for (FirewallRule rule : staticNatFirewallRules) {
                    staticNatRules.add(_rulesMgr.buildStaticNatRule(rule, false));
                }
                createApplyStaticNatRulesCommands(staticNatRules, router, cmds, guestNetworkId);
            }
   
            // Re-apply vpn rules
            s_logger.debug("Found " + vpns.size() + " vpn(s) to apply as a part of domR " + router + " start.");
            if (!vpns.isEmpty()) {
                for (RemoteAccessVpn vpn : vpns) {
                    createApplyVpnCommands(true, vpn, router, cmds);
                }
            }
   
            List<LoadBalancerVO> lbs = _loadBalancerDao.listByNetworkIdAndScheme(guestNetworkId, Scheme.Public);
            List<LoadBalancingRule> lbRules = new ArrayList<LoadBalancingRule>();
            if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.Lb, provider)) {
                // Re-apply load balancing rules
                for (LoadBalancerVO lb : lbs) {
                    List<LbDestination> dstList = _lbMgr.getExistingDestinations(lb.getId());
                    List<LbStickinessPolicy> policyList = _lbMgr.getStickinessPolicies(lb.getId());
                    List<LbHealthCheckPolicy> hcPolicyList = _lbMgr.getHealthCheckPolicies(lb.getId());
                    Ip sourceIp = _networkModel.getPublicIpAddress(lb.getSourceIpAddressId()).getAddress();
                    LbSslCert sslCert = _lbMgr.getLbSslCert(lb.getId());
                    LoadBalancingRule loadBalancing = new LoadBalancingRule(lb, dstList, policyList, hcPolicyList, sourceIp, sslCert, lb.getLbProtocol());
                    lbRules.add(loadBalancing);
                }
            }

            s_logger.debug("Found " + lbRules.size() + " load balancing rule(s) to apply as a part of domR " + router + " start.");
            if (!lbRules.isEmpty()) {
                    createApplyLoadBalancingRulesCommands(lbRules, router, cmds, guestNetworkId);
            }
        }
        //Reapply dhcp and dns configuration.
        Network guestNetwork = _networkDao.findById(guestNetworkId);
        if (guestNetwork.getGuestType()==GuestType.Shared &&  _networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.Dhcp, provider)) {
            Map<Network.Capability, String> dhcpCapabilities = _networkSvc.getNetworkOfferingServiceCapabilities(_networkOfferingDao.findById(_networkDao.findById(guestNetworkId).getNetworkOfferingId()), Service.Dhcp);
            String supportsMultipleSubnets = dhcpCapabilities.get(Network.Capability.DhcpAccrossMultipleSubnets);
            if (supportsMultipleSubnets != null && Boolean.valueOf(supportsMultipleSubnets)) {
                List<NicIpAliasVO> revokedIpAliasVOs = _nicIpAliasDao.listByNetworkIdAndState(guestNetworkId, NicIpAlias.state.revoked);
                s_logger.debug("Found" + revokedIpAliasVOs.size() + "ip Aliases to revoke on the router as a part of dhcp configuration");
                removeRevokedIpAliasFromDb(revokedIpAliasVOs);

                List<NicIpAliasVO> aliasVOs = _nicIpAliasDao.listByNetworkIdAndState(guestNetworkId, NicIpAlias.state.active);
                s_logger.debug("Found" + aliasVOs.size() + "ip Aliases to apply on the router as a part of dhcp configuration");
                List<IpAliasTO> activeIpAliasTOs = new ArrayList<IpAliasTO>();
                for (NicIpAliasVO aliasVO : aliasVOs) {
                    activeIpAliasTOs.add(new IpAliasTO(aliasVO.getIp4Address(), aliasVO.getNetmask(), aliasVO.getAliasCount().toString()));
                }
                if (activeIpAliasTOs.size() != 0){
                    createIpAlias(router, activeIpAliasTOs, guestNetworkId, cmds);
                    configDnsMasq(router, _networkDao.findById(guestNetworkId), cmds);
                }
            }
        }
    }

    private void removeRevokedIpAliasFromDb(List<NicIpAliasVO> revokedIpAliasVOs) {
        for (NicIpAliasVO ipalias : revokedIpAliasVOs) {
            _nicIpAliasDao.expunge(ipalias.getId());
        }
    }

    protected void finalizeIpAssocForNetwork(Commands cmds, VirtualRouter router, Provider provider,
            Long guestNetworkId, Map<String, String> vlanMacAddress) {
        
        ArrayList<? extends PublicIpAddress> publicIps = getPublicIpsToApply(router, provider, guestNetworkId);
        
        if (publicIps != null && !publicIps.isEmpty()) {
            s_logger.debug("Found " + publicIps.size() + " ip(s) to apply as a part of domR " + router + " start.");
            // Re-apply public ip addresses - should come before PF/LB/VPN
            if (_networkModel.isProviderSupportServiceInNetwork(guestNetworkId, Service.Firewall, provider)) {
                createAssociateIPCommands(router, publicIps, cmds, 0);
            }
        }
    }

    protected ArrayList<? extends PublicIpAddress> getPublicIpsToApply(VirtualRouter router, Provider provider,
            Long guestNetworkId, com.cloud.network.IpAddress.State... skipInStates) {
        long ownerId = router.getAccountId();
        final List<? extends IpAddress> userIps;

        Network guestNetwork = _networkDao.findById(guestNetworkId);
        if (guestNetwork.getGuestType() == GuestType.Shared) {
            // ignore the account id for the shared network
            userIps = _networkModel.listPublicIpsAssignedToGuestNtwk(guestNetworkId, null);
        } else {
            userIps = _networkModel.listPublicIpsAssignedToGuestNtwk(ownerId, guestNetworkId, null);
        }

        List<PublicIp> allPublicIps = new ArrayList<PublicIp>();
        if (userIps != null && !userIps.isEmpty()) {
            boolean addIp = true;
            for (IpAddress userIp : userIps) {
                if (skipInStates != null) {
                    for (IpAddress.State stateToSkip : skipInStates) {
                        if (userIp.getState() == stateToSkip) {
                            s_logger.debug("Skipping ip address " + userIp + " in state " + userIp.getState());
                            addIp = false;
                            break;
                        }
                    }
                }
                
                if (addIp) {
                    IPAddressVO ipVO = _ipAddressDao.findById(userIp.getId());
                    PublicIp publicIp = PublicIp.createFromAddrAndVlan(ipVO, _vlanDao.findById(userIp.getVlanId()));
                    allPublicIps.add(publicIp);
                }
            }
        }
        
        //Get public Ips that should be handled by router
        Network network = _networkDao.findById(guestNetworkId);
        Map<PublicIpAddress, Set<Service>> ipToServices = _networkModel.getIpToServices(allPublicIps, false, true);
        Map<Provider, ArrayList<PublicIpAddress>> providerToIpList = _networkModel.getProviderToIpList(network, ipToServices);
        // Only cover virtual router for now, if ELB use it this need to be modified
      
        ArrayList<PublicIpAddress> publicIps = providerToIpList.get(provider);
        return publicIps;
    }

    @Override
    public boolean finalizeStart(VirtualMachineProfile profile, long hostId, Commands cmds,
            ReservationContext context) {
        DomainRouterVO router = _routerDao.findById(profile.getId());
        
        boolean result = true;

        Answer answer = cmds.getAnswer("checkSsh");
        if (answer != null && answer instanceof CheckSshAnswer) {
            CheckSshAnswer sshAnswer = (CheckSshAnswer) answer;
            if (sshAnswer == null || !sshAnswer.getResult()) {
                s_logger.warn("Unable to ssh to the VM: " + sshAnswer.getDetails());
                result = false;
            }
        } else {
            result = false;
        }
        if (result == false) {
            return result;
        }
        
        //Get guest networks info
        List<Network> guestNetworks = new ArrayList<Network>();
        
        List<? extends Nic> routerNics = _nicDao.listByVmId(profile.getId());
        for (Nic nic : routerNics) {
        	Network network = _networkModel.getNetwork(nic.getNetworkId());
            if (network.getTrafficType() == TrafficType.Guest) {
                guestNetworks.add(network);
                if (nic.getBroadcastUri().getScheme().equals("pvlan")) {
                	NicProfile nicProfile = new NicProfile(nic, network, nic.getBroadcastUri(), nic.getIsolationUri(), 0, false, "pvlan-nic");
                	result = setupDhcpForPvlan(true, router, router.getHostId(), nicProfile);
                }
            }
        }
        
        if (!result) {
        	return result;
        }
        
        answer = cmds.getAnswer("getDomRVersion");
        if (answer != null && answer instanceof GetDomRVersionAnswer) {
            GetDomRVersionAnswer versionAnswer = (GetDomRVersionAnswer)answer;
            if (answer == null || !answer.getResult()) {
                s_logger.warn("Unable to get the template/scripts version of router " + router.getInstanceName() +
                        " due to: " + versionAnswer.getDetails());
                result = false;
            } else {
                router.setTemplateVersion(versionAnswer.getTemplateVersion());
                router.setScriptsVersion(versionAnswer.getScriptsVersion());
                router = _routerDao.persist(router, guestNetworks);
            }
        } else {
            result = false;
        }

        return result;
    }

    @Override
    public void finalizeStop(VirtualMachineProfile profile, Answer answer) {
        if (answer != null) {
            VirtualMachine vm = profile.getVirtualMachine();
            DomainRouterVO domR = _routerDao.findById(vm.getId());
            processStopOrRebootAnswer(domR, answer);
            List<? extends Nic> routerNics = _nicDao.listByVmId(profile.getId());
            for (Nic nic : routerNics) {
            	Network network = _networkModel.getNetwork(nic.getNetworkId());
            	if (network.getTrafficType() == TrafficType.Guest && nic.getBroadcastUri() != null && nic.getBroadcastUri().getScheme().equals("pvlan")) {
                	NicProfile nicProfile = new NicProfile(nic, network, nic.getBroadcastUri(), nic.getIsolationUri(), 0, false, "pvlan-nic");
            		setupDhcpForPvlan(false, domR, domR.getHostId(), nicProfile);
            	}
            }

        }
    }

    @Override
    public void finalizeExpunge(VirtualMachine vm) {
    }


    @Override
    public boolean startRemoteAccessVpn(Network network, RemoteAccessVpn vpn, List<? extends VirtualRouter> routers)
            throws ResourceUnavailableException {
        if (routers == null || routers.isEmpty()) {
            s_logger.warn("Failed to start remote access VPN: no router found for account and zone");
            throw new ResourceUnavailableException("Failed to start remote access VPN: no router found for account and zone",
                    DataCenter.class, network.getDataCenterId());
        }

        for (VirtualRouter router : routers) {
            if (router.getState() != State.Running) {
                s_logger.warn("Failed to start remote access VPN: router not in right state " + router.getState());
                throw new ResourceUnavailableException("Failed to start remote access VPN: router not in right state "
                        + router.getState(), DataCenter.class, network.getDataCenterId());
            }

            Commands cmds = new Commands(Command.OnError.Stop);
            createApplyVpnCommands(true, vpn, router, cmds);

            try {
                _agentMgr.send(router.getHostId(), cmds);
            } catch (OperationTimedoutException e) {
                s_logger.debug("Failed to start remote access VPN: ", e);
                throw new AgentUnavailableException("Unable to send commands to virtual router ", router.getHostId(), e);
            }
            Answer answer = cmds.getAnswer("users");
            if (!answer.getResult()) {
                s_logger.error("Unable to start vpn: unable add users to vpn in zone " + router.getDataCenterId()
                        + " for account " + vpn.getAccountId() + " on domR: " + router.getInstanceName()
                        + " due to " + answer.getDetails());
                throw new ResourceUnavailableException("Unable to start vpn: Unable to add users to vpn in zone " +
                        router.getDataCenterId() + " for account " + vpn.getAccountId() + " on domR: "
                        + router.getInstanceName() + " due to " + answer.getDetails(), DataCenter.class, router.getDataCenterId());
            }
            answer = cmds.getAnswer("startVpn");
            if (!answer.getResult()) {
                s_logger.error("Unable to start vpn in zone " + router.getDataCenterId() + " for account " +
            vpn.getAccountId() + " on domR: " + router.getInstanceName() + " due to "
                        + answer.getDetails());
                throw new ResourceUnavailableException("Unable to start vpn in zone " + router.getDataCenterId()
                        + " for account " + vpn.getAccountId() + " on domR: " + router.getInstanceName()
                        + " due to " + answer.getDetails(), DataCenter.class, router.getDataCenterId());
            }

        }
        return true;
    }


    @Override
    public boolean deleteRemoteAccessVpn(Network network, RemoteAccessVpn vpn, List<? extends VirtualRouter> routers)
            throws ResourceUnavailableException {
        if (routers == null || routers.isEmpty()) {
            s_logger.warn("Failed to delete remote access VPN: no router found for account and zone");
            throw new ResourceUnavailableException("Failed to delete remote access VPN", DataCenter.class, network.getDataCenterId());
        }

        boolean result = true;
        for (VirtualRouter router : routers) {
            if (router.getState() == State.Running) {
                Commands cmds = new Commands(Command.OnError.Continue);
                createApplyVpnCommands(false, vpn, router, cmds);
                result = result && sendCommandsToRouter(router, cmds);
            } else if (router.getState() == State.Stopped) {
                s_logger.debug("Router " + router + " is in Stopped state, not sending deleteRemoteAccessVpn command to it");
                continue;
            } else {
                s_logger.warn("Failed to delete remote access VPN: domR " + router + " is not in right state " + router.getState());
                throw new ResourceUnavailableException("Failed to delete remote access VPN: domR is not in right state " +
                        router.getState(), DataCenter.class, network.getDataCenterId());
            }
        }

        return result;
    }


    private DomainRouterVO start(DomainRouterVO router, User user, Account caller, Map<Param, Object> params, DeploymentPlan planToDeploy)
            throws StorageUnavailableException, InsufficientCapacityException,
    ConcurrentOperationException, ResourceUnavailableException {
        s_logger.debug("Starting router " + router);
        try {
            _itMgr.advanceStart(router.getUuid(), params, planToDeploy, null);
        } catch (OperationTimedoutException e) {
            throw new ResourceUnavailableException("Starting router " + router + " failed! " + e.toString(), DataCenter.class, router.getDataCenterId());
        }
        if (router.isStopPending()) {
            s_logger.info("Clear the stop pending flag of router " + router.getHostName() + " after start router successfully!");
            router.setStopPending(false);
            router = _routerDao.persist(router);
        }
        // We don't want the failure of VPN Connection affect the status of router, so we try to make connection
        // only after router start successfully
        Long vpcId = router.getVpcId();
        if (vpcId != null) {
            _s2sVpnMgr.reconnectDisconnectedVpnByVpc(vpcId);
        }
        return _routerDao.findById(router.getId());
    }

    @Override
    public DomainRouterVO stop(VirtualRouter router, boolean forced, User user, Account caller) throws ConcurrentOperationException, ResourceUnavailableException {
        s_logger.debug("Stopping router " + router);
        try {
            _itMgr.advanceStop(router.getUuid(), forced);
            return _routerDao.findById(router.getId());
        } catch (OperationTimedoutException e) {
            throw new CloudRuntimeException("Unable to stop " + router, e);
        }
    }

    @Override
    public boolean configDhcpForSubnet(Network network, final NicProfile nic, VirtualMachineProfile profile, DeployDestination dest, List<DomainRouterVO> routers) throws ResourceUnavailableException {
        UserVmVO vm = _userVmDao.findById(profile.getId());
        _userVmDao.loadDetails(vm);

        final boolean isZoneBasic = (dest.getDataCenter().getNetworkType() == NetworkType.Basic);
        final Long podId = isZoneBasic ? dest.getPod().getId() : null;

        //Asuming we have only one router per network For Now.
        DomainRouterVO router = routers.get(0);
        if (router.getState() != State.Running) {
            s_logger.warn("Failed to configure dhcp: router not in running state");
            throw new ResourceUnavailableException("Unable to assign ip addresses, domR is not in right state " +
                    router.getState(), DataCenter.class, network.getDataCenterId());
        }
        //check if this is not the primary subnet.
        NicVO domr_guest_nic = _nicDao.findByInstanceIdAndIpAddressAndVmtype(router.getId(), _nicDao.getIpAddress(nic.getNetworkId(), router.getId()), VirtualMachine.Type.DomainRouter);
        //check if the router ip address and the vm ip address belong to same subnet.
        //if they do not belong to same netwoek check for the alias ips. if not create one.
        // This should happen only in case of Basic and Advanced SG enabled networks.
        if (!NetUtils.sameSubnet(domr_guest_nic.getIp4Address(), nic.getIp4Address(), nic.getNetmask())){
            List<NicIpAliasVO> aliasIps = _nicIpAliasDao.listByNetworkIdAndState(domr_guest_nic.getNetworkId(), NicIpAlias.state.active);
            boolean ipInVmsubnet =false;
            for (NicIpAliasVO alias : aliasIps) {
                //check if any of the alias ips belongs to the Vm's subnet.
                if (NetUtils.sameSubnet(alias.getIp4Address(),nic.getIp4Address(),nic.getNetmask())){
                    ipInVmsubnet = true;
                    break;
                }
            }
            PublicIp routerPublicIP = null;
            String routerAliasIp =null;
            DataCenter dc = _dcDao.findById(router.getDataCenterId());
            if (ipInVmsubnet == false) {
                try {
                    if (network.getTrafficType() == TrafficType.Guest && network.getGuestType() == GuestType.Shared) {
                        Pod pod = _podDao.findById(vm.getPodIdToDeployIn());
                        Account caller = CallContext.current().getCallingAccount();
                        List<VlanVO> vlanList = _vlanDao.listVlansByNetworkIdAndGateway(network.getId(), nic.getGateway());
                        List<Long>   vlanDbIdList = new ArrayList<Long>();
                        for (VlanVO vlan : vlanList) {
                            vlanDbIdList.add(vlan.getId());
                        }
                        if (dc.getNetworkType() == NetworkType.Basic) {
                            routerPublicIP = _ipAddrMgr.assignPublicIpAddressFromVlans(router.getDataCenterId(),
                                vm.getPodIdToDeployIn(),
                                caller,
                                Vlan.VlanType.DirectAttached,
                                vlanDbIdList,
                                nic.getNetworkId(),
                                null,
                                false);
                        }
                        else {
                            routerPublicIP = _ipAddrMgr.assignPublicIpAddressFromVlans(router.getDataCenterId(),
                                null,
                                caller,
                                Vlan.VlanType.DirectAttached,
                                vlanDbIdList,
                                nic.getNetworkId(),
                                null,
                                false);
                        }

                        routerAliasIp = routerPublicIP.getAddress().addr();
                    }
                }
                catch (InsufficientAddressCapacityException e){
                    s_logger.info(e.getMessage());
                    s_logger.info("unable to configure dhcp for this VM.");
                    return false;
                }
                //this means we did not create a ip alis on the router.
                NicIpAliasVO alias = new NicIpAliasVO(domr_guest_nic.getId(), routerAliasIp, router.getId(), CallContext.current().getCallingAccountId(), network.getDomainId(), nic.getNetworkId(),nic.getGateway(), nic.getNetmask());
                alias.setAliasCount((routerPublicIP.getIpMacAddress()));
                _nicIpAliasDao.persist(alias);
                List<IpAliasTO> ipaliasTo = new ArrayList<IpAliasTO>();
                ipaliasTo.add(new IpAliasTO(routerAliasIp, alias.getNetmask(), alias.getAliasCount().toString()));
                Commands cmds = new Commands(Command.OnError.Stop);
                createIpAlias(router, ipaliasTo, alias.getNetworkId(), cmds);
                //also add the required configuration to the dnsmasq for supporting dhcp and dns on the new ip.
                configDnsMasq(router, network, cmds);
                boolean result = sendCommandsToRouter(router, cmds);
                if (result == false) {
                    final NicIpAliasVO ipAliasVO = _nicIpAliasDao.findByInstanceIdAndNetworkId(network.getId(), router.getId());
                    final PublicIp routerPublicIPFinal = routerPublicIP; 
                    Transaction.execute(new TransactionCallbackNoReturn() {
                        @Override
                        public void doInTransactionWithoutResult(TransactionStatus status) {
                            _nicIpAliasDao.expunge(ipAliasVO.getId());
                            _ipAddressDao.unassignIpAddress(routerPublicIPFinal.getId());
                        }
                    });
                    throw new CloudRuntimeException("failed to configure ip alias on the router as a part of dhcp config");
                }
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean removeDhcpSupportForSubnet(Network network,  List<DomainRouterVO> routers) throws ResourceUnavailableException {
        if (routers == null || routers.isEmpty()) {
            s_logger.warn("Failed to add/remove VPN users: no router found for account and zone");
            throw new ResourceUnavailableException("Unable to assign ip addresses, domR doesn't exist for network " +
                    network.getId(), DataCenter.class, network.getDataCenterId());
        }

        for (DomainRouterVO router : routers) {
            if (router.getState() != State.Running) {
                s_logger.warn("Failed to add/remove VPN users: router not in running state");
                throw new ResourceUnavailableException("Unable to assign ip addresses, domR is not in right state " +
                        router.getState(), DataCenter.class, network.getDataCenterId());
            }

            Commands cmds = new Commands(Command.OnError.Continue);
            final List<NicIpAliasVO> revokedIpAliasVOs = _nicIpAliasDao.listByNetworkIdAndState(network.getId(), NicIpAlias.state.revoked);
            s_logger.debug("Found" + revokedIpAliasVOs.size() + "ip Aliases to revoke on the router as a part of dhcp configuration");
            List<IpAliasTO> revokedIpAliasTOs = new ArrayList<IpAliasTO>();
            for (NicIpAliasVO revokedAliasVO : revokedIpAliasVOs) {
                revokedIpAliasTOs.add(new IpAliasTO(revokedAliasVO.getIp4Address(), revokedAliasVO.getNetmask(), revokedAliasVO.getAliasCount().toString()));
            }
            List<NicIpAliasVO> aliasVOs = _nicIpAliasDao.listByNetworkIdAndState(network.getId(), NicIpAlias.state.active);
            s_logger.debug("Found" + aliasVOs.size() + "ip Aliases to apply on the router as a part of dhcp configuration");
            List<IpAliasTO> activeIpAliasTOs = new ArrayList<IpAliasTO>();
            for (NicIpAliasVO aliasVO : aliasVOs) {
                activeIpAliasTOs.add(new IpAliasTO(aliasVO.getIp4Address(), aliasVO.getNetmask(), aliasVO.getAliasCount().toString()));
            }
            createDeleteIpAliasCommand(router, revokedIpAliasTOs, activeIpAliasTOs, network.getId(), cmds);
            configDnsMasq(router, network, cmds);
            boolean result = sendCommandsToRouter(router, cmds);
            if (result) {
                Transaction.execute(new TransactionCallbackNoReturn() {
                    @Override
                    public void doInTransactionWithoutResult(TransactionStatus status) {
                        for (NicIpAliasVO revokedAliasVO : revokedIpAliasVOs) {
                            _nicIpAliasDao.expunge(revokedAliasVO.getId());
                        }
                    }
                });
                return true;
            }
        }
        return  false;
    }


    @Override
    public boolean applyDhcpEntry(Network network, final NicProfile nic, VirtualMachineProfile profile,
            DeployDestination dest, List<DomainRouterVO> routers)
            throws ResourceUnavailableException {
        if(s_logger.isTraceEnabled()) {
            s_logger.trace("applyDhcpEntry(" + network.getCidr() + ", " + nic.getMacAddress() + ", " + profile.getUuid() + ", " + dest.getHost() + ", " + routers + ")");
        }
        final UserVmVO vm = _userVmDao.findById(profile.getId());
        _userVmDao.loadDetails(vm);
        
        final VirtualMachineProfile updatedProfile = profile;
        final boolean isZoneBasic = (dest.getDataCenter().getNetworkType() == NetworkType.Basic);
        final Long podId = isZoneBasic ? dest.getPod().getId() : null;
        
        boolean podLevelException = false;
        //for user vm in Basic zone we should try to re-deploy vm in a diff pod if it fails to deploy in original pod; so throwing exception with Pod scope
        if (isZoneBasic && podId != null && updatedProfile.getVirtualMachine().getType() == VirtualMachine.Type.User
                && network.getTrafficType() == TrafficType.Guest && network.getGuestType() == Network.GuestType.Shared) {
            podLevelException = true;
        }
        
        return applyRules(network, routers, "dhcp entry", podLevelException, podId, true, new RuleApplier() {
            @Override
            public boolean execute(Network network, VirtualRouter router) throws ResourceUnavailableException {
                //for basic zone, send dhcp/dns information to all routers in the basic network only when _dnsBasicZoneUpdates is set to "all" value
                Commands cmds = new Commands(Command.OnError.Stop);
                if (!(isZoneBasic && router.getPodIdToDeployIn().longValue() != podId.longValue() && _dnsBasicZoneUpdates.equalsIgnoreCase("pod"))) {
                    NicVO nicVo = _nicDao.findById(nic.getId());
                    createDhcpEntryCommand(router, vm, nicVo, cmds);
                    return sendCommandsToRouter(router, cmds);
                }
                return true;
            }
        });
    }

    private void createDeleteIpAliasCommand(DomainRouterVO router, List<IpAliasTO> deleteIpAliasTOs, List<IpAliasTO> createIpAliasTos, long networkId, Commands cmds) {
        String routerip = getRouterIpInNetwork(networkId, router.getId());
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        DeleteIpAliasCommand deleteIpaliasCmd = new DeleteIpAliasCommand(routerip, deleteIpAliasTOs, createIpAliasTos);
        deleteIpaliasCmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
        deleteIpaliasCmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
        deleteIpaliasCmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP,routerip);
        deleteIpaliasCmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());

        cmds.addCommand("deleteIpalias", deleteIpaliasCmd);
    }

    private NicVO findDefaultDnsIp(long userVmId) {
        NicVO defaultNic = _nicDao.findDefaultNicForVM(userVmId);
        
        //check if DNS provider is the domR
        if (!_networkModel.isProviderSupportServiceInNetwork(defaultNic.getNetworkId(), Service.Dns, Provider.VirtualRouter)) {
            return null;
        }
        
        NetworkOffering offering = _networkOfferingDao.findById(_networkDao.findById(defaultNic.getNetworkId()).getNetworkOfferingId());
        if (offering.getRedundantRouter()) {
            return findGatewayIp(userVmId);
        }
        
        DataCenter dc = _dcDao.findById(_networkModel.getNetwork(defaultNic.getNetworkId()).getDataCenterId());
        boolean isZoneBasic = (dc.getNetworkType() == NetworkType.Basic);
        
        //find domR's nic in the network
        NicVO domrDefaultNic;
        if (isZoneBasic){
            domrDefaultNic = _nicDao.findByNetworkIdTypeAndGateway(defaultNic.getNetworkId(), VirtualMachine.Type.DomainRouter, defaultNic.getGateway());
        } else{
            domrDefaultNic = _nicDao.findByNetworkIdAndType(defaultNic.getNetworkId(), VirtualMachine.Type.DomainRouter);
        }
        return domrDefaultNic;
    }

    private NicVO findGatewayIp(long userVmId) {
        NicVO defaultNic = _nicDao.findDefaultNicForVM(userVmId);
        return defaultNic;
     }

    @Override
    public boolean applyUserData(Network network, final NicProfile nic, VirtualMachineProfile profile, DeployDestination dest, List<DomainRouterVO> routers)
            throws ResourceUnavailableException {
        final UserVmVO vm = _userVmDao.findById(profile.getId());
        _userVmDao.loadDetails(vm);
        
        final VirtualMachineProfile updatedProfile = profile;
        final boolean isZoneBasic = (dest.getDataCenter().getNetworkType() == NetworkType.Basic);
        final Long podId = isZoneBasic ? dest.getPod().getId() : null;
        
        boolean podLevelException = false;
        //for user vm in Basic zone we should try to re-deploy vm in a diff pod if it fails to deploy in original pod; so throwing exception with Pod scope
        if (isZoneBasic && podId != null && updatedProfile.getVirtualMachine().getType() == VirtualMachine.Type.User
                && network.getTrafficType() == TrafficType.Guest && network.getGuestType() == Network.GuestType.Shared) {
            podLevelException = true;
        }
        
        return applyRules(network, routers, "userdata and password entry", podLevelException, podId, false, new RuleApplier() {
            @Override
            public boolean execute(Network network, VirtualRouter router) throws ResourceUnavailableException {
                //for basic zone, send vm data/password information only to the router in the same pod
                Commands cmds = new Commands(Command.OnError.Stop);
                if (!(isZoneBasic && router.getPodIdToDeployIn().longValue() != podId.longValue())) {
                    NicVO nicVo = _nicDao.findById(nic.getId());
                    createPasswordCommand(router, updatedProfile, nicVo, cmds);
                    createVmDataCommand(router, vm, nicVo, vm.getDetail("SSH.PublicKey"), cmds);
                    return sendCommandsToRouter(router, cmds);
                }
                return true;
            }
        });
    }

    protected void createApplyVpnUsersCommand(List<? extends VpnUser> users, VirtualRouter router, Commands cmds)
    {
    	List<VpnUser> addUsers = new ArrayList<VpnUser>();
    	List<VpnUser> removeUsers = new ArrayList<VpnUser>();
    	for (VpnUser user : users) {
    		if (user.getState() == VpnUser.State.Add || user.getState() == VpnUser.State.Active) {
    			addUsers.add(user);
    		} else if (user.getState() == VpnUser.State.Revoke) {
    			removeUsers.add(user);
    		}
    	}

    	VpnUsersCfgCommand cmd = new VpnUsersCfgCommand(addUsers, removeUsers);
    	cmd.setAccessDetail(NetworkElementCommand.ACCOUNT_ID, String.valueOf(router.getAccountId()));
    	cmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
    	cmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
    	DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
    	cmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());
    	
    	cmds.addCommand("users", cmd);
    }
    
    @Override
    //FIXME add partial success and STOP state support
    public String[] applyVpnUsers(Network network, List<? extends VpnUser> users, List<DomainRouterVO> routers) throws ResourceUnavailableException {
        if (routers == null || routers.isEmpty()) {
            s_logger.warn("Failed to add/remove VPN users: no router found for account and zone");
            throw new ResourceUnavailableException("Unable to assign ip addresses, domR doesn't exist for network " +
            network.getId(), DataCenter.class, network.getDataCenterId());
        }

        boolean agentResults = true;

        for (DomainRouterVO router : routers) {
            if (router.getState() != State.Running) {
                s_logger.warn("Failed to add/remove VPN users: router not in running state");
                throw new ResourceUnavailableException("Unable to assign ip addresses, domR is not in right state " +
                        router.getState(), DataCenter.class, network.getDataCenterId());
            }

            Commands cmds = new Commands(Command.OnError.Continue);
            createApplyVpnUsersCommand(users, router, cmds);

            // Currently we receive just one answer from the agent. In the future we have to parse individual answers and set
            // results accordingly
            boolean agentResult = sendCommandsToRouter(router, cmds);
            agentResults = agentResults && agentResult;
        }

        String[] result = new String[users.size()];
        for (int i = 0; i < result.length; i++) {
            if (agentResults) {
                result[i] = null;
            } else {
                result[i] = String.valueOf(agentResults);
            }
        }

        return result;
    }

    @Override @ActionEvent(eventType = EventTypes.EVENT_ROUTER_START, eventDescription = "starting router Vm", async = true)
    public VirtualRouter startRouter(long id) throws ResourceUnavailableException, InsufficientCapacityException, ConcurrentOperationException{
        return startRouter(id, true);
    }

    @Override
    public VirtualRouter startRouter(long routerId, boolean reprogramNetwork) throws ResourceUnavailableException,
    InsufficientCapacityException, ConcurrentOperationException {
        Account caller = CallContext.current().getCallingAccount();
        User callerUser = _accountMgr.getActiveUser(CallContext.current().getCallingUserId());

        // verify parameters
        DomainRouterVO router = _routerDao.findById(routerId);
        if (router == null) {
            throw new InvalidParameterValueException("Unable to find router by id " + routerId + ".");
        }
        _accountMgr.checkAccess(caller, null, true, router);

        Account owner = _accountMgr.getAccount(router.getAccountId());

        // Check if all networks are implemented for the domR; if not - implement them
        DataCenter dc = _dcDao.findById(router.getDataCenterId());
        HostPodVO pod = null;
        if (router.getPodIdToDeployIn() != null) {
            pod = _podDao.findById(router.getPodIdToDeployIn());
        }
        DeployDestination dest = new DeployDestination(dc, pod, null, null);

        ReservationContext context = new ReservationContextImpl(null, null, callerUser, owner);

        List<NicVO> nics = _nicDao.listByVmId(routerId);

        for (NicVO nic : nics) {
            if (!_networkMgr.startNetwork(nic.getNetworkId(), dest, context)) {
                s_logger.warn("Failed to start network id=" + nic.getNetworkId() + " as a part of domR start");
                throw new CloudRuntimeException("Failed to start network id=" + nic.getNetworkId() + " as a part of domR start");
            }
        }

        //After start network, check if it's already running
        router = _routerDao.findById(routerId);
        if (router.getState() == State.Running) {
            return router;
        }

        UserVO user = _userDao.findById(CallContext.current().getCallingUserId());
        Map<Param, Object> params = new HashMap<Param, Object>();
        if (reprogramNetwork) {
            params.put(Param.ReProgramGuestNetworks, true);
        } else {
            params.put(Param.ReProgramGuestNetworks, false);
        }
        VirtualRouter virtualRouter = startVirtualRouter(router, user, caller, params);
        if(virtualRouter == null){
            throw new CloudRuntimeException("Failed to start router with id " + routerId);
        }
        return virtualRouter;
    }

    private void createAssociateIPCommands(final VirtualRouter router, final List<? extends PublicIpAddress> ips, Commands cmds, long vmId) {

        // Ensure that in multiple vlans case we first send all ip addresses of vlan1, then all ip addresses of vlan2, etc..
        Map<String, ArrayList<PublicIpAddress>> vlanIpMap = new HashMap<String, ArrayList<PublicIpAddress>>();
        for (final PublicIpAddress ipAddress : ips) {
            String vlanTag = ipAddress.getVlanTag();
            ArrayList<PublicIpAddress> ipList = vlanIpMap.get(vlanTag);
            if (ipList == null) {
                ipList = new ArrayList<PublicIpAddress>();
            }
            //domR doesn't support release for sourceNat IP address; so reset the state
            if (ipAddress.isSourceNat() && ipAddress.getState() == IpAddress.State.Releasing) {
                ipAddress.setState(IpAddress.State.Allocated);
            }
            ipList.add(ipAddress);
            vlanIpMap.put(vlanTag, ipList);
        }

        List<NicVO> nics = _nicDao.listByVmId(router.getId());
        String baseMac = null;
        for (NicVO nic : nics) {
        	NetworkVO nw = _networkDao.findById(nic.getNetworkId());
        	if (nw.getTrafficType() == TrafficType.Public) {
        		baseMac = nic.getMacAddress();
        		break;
        	}
        }

        for (Map.Entry<String, ArrayList<PublicIpAddress>> vlanAndIp : vlanIpMap.entrySet()) {
            List<PublicIpAddress> ipAddrList = vlanAndIp.getValue();
            // Source nat ip address should always be sent first
            Collections.sort(ipAddrList, new Comparator<PublicIpAddress>() {
                @Override
                public int compare(PublicIpAddress o1, PublicIpAddress o2) {
                    boolean s1 = o1.isSourceNat();
                    boolean s2 = o2.isSourceNat();
                    return (s1 ^ s2) ? ((s1 ^ true) ? 1 : -1) : 0;
                }
            });

            // Get network rate - required for IpAssoc
            Integer networkRate = _networkModel.getNetworkRate(ipAddrList.get(0).getNetworkId(), router.getId());
            Network network = _networkModel.getNetwork(ipAddrList.get(0).getNetworkId());

            IpAddressTO[] ipsToSend = new IpAddressTO[ipAddrList.size()];
            int i = 0;
            boolean firstIP = true;

            for (final PublicIpAddress ipAddr : ipAddrList) {

                boolean add = (ipAddr.getState() == IpAddress.State.Releasing ? false : true);
                boolean sourceNat = ipAddr.isSourceNat();
                /* enable sourceNAT for the first ip of the public interface */
                if (firstIP) {
                    sourceNat = true;
                }
                String vlanId = ipAddr.getVlanTag();
                String vlanGateway = ipAddr.getGateway();
                String vlanNetmask = ipAddr.getNetmask();
                String vifMacAddress = null;
                // For non-source nat IP, set the mac to be something based on first public nic's MAC
                // We cannot depends on first ip because we need to deal with first ip of other nics
                if (!ipAddr.isSourceNat() && ipAddr.getVlanId() != 0) {
                	vifMacAddress = NetUtils.generateMacOnIncrease(baseMac, ipAddr.getVlanId());
                } else {
                	vifMacAddress = ipAddr.getMacAddress();
                }

                IpAddressTO ip = new IpAddressTO(ipAddr.getAccountId(), ipAddr.getAddress().addr(), add, firstIP,
                        sourceNat, vlanId, vlanGateway, vlanNetmask, vifMacAddress, networkRate, ipAddr.isOneToOneNat());

                ip.setTrafficType(network.getTrafficType());
                ip.setNetworkName(_networkModel.getNetworkTag(router.getHypervisorType(), network));
                ipsToSend[i++] = ip;
                /* send the firstIP = true for the first Add, this is to create primary on interface*/
                if (!firstIP || add)  {
                    firstIP = false;
                }
            }
            IpAssocCommand cmd = new IpAssocCommand(ipsToSend);
            cmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
            cmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, getRouterIpInNetwork(ipAddrList.get(0).getAssociatedWithNetworkId(), router.getId()));
            cmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
            DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
            cmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());

            cmds.addCommand("IPAssocCommand", cmd);
        }
    }

    private void createApplyPortForwardingRulesCommands(List<? extends PortForwardingRule> rules, VirtualRouter router, Commands cmds, long guestNetworkId) {
        List<PortForwardingRuleTO> rulesTO = null;
        if (rules != null) {
            rulesTO = new ArrayList<PortForwardingRuleTO>();
            for (PortForwardingRule rule : rules) {
                IpAddress sourceIp = _networkModel.getIp(rule.getSourceIpAddressId());
                PortForwardingRuleTO ruleTO = new PortForwardingRuleTO(rule, null, sourceIp.getAddress().addr());
                rulesTO.add(ruleTO);
            }
        }

        SetPortForwardingRulesCommand cmd = null;
        
        if (router.getVpcId() != null) {
            cmd = new SetPortForwardingRulesVpcCommand(rulesTO);
        } else {
            cmd = new SetPortForwardingRulesCommand(rulesTO);
        }
        
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, getRouterIpInNetwork(guestNetworkId, router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        cmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());

        cmds.addCommand(cmd);
    }

    private void createApplyStaticNatRulesCommands(List<? extends StaticNatRule> rules, VirtualRouter router, Commands cmds, long guestNetworkId) {
        List<StaticNatRuleTO> rulesTO = null;
        if (rules != null) {
            rulesTO = new ArrayList<StaticNatRuleTO>();
            for (StaticNatRule rule : rules) {
                IpAddress sourceIp = _networkModel.getIp(rule.getSourceIpAddressId());
                StaticNatRuleTO ruleTO = new StaticNatRuleTO(rule, null, sourceIp.getAddress().addr(), rule.getDestIpAddress());
                rulesTO.add(ruleTO);
            }
        }

        SetStaticNatRulesCommand cmd = new SetStaticNatRulesCommand(rulesTO, router.getVpcId());
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, getRouterIpInNetwork(guestNetworkId, router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        cmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());
        cmds.addCommand(cmd);
    }

    private void createApplyLoadBalancingRulesCommands(List<LoadBalancingRule> rules, VirtualRouter router, Commands cmds, long guestNetworkId) {

        LoadBalancerTO[] lbs = new LoadBalancerTO[rules.size()];
        int i = 0;
        // We don't support VR to be inline currently
        boolean inline = false;
        for (LoadBalancingRule rule : rules) {
            boolean revoked = (rule.getState().equals(FirewallRule.State.Revoke));
            String protocol = rule.getProtocol();
            String algorithm = rule.getAlgorithm();
            String uuid = rule.getUuid();

            String srcIp = rule.getSourceIp().addr();
            int srcPort = rule.getSourcePortStart();
            List<LbDestination> destinations = rule.getDestinations();
            List<LbStickinessPolicy> stickinessPolicies = rule.getStickinessPolicies();
            LoadBalancerTO lb = new LoadBalancerTO(uuid, srcIp, srcPort, protocol, algorithm, revoked, false, inline, destinations, stickinessPolicies);
            lbs[i++] = lb;
        }
        String routerPublicIp = null;

        if (router instanceof DomainRouterVO) {
            DomainRouterVO domr = _routerDao.findById(router.getId());
            routerPublicIp = domr.getPublicIpAddress();
        }
        
        Network guestNetwork = _networkModel.getNetwork(guestNetworkId);
        Nic nic = _nicDao.findByNtwkIdAndInstanceId(guestNetwork.getId(), router.getId());
        NicProfile nicProfile = new NicProfile(nic, guestNetwork, nic.getBroadcastUri(), nic.getIsolationUri(),
                _networkModel.getNetworkRate(guestNetwork.getId(), router.getId()),
                _networkModel.isSecurityGroupSupportedInNetwork(guestNetwork),
                _networkModel.getNetworkTag(router.getHypervisorType(), guestNetwork));
        NetworkOffering offering =_networkOfferingDao.findById(guestNetwork.getNetworkOfferingId());
        String maxconn= null;
        if (offering.getConcurrentConnections() == null) {
            maxconn =  _configDao.getValue(Config.NetworkLBHaproxyMaxConn.key());
        }
        else {
            maxconn = offering.getConcurrentConnections().toString();
        }

        LoadBalancerConfigCommand cmd = new LoadBalancerConfigCommand(lbs, routerPublicIp,
                getRouterIpInNetwork(guestNetworkId, router.getId()), router.getPrivateIpAddress(),
                _itMgr.toNicTO(nicProfile, router.getHypervisorType()), router.getVpcId(), maxconn, offering.isKeepAliveEnabled());

        cmd.lbStatsVisibility = _configDao.getValue(Config.NetworkLBHaproxyStatsVisbility.key());
        cmd.lbStatsUri = _configDao.getValue(Config.NetworkLBHaproxyStatsUri.key());
        cmd.lbStatsAuth = _configDao.getValue(Config.NetworkLBHaproxyStatsAuth.key());
        cmd.lbStatsPort = _configDao.getValue(Config.NetworkLBHaproxyStatsPort.key());


        cmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, getRouterIpInNetwork(guestNetworkId, router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        cmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());
        cmds.addCommand(cmd);

    }

    protected String getVpnCidr(RemoteAccessVpn vpn)
    {
    	Network network = _networkDao.findById(vpn.getNetworkId());
    	return network.getCidr();
    }
    
    protected void createApplyVpnCommands(boolean isCreate, RemoteAccessVpn vpn, VirtualRouter router, Commands cmds) {
        List<VpnUserVO> vpnUsers = _vpnUsersDao.listByAccount(vpn.getAccountId());

        createApplyVpnUsersCommand(vpnUsers, router, cmds);

        IpAddress ip = _networkModel.getIp(vpn.getServerAddressId());

        String cidr = getVpnCidr(vpn);
        RemoteAccessVpnCfgCommand startVpnCmd = new RemoteAccessVpnCfgCommand(isCreate, ip.getAddress().addr(),
                vpn.getLocalIp(), vpn.getIpRange(), vpn.getIpsecPresharedKey(), (vpn.getVpcId() != null));
        startVpnCmd.setLocalCidr(cidr);
        startVpnCmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
        startVpnCmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        startVpnCmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());

        cmds.addCommand("startVpn", startVpnCmd);
    }
    
    private void createPasswordCommand(VirtualRouter router, VirtualMachineProfile profile, NicVO nic, Commands cmds) {
        String password = (String) profile.getParameter(VirtualMachineProfile.Param.VmPassword);
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());

        // password should be set only on default network element
        if (password != null && nic.isDefaultNic()) {
            final String encodedPassword = PasswordGenerator.rot13(password);
            SavePasswordCommand cmd = new SavePasswordCommand(encodedPassword, nic.getIp4Address(), profile.getVirtualMachine().getHostName(), _networkModel.getExecuteInSeqNtwkElmtCmd());
            cmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
            cmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, getRouterIpInNetwork(nic.getNetworkId(), router.getId()));
            cmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
            cmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());

            cmds.addCommand("password", cmd);
        }
        
    }
    
    private void createVmDataCommand(VirtualRouter router, UserVm vm, NicVO nic, String publicKey, Commands cmds) {
        String serviceOffering = _serviceOfferingDao.findByIdIncludingRemoved(vm.getId(), vm.getServiceOfferingId()).getDisplayText();
        String zoneName = _dcDao.findById(router.getDataCenterId()).getName();
        cmds.addCommand("vmdata",
                generateVmDataCommand(router, nic.getIp4Address(), vm.getUserData(), serviceOffering, zoneName, nic.getIp4Address(),
                        vm.getHostName(), vm.getInstanceName(), vm.getId(), vm.getUuid(), publicKey, nic.getNetworkId()));
        
    }

    private void createVmDataCommandForVMs(DomainRouterVO router, Commands cmds, long guestNetworkId) {
        List<UserVmVO> vms = _userVmDao.listByNetworkIdAndStates(guestNetworkId, State.Running, State.Migrating, State.Stopping);
        DataCenterVO dc = _dcDao.findById(router.getDataCenterId());
        for (UserVmVO vm : vms) {
            boolean createVmData = true;
            if (dc.getNetworkType() == NetworkType.Basic && router.getPodIdToDeployIn().longValue() != vm.getPodIdToDeployIn().longValue()) {
                createVmData = false;
            }

            if (createVmData) {
                NicVO nic = _nicDao.findByNtwkIdAndInstanceId(guestNetworkId, vm.getId());
                if (nic != null) {
                    s_logger.debug("Creating user data entry for vm " + vm + " on domR " + router);
                    createVmDataCommand(router, vm, nic, null, cmds);
                }
            }
        }
    }
    
    private void createDhcpEntryCommand(VirtualRouter router, UserVm vm, NicVO nic, Commands cmds) {
        DhcpEntryCommand dhcpCommand = new DhcpEntryCommand(nic.getMacAddress(), nic.getIp4Address(), vm.getHostName(), nic.getIp6Address(), _networkModel.getExecuteInSeqNtwkElmtCmd());
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        Nic defaultNic = findGatewayIp(vm.getId());
        String gatewayIp = defaultNic.getGateway();
        if (gatewayIp != null && !gatewayIp.equals(nic.getGateway())) {
            gatewayIp = "0.0.0.0";
        }
        dhcpCommand.setDefaultRouter(gatewayIp);
        dhcpCommand.setIp6Gateway(nic.getIp6Gateway());
        String ipaddress=null;
        NicVO domrDefaultNic =  findDefaultDnsIp(vm.getId());
        if (domrDefaultNic != null){
            ipaddress  = domrDefaultNic.getIp4Address();
        }
        dhcpCommand.setDefaultDns(ipaddress);
        dhcpCommand.setDuid(NetUtils.getDuidLL(nic.getMacAddress()));
        dhcpCommand.setDefault(nic.isDefaultNic());

        dhcpCommand.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
        dhcpCommand.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
        dhcpCommand.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, getRouterIpInNetwork(nic.getNetworkId(), router.getId()));
        dhcpCommand.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());

        cmds.addCommand("dhcp", dhcpCommand);
    }

    private void configDnsMasq(VirtualRouter router, Network network, Commands cmds) {
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        List<NicIpAliasVO> ipAliasVOList = _nicIpAliasDao.listByNetworkIdAndState(network.getId(), NicIpAlias.state.active);
        List<DhcpTO> ipList = new ArrayList<DhcpTO>();

        NicVO router_guest_nic = _nicDao.findByNtwkIdAndInstanceId(network.getId(), router.getId());
        String cidr = NetUtils.getCidrFromGatewayAndNetmask(router_guest_nic.getGateway(), router_guest_nic.getNetmask());
        String[] cidrPair = cidr.split("\\/");
        String cidrAddress = cidrPair[0];
        long cidrSize = Long.parseLong(cidrPair[1]);
        String startIpOfSubnet = NetUtils.getIpRangeStartIpFromCidr(cidrAddress, cidrSize);

        ipList.add(new DhcpTO(router_guest_nic.getIp4Address(),router_guest_nic.getGateway(),router_guest_nic.getNetmask(), startIpOfSubnet));
        for (NicIpAliasVO ipAliasVO : ipAliasVOList) {
             DhcpTO DhcpTO = new DhcpTO(ipAliasVO.getIp4Address(), ipAliasVO.getGateway(), ipAliasVO.getNetmask(), ipAliasVO.getStartIpOfSubnet());
             if (s_logger.isTraceEnabled()) {
                 s_logger.trace("configDnsMasq : adding ip {" + DhcpTO.getGateway() + ", " + DhcpTO.getNetmask() + ", " + DhcpTO.getRouterIp() + ", " + DhcpTO.getStartIpOfSubnet() + "}");
             }
             ipList.add(DhcpTO);
             ipAliasVO.setVmId(router.getId());
        }
        DataCenterVO dcvo = _dcDao.findById(router.getDataCenterId());
        DnsMasqConfigCommand dnsMasqConfigCmd = new DnsMasqConfigCommand(ipList);
        dnsMasqConfigCmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
        dnsMasqConfigCmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
        dnsMasqConfigCmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, getRouterIpInNetwork(network.getId(), router.getId()));
        dnsMasqConfigCmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());
        cmds.addCommand("dnsMasqConfig" ,dnsMasqConfigCmd);
    }


    private void createIpAlias(VirtualRouter router, List<IpAliasTO> ipAliasTOs, Long networkid, Commands cmds) {

        String routerip = getRouterIpInNetwork(networkid, router.getId());
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        CreateIpAliasCommand ipaliasCmd = new CreateIpAliasCommand(routerip, ipAliasTOs);
        ipaliasCmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
        ipaliasCmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
        ipaliasCmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP,routerip);
        ipaliasCmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());

        cmds.addCommand("ipalias", ipaliasCmd);
    }

    private void createDhcpEntryCommandsForVMs(DomainRouterVO router, Commands cmds, long guestNetworkId) {
        List<UserVmVO> vms = _userVmDao.listByNetworkIdAndStates(guestNetworkId, State.Running, State.Migrating, State.Stopping);
        DataCenterVO dc = _dcDao.findById(router.getDataCenterId());
        for (UserVmVO vm : vms) {
            boolean createDhcp = true;
            if (dc.getNetworkType() == NetworkType.Basic && router.getPodIdToDeployIn().longValue() != vm.getPodIdToDeployIn().longValue()
                    && _dnsBasicZoneUpdates.equalsIgnoreCase("pod")) {
                createDhcp = false;
            }
            if (createDhcp) {
                NicVO nic = _nicDao.findByNtwkIdAndInstanceId(guestNetworkId, vm.getId());
                if (nic != null) {
                    s_logger.debug("Creating dhcp entry for vm " + vm + " on domR " + router + ".");
                    createDhcpEntryCommand(router, vm, nic, cmds);
                }
            }
        }
    }

    protected boolean sendCommandsToRouter(final VirtualRouter router, Commands cmds) throws AgentUnavailableException {
        if(!checkRouterVersion(router)){
            s_logger.debug("Router requires upgrade. Unable to send command to router:" + router.getId());
            throw new CloudRuntimeException("Unable to send command. Upgrade in progress. Please contact administrator.");
        }
        Answer[] answers = null;
        try {
            answers = _agentMgr.send(router.getHostId(), cmds);
        } catch (OperationTimedoutException e) {
            s_logger.warn("Timed Out", e);
            throw new AgentUnavailableException("Unable to send commands to virtual router ", router.getHostId(), e);
        }

        if (answers == null) {
            return false;
        }

        if (answers.length != cmds.size()) {
            return false;
        }

        // FIXME: Have to return state for individual command in the future
        boolean result = true;
        if (answers.length > 0) {
            for (Answer answer : answers) {
                if (!answer.getResult()) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    protected void handleSingleWorkingRedundantRouter(List<? extends VirtualRouter> connectedRouters, List<? extends VirtualRouter> disconnectedRouters, String reason) throws ResourceUnavailableException
    {
        if (connectedRouters.isEmpty() || disconnectedRouters.isEmpty()) {
            return;
        }
        if (connectedRouters.size() != 1 || disconnectedRouters.size() != 1) {
            s_logger.warn("How many redundant routers do we have?? ");
            return;
        }
        if (!connectedRouters.get(0).getIsRedundantRouter()) {
            throw new ResourceUnavailableException("Who is calling this with non-redundant router or non-domain router?",
                    DataCenter.class, connectedRouters.get(0).getDataCenterId());
        }
        if (!disconnectedRouters.get(0).getIsRedundantRouter()) {
            throw new ResourceUnavailableException("Who is calling this with non-redundant router or non-domain router?",
                    DataCenter.class, disconnectedRouters.get(0).getDataCenterId());
        }

        DomainRouterVO connectedRouter = (DomainRouterVO)connectedRouters.get(0);
        DomainRouterVO disconnectedRouter = (DomainRouterVO)disconnectedRouters.get(0);

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("About to stop the router " + disconnectedRouter.getInstanceName() + " due to: " + reason);
        }
        String title = "Virtual router " + disconnectedRouter.getInstanceName() + " would be stopped after connecting back, due to " + reason;
        String context =  "Virtual router (name: " + disconnectedRouter.getInstanceName() + ", id: " + disconnectedRouter.getId() + ") would be stopped after connecting back, due to: " + reason;
        _alertMgr.sendAlert(AlertManager.AlertType.ALERT_TYPE_DOMAIN_ROUTER,
                disconnectedRouter.getDataCenterId(), disconnectedRouter.getPodIdToDeployIn(), title, context);
        disconnectedRouter.setStopPending(true);
        disconnectedRouter = _routerDao.persist(disconnectedRouter);

        int connRouterPR = getRealPriority(connectedRouter);
        int disconnRouterPR = getRealPriority(disconnectedRouter);
        if (connRouterPR < disconnRouterPR) {
            //connRouterPR < disconnRouterPR, they won't equal at anytime
            if (!connectedRouter.getIsPriorityBumpUp()) {
                final BumpUpPriorityCommand command = new BumpUpPriorityCommand();
                command.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(connectedRouter.getId()));
                command.setAccessDetail(NetworkElementCommand.ROUTER_NAME, connectedRouter.getInstanceName());
                final Answer answer = _agentMgr.easySend(connectedRouter.getHostId(), command);
                if (!answer.getResult()) {
                    s_logger.error("Failed to bump up " + connectedRouter.getInstanceName() + "'s priority! " + answer.getDetails());
                }
            } else {
                String t = "Can't bump up virtual router " + connectedRouter.getInstanceName() + "'s priority due to it's already bumped up!";
                _alertMgr.sendAlert(AlertManager.AlertType.ALERT_TYPE_DOMAIN_ROUTER,
                        connectedRouter.getDataCenterId(), connectedRouter.getPodIdToDeployIn(), t, t);
            }
        }
    }

    @Override
    public boolean associatePublicIP(Network network, final List<? extends PublicIpAddress> ipAddress, List<? extends VirtualRouter> routers)
            throws ResourceUnavailableException {
        if (ipAddress == null || ipAddress.isEmpty()) {
            s_logger.debug("No ip association rules to be applied for network " + network.getId());
            return true;
        }
        return applyRules(network, routers, "ip association", false, null, false, new RuleApplier() {
            @Override
            public boolean execute(Network network, VirtualRouter router) throws ResourceUnavailableException {
                Commands cmds = new Commands(Command.OnError.Continue);
                createAssociateIPCommands(router, ipAddress, cmds, 0);
                return sendCommandsToRouter(router, cmds);
            }
        });
    }

    @Override
    public boolean applyFirewallRules(Network network, final List<? extends FirewallRule> rules, List<? extends VirtualRouter> routers) throws ResourceUnavailableException {
        if (rules == null || rules.isEmpty()) {
            s_logger.debug("No firewall rules to be applied for network " + network.getId());
            return true;
        }
        return applyRules(network, routers, "firewall rules", false, null, false, new RuleApplier() {
            @Override
            public boolean execute(Network network, VirtualRouter router) throws ResourceUnavailableException {
                if (rules.get(0).getPurpose() == Purpose.LoadBalancing) {
                    // for load balancer we have to resend all lb rules for the network
                    List<LoadBalancerVO> lbs = _loadBalancerDao.listByNetworkIdAndScheme(network.getId(), Scheme.Public);
                    List<LoadBalancingRule> lbRules = new ArrayList<LoadBalancingRule>();
                    for (LoadBalancerVO lb : lbs) {
                        List<LbDestination> dstList = _lbMgr.getExistingDestinations(lb.getId());
                        List<LbStickinessPolicy> policyList = _lbMgr.getStickinessPolicies(lb.getId());
                        List<LbHealthCheckPolicy> hcPolicyList = _lbMgr.getHealthCheckPolicies(lb.getId());
                        LbSslCert sslCert = _lbMgr.getLbSslCert(lb.getId());
                        Ip sourceIp = _networkModel.getPublicIpAddress(lb.getSourceIpAddressId()).getAddress();
                        LoadBalancingRule loadBalancing = new LoadBalancingRule(lb, dstList, policyList, hcPolicyList, sourceIp,
                                sslCert, lb.getLbProtocol());

                        lbRules.add(loadBalancing);
                    }
                    return sendLBRules(router, lbRules, network.getId());
                } else if (rules.get(0).getPurpose() == Purpose.PortForwarding) {
                    return sendPortForwardingRules(router, (List<PortForwardingRule>) rules, network.getId());
                } else if (rules.get(0).getPurpose() == Purpose.StaticNat) {
                    return sendStaticNatRules(router, (List<StaticNatRule>) rules, network.getId());
                } else if (rules.get(0).getPurpose() == Purpose.Firewall) {
                    return sendFirewallRules(router, (List<FirewallRule>) rules, network.getId());
                } else {
                    s_logger.warn("Unable to apply rules of purpose: " + rules.get(0).getPurpose());
                    return false;
                }
            }
        });
    }
    
    
    @Override
    public boolean applyLoadBalancingRules(Network network, final List<? extends LoadBalancingRule> rules, List<? extends VirtualRouter> routers) throws ResourceUnavailableException {
        if (rules == null || rules.isEmpty()) {
            s_logger.debug("No lb rules to be applied for network " + network.getId());
            return true;
        }
        return applyRules(network, routers, "loadbalancing rules", false, null, false, new RuleApplier() {
            @Override
            public boolean execute(Network network, VirtualRouter router) throws ResourceUnavailableException {
                // for load balancer we have to resend all lb rules for the network
                List<LoadBalancerVO> lbs = _loadBalancerDao.listByNetworkIdAndScheme(network.getId(), Scheme.Public);
                List<LoadBalancingRule> lbRules = new ArrayList<LoadBalancingRule>();
                for (LoadBalancerVO lb : lbs) {
                    List<LbDestination> dstList = _lbMgr.getExistingDestinations(lb.getId());
                    List<LbStickinessPolicy> policyList = _lbMgr.getStickinessPolicies(lb.getId());
                    List<LbHealthCheckPolicy> hcPolicyList = _lbMgr.getHealthCheckPolicies(lb.getId());
                    LbSslCert sslCert = _lbMgr.getLbSslCert(lb.getId());
                    Ip sourceIp = _networkModel.getPublicIpAddress(lb.getSourceIpAddressId()).getAddress();
                    LoadBalancingRule loadBalancing = new LoadBalancingRule(lb, dstList, policyList, hcPolicyList, sourceIp, sslCert, lb.getLbProtocol());
                    lbRules.add(loadBalancing);
                }
                return sendLBRules(router, lbRules, network.getId());
            }
        });
    }

    protected boolean sendLBRules(VirtualRouter router, List<LoadBalancingRule> rules, long guestNetworkId) throws ResourceUnavailableException {
        Commands cmds = new Commands(Command.OnError.Continue);
        createApplyLoadBalancingRulesCommands(rules, router, cmds, guestNetworkId);
        return sendCommandsToRouter(router, cmds);
    }

    protected boolean sendPortForwardingRules(VirtualRouter router, List<PortForwardingRule> rules, long guestNetworkId) throws ResourceUnavailableException {
        Commands cmds = new Commands(Command.OnError.Continue);
        createApplyPortForwardingRulesCommands(rules, router, cmds, guestNetworkId);
        return sendCommandsToRouter(router, cmds);
    }

    protected boolean sendStaticNatRules(VirtualRouter router, List<StaticNatRule> rules, long guestNetworkId) throws ResourceUnavailableException {
        Commands cmds = new Commands(Command.OnError.Continue);
        createApplyStaticNatRulesCommands(rules, router, cmds, guestNetworkId);
        return sendCommandsToRouter(router, cmds);
    }

    @Override
    public List<VirtualRouter> getRoutersForNetwork(long networkId) {
        List<DomainRouterVO> routers = _routerDao.findByNetwork(networkId);
        List<VirtualRouter> vrs = new ArrayList<VirtualRouter>(routers.size());
        for (DomainRouterVO router : routers) {
            vrs.add(router);
        }
        return vrs;
    }

    private void createFirewallRulesCommands(List<? extends FirewallRule> rules, VirtualRouter router, Commands cmds, long guestNetworkId) {
        List<FirewallRuleTO> rulesTO = null;
        String systemRule = null;
        Boolean defaultEgressPolicy = false;
        if (rules != null) {
            if (rules.size() > 0) {
                if (rules.get(0).getTrafficType() == FirewallRule.TrafficType.Egress && rules.get(0).getType() == FirewallRule.FirewallRuleType.System) {
                    systemRule = String.valueOf(FirewallRule.FirewallRuleType.System);
                }
            }
            rulesTO = new ArrayList<FirewallRuleTO>();
            for (FirewallRule rule : rules) {
                _rulesDao.loadSourceCidrs((FirewallRuleVO)rule);
                FirewallRule.TrafficType traffictype = rule.getTrafficType();
                if(traffictype == FirewallRule.TrafficType.Ingress){
                    IpAddress sourceIp = _networkModel.getIp(rule.getSourceIpAddressId());
                    FirewallRuleTO ruleTO = new FirewallRuleTO(rule, null, sourceIp.getAddress().addr(),Purpose.Firewall,traffictype);
                    rulesTO.add(ruleTO);
                } else if (rule.getTrafficType() == FirewallRule.TrafficType.Egress){
                    NetworkVO network = _networkDao.findById(guestNetworkId);
                    NetworkOfferingVO offering = _networkOfferingDao.findById(network.getNetworkOfferingId());
                    defaultEgressPolicy = offering.getEgressDefaultPolicy();
                    assert (rule.getSourceIpAddressId()==null) : "ipAddressId should be null for egress firewall rule. ";
                    FirewallRuleTO ruleTO = new FirewallRuleTO(rule, null,"",Purpose.Firewall, traffictype, defaultEgressPolicy);
                    rulesTO.add(ruleTO);
                }
            }
        }


        SetFirewallRulesCommand cmd = new SetFirewallRulesCommand(rulesTO);
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, getRouterIpInNetwork(guestNetworkId, router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        cmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());
        if (systemRule != null) {
            cmd.setAccessDetail(NetworkElementCommand.FIREWALL_EGRESS_DEFAULT, systemRule);
        } else {
            cmd.setAccessDetail(NetworkElementCommand.FIREWALL_EGRESS_DEFAULT, String.valueOf(defaultEgressPolicy));
        }

        cmds.addCommand(cmd);
    }


    protected boolean sendFirewallRules(VirtualRouter router, List<FirewallRule> rules, long guestNetworkId) throws ResourceUnavailableException {
        Commands cmds = new Commands(Command.OnError.Continue);
        createFirewallRulesCommands(rules, router, cmds, guestNetworkId);
        return sendCommandsToRouter(router, cmds);
    }

    @Override
    public String getDnsBasicZoneUpdate() {
        return _dnsBasicZoneUpdates;
    }
    
    protected interface RuleApplier {
        boolean execute(Network network, VirtualRouter router) throws ResourceUnavailableException;
    }
    
    protected boolean applyRules(Network network, List<? extends VirtualRouter> routers, String typeString,
            boolean isPodLevelException, Long podId, boolean failWhenDisconnect, RuleApplier applier) throws ResourceUnavailableException {
        if (routers == null || routers.isEmpty()) {
            s_logger.warn("Unable to apply " + typeString + ", virtual router doesn't exist in the network " + network.getId());
            throw new ResourceUnavailableException("Unable to apply " + typeString , DataCenter.class, network.getDataCenterId());
        }

        DataCenter dc = _dcDao.findById(network.getDataCenterId());
        boolean isZoneBasic = (dc.getNetworkType() == NetworkType.Basic);
        
        // isPodLevelException and podId is only used for basic zone
        assert !((!isZoneBasic && isPodLevelException) || (isZoneBasic && isPodLevelException && podId == null));
        
        List<VirtualRouter> connectedRouters = new ArrayList<VirtualRouter>();
        List<VirtualRouter> disconnectedRouters = new ArrayList<VirtualRouter>();
        boolean result = true;
        String msg = "Unable to apply " + typeString + " on disconnected router ";
        for (VirtualRouter router : routers) {
            if (router.getState() == State.Running) {
                s_logger.debug("Applying " + typeString + " in network " + network);

                if (router.isStopPending()) {
                    if (_hostDao.findById(router.getHostId()).getState() == Status.Up) {
                        throw new ResourceUnavailableException("Unable to process due to the stop pending router " +
                    router.getInstanceName() + " haven't been stopped after it's host coming back!",
                                DataCenter.class, router.getDataCenterId());
                    }
                    s_logger.debug("Router " + router.getInstanceName() + " is stop pending, so not sending apply " +
                    typeString + " commands to the backend");
                    continue;
                }

                try {
                    result = applier.execute(network, router);
                    connectedRouters.add(router);
                } catch (AgentUnavailableException e) {
                    s_logger.warn(msg + router.getInstanceName(), e);
                    disconnectedRouters.add(router);
                }

                //If rules fail to apply on one domR and not due to disconnection, no need to proceed with the rest
                if (!result) {
                    if (isZoneBasic && isPodLevelException) {
                        throw new ResourceUnavailableException("Unable to apply " + typeString + " on router ", Pod.class, podId);
                    }
                    throw new ResourceUnavailableException("Unable to apply " + typeString + " on router ", DataCenter.class,
                            router.getDataCenterId());
                }

            } else if (router.getState() == State.Stopped || router.getState() == State.Stopping) {
                s_logger.debug("Router " + router.getInstanceName() + " is in " + router.getState() +
                        ", so not sending apply " + typeString + " commands to the backend");
            } else {
                s_logger.warn("Unable to apply " + typeString + ", virtual router is not in the right state " + router.getState());
                if (isZoneBasic && isPodLevelException) {
                    throw new ResourceUnavailableException("Unable to apply " + typeString +
                            ", virtual router is not in the right state", Pod.class, podId);
                }
                throw new ResourceUnavailableException("Unable to apply " + typeString +
                        ", virtual router is not in the right state", DataCenter.class, router.getDataCenterId());
            }
        }

        if (!connectedRouters.isEmpty()) {
            if (!isZoneBasic && !disconnectedRouters.isEmpty() && disconnectedRouters.get(0).getIsRedundantRouter()) {
                // These disconnected redundant virtual routers are out of sync now, stop them for synchronization
                handleSingleWorkingRedundantRouter(connectedRouters, disconnectedRouters, msg);
            }
        } else if (!disconnectedRouters.isEmpty()) {
            for (VirtualRouter router : disconnectedRouters) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug(msg + router.getInstanceName() + "(" + router.getId() + ")");
                }
            }
            if (isZoneBasic && isPodLevelException) {
                throw new ResourceUnavailableException(msg, Pod.class, podId);
            }
            throw new ResourceUnavailableException(msg, DataCenter.class, disconnectedRouters.get(0).getDataCenterId());
        }

        result = true;
        if (failWhenDisconnect) {
            result = !connectedRouters.isEmpty();
        }
        return result;
    }

    @Override
    public boolean applyStaticNats(Network network, final List<? extends StaticNat> rules, List<? extends VirtualRouter> routers) throws ResourceUnavailableException {
        if (rules == null || rules.isEmpty()) {
            s_logger.debug("No static nat rules to be applied for network " + network.getId());
            return true;
        }
        return applyRules(network, routers, "static nat rules", false, null, false, new RuleApplier() {
            @Override
            public boolean execute(Network network, VirtualRouter router) throws ResourceUnavailableException {
                return applyStaticNat(router, rules, network.getId());
            }
        });
    }


    protected boolean applyStaticNat(VirtualRouter router, List<? extends StaticNat> rules, long guestNetworkId) throws ResourceUnavailableException {
        Commands cmds = new Commands(Command.OnError.Continue);
        createApplyStaticNatCommands(rules, router, cmds, guestNetworkId);
        return sendCommandsToRouter(router, cmds);
    }

    private void createApplyStaticNatCommands(List<? extends StaticNat> rules, VirtualRouter router, Commands cmds,
            long guestNetworkId) {
        List<StaticNatRuleTO> rulesTO = null;
        if (rules != null) {
            rulesTO = new ArrayList<StaticNatRuleTO>();
            for (StaticNat rule : rules) {
                IpAddress sourceIp = _networkModel.getIp(rule.getSourceIpAddressId());
                StaticNatRuleTO ruleTO = new StaticNatRuleTO(0, sourceIp.getAddress().addr(), null,
                        null, rule.getDestIpAddress(), null, null, null, rule.isForRevoke(), false);
                rulesTO.add(ruleTO);
            }
        }

        SetStaticNatRulesCommand cmd = new SetStaticNatRulesCommand(rulesTO, router.getVpcId());
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_IP, getRouterControlIp(router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_GUEST_IP, getRouterIpInNetwork(guestNetworkId, router.getId()));
        cmd.setAccessDetail(NetworkElementCommand.ROUTER_NAME, router.getInstanceName());
        DataCenterVO dcVo = _dcDao.findById(router.getDataCenterId());
        cmd.setAccessDetail(NetworkElementCommand.ZONE_NETWORK_TYPE, dcVo.getNetworkType().toString());
        cmds.addCommand(cmd);
    }

    @Override
    public int getTimeout() {
        return -1;
    }

    @Override
    public boolean isRecurring() {
        return false;
    }

    @Override
    public boolean processAnswers(long agentId, long seq, Answer[] answers) {
        return false;
    }

    @Override
    public boolean processCommands(long agentId, long seq, Command[] commands) {
        return false;
    }

    @Override
    public void processConnect(Host host, StartupCommand cmd, boolean forRebalance) throws ConnectionException {
        List<DomainRouterVO> routers = _routerDao.listIsolatedByHostId(host.getId());
        for (DomainRouterVO router : routers) {
            if (router.isStopPending()) {
                s_logger.info("Stopping router " + router.getInstanceName() + " due to stop pending flag found!");
                State state = router.getState();
                if (state != State.Stopped && state != State.Destroyed) {
                    try {
                        stopRouter(router.getId(), false);
                    } catch (ResourceUnavailableException e) {
                        s_logger.warn("Fail to stop router " + router.getInstanceName(), e);
                        throw new ConnectionException(false, "Fail to stop router " + router.getInstanceName());
                    } catch (ConcurrentOperationException e) {
                        s_logger.warn("Fail to stop router " + router.getInstanceName(), e);
                        throw new ConnectionException(false, "Fail to stop router " + router.getInstanceName());
                    }
                }
                router.setStopPending(false);
                router = _routerDao.persist(router);
            }
        }
    }

    @Override
    public AgentControlAnswer processControlCommand(long agentId, AgentControlCommand cmd) {
        return null;
    }

    @Override
    
    public boolean processDisconnect(long agentId, Status state) {
        return false;
    }

    @Override
    public boolean processTimeout(long agentId, long seq) {
        return false;
    }

    protected String getRouterControlIp(long routerId) {
        String routerControlIpAddress = null;
        List<NicVO> nics = _nicDao.listByVmId(routerId);
        for (NicVO n : nics) {
            NetworkVO nc = _networkDao.findById(n.getNetworkId());
            if (nc !=null && nc.getTrafficType() == TrafficType.Control) {
                routerControlIpAddress = n.getIp4Address();
                break;
            }
        }
        
        if(routerControlIpAddress == null) {
            s_logger.warn("Unable to find router's control ip in its attached NICs!. routerId: " + routerId);
            DomainRouterVO router = _routerDao.findById(routerId);
            return router.getPrivateIpAddress();
        }
            
        return routerControlIpAddress;
    }
    
    
    protected String getRouterIpInNetwork(long networkId, long instanceId) {
        return _nicDao.getIpAddress(networkId, instanceId);
    }


    @Override
    public void prepareStop(VirtualMachineProfile profile){
        //Collect network usage before stopping Vm

        final DomainRouterVO router = _routerDao.findById(profile.getVirtualMachine().getId());
        if(router == null){
            return;
        }

        String privateIP = router.getPrivateIpAddress();

        if (privateIP != null) {
            final boolean forVpc = router.getVpcId() != null;
            List<? extends Nic> routerNics = _nicDao.listByVmId(router.getId());
            for (final Nic routerNic : routerNics) {
                final Network network = _networkModel.getNetwork(routerNic.getNetworkId());
                //Send network usage command for public nic in VPC VR
                //Send network usage command for isolated guest nic of non VPC VR
                if ((forVpc && network.getTrafficType() == TrafficType.Public) || (!forVpc && network.getTrafficType() == TrafficType.Guest && network.getGuestType() == Network.GuestType.Isolated)) {
                    final NetworkUsageCommand usageCmd = new NetworkUsageCommand(privateIP, router.getHostName(),
                            forVpc, routerNic.getIp4Address());
                    final String routerType = router.getType().toString();
                    final UserStatisticsVO previousStats = _userStatsDao.findBy(router.getAccountId(),
                            router.getDataCenterId(), network.getId(), (forVpc ? routerNic.getIp4Address() : null), router.getId(), routerType);
                    NetworkUsageAnswer answer = null;
                    try {
                        answer = (NetworkUsageAnswer) _agentMgr.easySend(router.getHostId(), usageCmd);
                    } catch (Exception e) {
                        s_logger.warn("Error while collecting network stats from router: " + router.getInstanceName() + " from host: " + router.getHostId(), e);
                        continue;
                    }

                    if (answer != null) {
                        if (!answer.getResult()) {
                            s_logger.warn("Error while collecting network stats from router: " + router.getInstanceName() + " from host: " + router.getHostId() + "; details: " + answer.getDetails());
                            continue;
                        }
                        try {
                            if ((answer.getBytesReceived() == 0) && (answer.getBytesSent() == 0)) {
                                s_logger.debug("Recieved and Sent bytes are both 0. Not updating user_statistics");
                                continue;
                            }
                            
                            final NetworkUsageAnswer answerFinal = answer;
                            Transaction.execute(new TransactionCallbackNoReturn() {
                                @Override
                                public void doInTransactionWithoutResult(TransactionStatus status) {
                                    UserStatisticsVO stats = _userStatsDao.lock(router.getAccountId(),
                                            router.getDataCenterId(), network.getId(), (forVpc ? routerNic.getIp4Address() : null), router.getId(), routerType);
                                    if (stats == null) {
                                        s_logger.warn("unable to find stats for account: " + router.getAccountId());
                                        return;
                                    }
        
                                    if (previousStats != null
                                            && ((previousStats.getCurrentBytesReceived() != stats.getCurrentBytesReceived())
                                            || (previousStats.getCurrentBytesSent() != stats.getCurrentBytesSent()))){
                                        s_logger.debug("Router stats changed from the time NetworkUsageCommand was sent. " +
                                                "Ignoring current answer. Router: " + answerFinal.getRouterName() + " Rcvd: " +
                                                answerFinal.getBytesReceived() + "Sent: " + answerFinal.getBytesSent());
                                        return;
                                    }
        
                                    if (stats.getCurrentBytesReceived() > answerFinal.getBytesReceived()) {
                                        if (s_logger.isDebugEnabled()) {
                                            s_logger.debug("Received # of bytes that's less than the last one.  " +
                                                    "Assuming something went wrong and persisting it. Router: " +
                                                    answerFinal.getRouterName() + " Reported: " + answerFinal.getBytesReceived()
                                                    + " Stored: " + stats.getCurrentBytesReceived());
                                        }
                                        stats.setNetBytesReceived(stats.getNetBytesReceived() + stats.getCurrentBytesReceived());
                                    }
                                    stats.setCurrentBytesReceived(answerFinal.getBytesReceived());
                                    if (stats.getCurrentBytesSent() > answerFinal.getBytesSent()) {
                                        if (s_logger.isDebugEnabled()) {
                                            s_logger.debug("Received # of bytes that's less than the last one.  " +
                                                    "Assuming something went wrong and persisting it. Router: " +
                                                    answerFinal.getRouterName() + " Reported: " + answerFinal.getBytesSent()
                                                    + " Stored: " + stats.getCurrentBytesSent());
                                        }
                                        stats.setNetBytesSent(stats.getNetBytesSent() + stats.getCurrentBytesSent());
                                    }
                                    stats.setCurrentBytesSent(answerFinal.getBytesSent());
                                    if (! _dailyOrHourly) {
                                        //update agg bytes
                                        stats.setAggBytesSent(stats.getNetBytesSent() + stats.getCurrentBytesSent());
                                        stats.setAggBytesReceived(stats.getNetBytesReceived() + stats.getCurrentBytesReceived());
                                    }
                                    _userStatsDao.update(stats.getId(), stats);
                                }
                            });
                        } catch (Exception e) {
                            s_logger.warn("Unable to update user statistics for account: " + router.getAccountId()
                                    + " Rx: " + answer.getBytesReceived() + "; Tx: " + answer.getBytesSent());
                        }
                    }
                }
            }
        }
    }



    @Override
    public VirtualRouter findRouter(long routerId) {
        return _routerDao.findById(routerId);
    }

    @Override
    public List<Long> upgradeRouterTemplate(UpgradeRouterTemplateCmd cmd){

        List<DomainRouterVO> routers = new ArrayList<DomainRouterVO>();
        int params = 0;

        Long routerId = cmd.getId();
        if(routerId != null)    {
            params++;
            DomainRouterVO router = _routerDao.findById(routerId);
            if(router != null){
                routers.add(router);
            }
        }

        Long domainId = cmd.getDomainId();
        if(domainId != null){
            String accountName = cmd.getAccount();
            //List by account, if account Name is specified along with domainId
            if(accountName != null){
                Account account = _accountMgr.getActiveAccountByName(accountName, domainId);
                if(account == null){
                    throw new InvalidParameterValueException("Account :"+accountName+" does not exist in domain: "+domainId);
                }
                routers = _routerDao.listRunningByAccountId(account.getId());
            } else {
            //List by domainId, account name not specified
                routers = _routerDao.listRunningByDomain(domainId);
            }
            params++;
        }

        Long clusterId = cmd.getClusterId();
        if(clusterId != null){
            params++;
            routers = _routerDao.listRunningByClusterId(clusterId);
        }

        Long podId = cmd.getPodId();
        if(podId != null){
            params++;
            routers = _routerDao.listRunningByPodId(podId);
        }

        Long zoneId = cmd.getZoneId();
        if(zoneId != null){
            params++;
            routers = _routerDao.listRunningByDataCenter(zoneId);
        }

        if(params > 1){
            throw new InvalidParameterValueException("Multiple parameters not supported. Specify only one among routerId/zoneId/podId/clusterId/accountId/domainId");
        }

        if(routers != null){
            return rebootRouters(routers);
        }

        return null;
    }

    //Checks if the router is at the required version
    // Compares MS version and router version
    protected boolean checkRouterVersion(VirtualRouter router){
        if(!routerVersionCheckEnabled.value()){
            //Router version check is disabled.
            return true;
        }
        if(router.getTemplateVersion() == null){
            return false;
        }
        String trimmedVersion = Version.trimRouterVersion(router.getTemplateVersion());
        return (Version.compare(trimmedVersion, _minVRVersion) >= 0);
    }

    private List<Long> rebootRouters(List<DomainRouterVO> routers){
        List<Long> jobIds = new ArrayList<Long>();
        for(DomainRouterVO router: routers){
            if(!checkRouterVersion(router)){
                    s_logger.debug("Upgrading template for router: "+router.getId());
                    ApiDispatcher.getInstance();
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("ctxUserId", "1");
                    params.put("ctxAccountId", "" + router.getAccountId());

                    RebootRouterCmd cmd = new RebootRouterCmd();
                    ComponentContext.inject(cmd);
                    params.put("id", ""+router.getId());
                    params.put("ctxStartEventId", "1");
                    AsyncJobVO job = new AsyncJobVO(UUID.randomUUID().toString(), User.UID_SYSTEM, router.getAccountId(), RebootRouterCmd.class.getName(),
                            ApiGsonHelper.getBuilder().create().toJson(params), router.getId(),
                            cmd.getInstanceType() != null ? cmd.getInstanceType().toString() : null);
                    job.setDispatcher(_asyncDispatcher.getName());
                    long jobId = _asyncMgr.submitAsyncJob(job);
                    jobIds.add(jobId);
            } else {
                s_logger.debug("Router: "+router.getId()+" is already at the latest version. No upgrade required" );
            }
        }
        return jobIds;
    }

    @Override
    public String getConfigComponentName() {
        return VirtualNetworkApplianceManagerImpl.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {UseExternalDnsServers, routerVersionCheckEnabled};
    }
}

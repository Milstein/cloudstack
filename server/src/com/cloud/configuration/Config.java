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
package com.cloud.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.engine.subsystem.api.storage.StoragePoolAllocator;
import org.apache.cloudstack.framework.config.ConfigKey;

import com.cloud.agent.AgentManager;
import com.cloud.consoleproxy.ConsoleProxyManager;
import com.cloud.ha.HighAvailabilityManager;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.network.router.VpcVirtualNetworkApplianceManager;
import com.cloud.network.vpc.VpcManager;
import com.cloud.server.ManagementServer;
import com.cloud.storage.StorageManager;
import com.cloud.storage.secondary.SecondaryStorageVmManager;
import com.cloud.storage.snapshot.SnapshotManager;
import com.cloud.template.TemplateManager;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.snapshot.VMSnapshotManager;

public enum Config {

	// Alert

	AlertEmailAddresses("Alert", ManagementServer.class, String.class, "alert.email.addresses", null, "Comma separated list of email addresses used for sending alerts.", null),
	AlertEmailSender("Alert", ManagementServer.class, String.class, "alert.email.sender", null, "Sender of alert email (will be in the From header of the email).", null),
	AlertSMTPHost("Alert", ManagementServer.class, String.class, "alert.smtp.host", null, "SMTP hostname used for sending out email alerts.", null),
	AlertSMTPPassword("Secure", ManagementServer.class, String.class, "alert.smtp.password", null, "Password for SMTP authentication (applies only if alert.smtp.useAuth is true).", null),
	AlertSMTPPort("Alert", ManagementServer.class, Integer.class, "alert.smtp.port", "465", "Port the SMTP server is listening on.", null),
    AlertSMTPConnectionTimeout("Alert", ManagementServer.class, Integer.class, "alert.smtp.connectiontimeout", "30000",
            "Socket connection timeout value in milliseconds. -1 for infinite timeout.", null),
    AlertSMTPTimeout("Alert", ManagementServer.class, Integer.class, "alert.smtp.timeout", "30000", "Socket I/O timeout value in milliseconds. -1 for infinite timeout.", null),
	AlertSMTPUseAuth("Alert", ManagementServer.class, String.class, "alert.smtp.useAuth", null, "If true, use SMTP authentication when sending emails.", null),
	AlertSMTPUsername("Alert", ManagementServer.class, String.class, "alert.smtp.username", null, "Username for SMTP authentication (applies only if alert.smtp.useAuth is true).", null),
	CapacityCheckPeriod("Alert", ManagementServer.class, Integer.class, "capacity.check.period", "300000", "The interval in milliseconds between capacity checks", null),
	PublicIpCapacityThreshold("Alert", ManagementServer.class, Float.class, "zone.virtualnetwork.publicip.capacity.notificationthreshold", "0.75", "Percentage (as a value between 0 and 1) of public IP address space utilization above which alerts will be sent.", null),
	PrivateIpCapacityThreshold("Alert", ManagementServer.class, Float.class, "pod.privateip.capacity.notificationthreshold", "0.75", "Percentage (as a value between 0 and 1) of private IP address space utilization above which alerts will be sent.", null),
	SecondaryStorageCapacityThreshold("Alert", ManagementServer.class, Float.class, "zone.secstorage.capacity.notificationthreshold", "0.75", "Percentage (as a value between 0 and 1) of secondary storage utilization above which alerts will be sent about low storage available.", null),
	VlanCapacityThreshold("Alert", ManagementServer.class, Float.class, "zone.vlan.capacity.notificationthreshold", "0.75", "Percentage (as a value between 0 and 1) of Zone Vlan utilization above which alerts will be sent about low number of Zone Vlans.", null),
	DirectNetworkPublicIpCapacityThreshold("Alert", ManagementServer.class, Float.class, "zone.directnetwork.publicip.capacity.notificationthreshold", "0.75", "Percentage (as a value between 0 and 1) of Direct Network Public Ip Utilization above which alerts will be sent about low number of direct network public ips.", null),
	LocalStorageCapacityThreshold("Alert", ManagementServer.class, Float.class, "cluster.localStorage.capacity.notificationthreshold", "0.75", "Percentage (as a value between 0 and 1) of local storage utilization above which alerts will be sent about low local storage available.", null),


	// Storage

	StorageStatsInterval("Storage", ManagementServer.class, String.class, "storage.stats.interval", "60000", "The interval (in milliseconds) when storage stats (per host) are retrieved from agents.", null),
	MaxVolumeSize("Storage", ManagementServer.class, Integer.class, "storage.max.volume.size", "2000", "The maximum size for a volume (in GB).", null),
    StorageCacheReplacementLRUTimeInterval("Storage", ManagementServer.class, Integer.class, "storage.cache.replacement.lru.interval", "30", "time interval for unused data on cache storage (in days).", null),
    StorageCacheReplacementEnabled("Storage", ManagementServer.class, Boolean.class, "storage.cache.replacement.enabled", "true", "enable or disable cache storage replacement algorithm.", null),
    StorageCacheReplacementInterval("Storage", ManagementServer.class, Integer.class, "storage.cache.replacement.interval", "86400", "time interval between cache replacement threads (in seconds).", null),
    MaxUploadVolumeSize("Storage",  ManagementServer.class, Integer.class, "storage.max.volume.upload.size", "500", "The maximum size for a uploaded volume(in GB).", null),
	TotalRetries("Storage", AgentManager.class, Integer.class, "total.retries", "4", "The number of times each command sent to a host should be retried in case of failure.", null),
	StoragePoolMaxWaitSeconds("Storage", ManagementServer.class, Integer.class, "storage.pool.max.waitseconds", "3600", "Timeout (in seconds) to synchronize storage pool operations.", null),
	StorageTemplateCleanupEnabled("Storage", ManagementServer.class, Boolean.class, "storage.template.cleanup.enabled", "true", "Enable/disable template cleanup activity, only take effect when overall storage cleanup is enabled", null),
	PrimaryStorageDownloadWait("Storage", TemplateManager.class, Integer.class, "primary.storage.download.wait", "10800", "In second, timeout for download template to primary storage", null),
	CreateVolumeFromSnapshotWait("Storage", StorageManager.class, Integer.class, "create.volume.from.snapshot.wait", "10800", "In second, timeout for creating volume from snapshot", null),
	CopyVolumeWait("Storage", StorageManager.class, Integer.class, "copy.volume.wait", "10800", "In second, timeout for copy volume command", null),
	CreatePrivateTemplateFromVolumeWait("Storage", UserVmManager.class, Integer.class, "create.private.template.from.volume.wait", "10800", "In second, timeout for CreatePrivateTemplateFromVolumeCommand", null),
	CreatePrivateTemplateFromSnapshotWait("Storage", UserVmManager.class, Integer.class, "create.private.template.from.snapshot.wait", "10800", "In second, timeout for CreatePrivateTemplateFromSnapshotCommand", null),
	BackupSnapshotWait(
            "Storage", StorageManager.class, Integer.class, "backup.snapshot.wait", "21600", "In second, timeout for BackupSnapshotCommand", null),
    HAStorageMigration("Storage", ManagementServer.class, Boolean.class, "enable.ha.storage.migration", "true", "Enable/disable storage migration across primary storage during HA", null),
            
	// Network
	NetworkLBHaproxyStatsVisbility("Network", ManagementServer.class, String.class, "network.loadbalancer.haproxy.stats.visibility", "global", "Load Balancer(haproxy) stats visibilty, the value can be one of the following six parameters : global,guest-network,link-local,disabled,all,default", null),
	NetworkLBHaproxyStatsUri("Network", ManagementServer.class, String.class, "network.loadbalancer.haproxy.stats.uri","/admin?stats","Load Balancer(haproxy) uri.",null),
	NetworkLBHaproxyStatsAuth("Secure", ManagementServer.class, String.class, "network.loadbalancer.haproxy.stats.auth","admin1:AdMiN123","Load Balancer(haproxy) authetication string in the format username:password",null),
	NetworkLBHaproxyStatsPort("Network", ManagementServer.class, String.class, "network.loadbalancer.haproxy.stats.port","8081","Load Balancer(haproxy) stats port number.",null),
    NetworkLBHaproxyMaxConn("Network", ManagementServer.class, Integer.class, "network.loadbalancer.haproxy.max.conn", "4096", "Load Balancer(haproxy) maximum number of concurrent connections(global max)", null),
	NetworkRouterRpFilter("Network", ManagementServer.class, Integer.class, "network.disable.rpfilter", "true", "disable rp_filter on Domain Router VM public interfaces.", null),

	GuestVlanBits("Network", ManagementServer.class, Integer.class, "guest.vlan.bits", "12", "The number of bits to reserve for the VLAN identifier in the guest subnet.", null),
	//MulticastThrottlingRate("Network", ManagementServer.class, Integer.class, "multicast.throttling.rate", "10", "Default multicast rate in megabits per second allowed.", null),
	DirectNetworkNoDefaultRoute("Network", ManagementServer.class, Boolean.class, "direct.network.no.default.route", "false", "Direct Network Dhcp Server should not send a default route", "true/false"),
	OvsTunnelNetwork("Network", ManagementServer.class, Boolean.class, "sdn.ovs.controller", "false", "Enable/Disable Open vSwitch SDN controller for L2-in-L3 overlay networks", null),
	OvsTunnelNetworkDefaultLabel("Network", ManagementServer.class, String.class, "sdn.ovs.controller.default.label", "cloud-public", "Default network label to be used when fetching interface for GRE endpoints", null),
	VmNetworkThrottlingRate("Network", ManagementServer.class, Integer.class, "vm.network.throttling.rate", "200", "Default data transfer rate in megabits per second allowed in User vm's default network.", null),

	SecurityGroupWorkCleanupInterval("Network", ManagementServer.class, Integer.class, "network.securitygroups.work.cleanup.interval", "120", "Time interval (seconds) in which finished work is cleaned up from the work table", null),
	SecurityGroupWorkerThreads("Network", ManagementServer.class, Integer.class, "network.securitygroups.workers.pool.size", "50", "Number of worker threads processing the security group update work queue", null),
	SecurityGroupWorkGlobalLockTimeout("Network", ManagementServer.class, Integer.class, "network.securitygroups.work.lock.timeout", "300", "Lock wait timeout (seconds) while updating the security group work queue", null),
	SecurityGroupWorkPerAgentMaxQueueSize("Network", ManagementServer.class, Integer.class, "network.securitygroups.work.per.agent.queue.size", "100", "The number of outstanding security group work items that can be queued to a host. If exceeded, work items will get dropped to conserve memory. Security Group Sync will take care of ensuring that the host gets updated eventually", null),

	SecurityGroupDefaultAdding("Network", ManagementServer.class, Boolean.class, "network.securitygroups.defaultadding", "true", "If true, the user VM would be added to the default security group by default", null),

	GuestOSNeedGatewayOnNonDefaultNetwork("Network", NetworkOrchestrationService.class, String.class, "network.dhcp.nondefaultnetwork.setgateway.guestos", "Windows", "The guest OS's name start with this fields would result in DHCP server response gateway information even when the network it's on is not default network. Names are separated by comma.", null),
    EnableServiceMonitoring("Network", ManagementServer.class, Boolean.class, "network.router.enableserviceMonitoring", "true", "service monitoring in router enable/disable option, default true", null),

	//VPN
	RemoteAccessVpnPskLength("Network", AgentManager.class, Integer.class, "remote.access.vpn.psk.length", "24", "The length of the ipsec preshared key (minimum 8, maximum 256)", null),
	RemoteAccessVpnUserLimit("Network", AgentManager.class, String.class, "remote.access.vpn.user.limit", "8", "The maximum number of VPN users that can be created per account", null),
	Site2SiteVpnConnectionPerVpnGatewayLimit("Network", ManagementServer.class, Integer.class, "site2site.vpn.vpngateway.connection.limit", "4", "The maximum number of VPN connection per VPN gateway", null),
	Site2SiteVpnSubnetsPerCustomerGatewayLimit("Network", ManagementServer.class, Integer.class, "site2site.vpn.customergateway.subnets.limit", "10", "The maximum number of subnets per customer gateway", null),

	// Console Proxy
	ConsoleProxyCapacityStandby("Console Proxy", AgentManager.class, String.class, "consoleproxy.capacity.standby", "10", "The minimal number of console proxy viewer sessions that system is able to serve immediately(standby capacity)", null),
	ConsoleProxyCapacityScanInterval("Console Proxy", AgentManager.class, String.class, "consoleproxy.capacityscan.interval", "30000", "The time interval(in millisecond) to scan whether or not system needs more console proxy to ensure minimal standby capacity", null),
	ConsoleProxyCmdPort("Console Proxy", AgentManager.class, Integer.class, "consoleproxy.cmd.port", "8001", "Console proxy command port that is used to communicate with management server", null),
	ConsoleProxyRestart("Console Proxy", AgentManager.class, Boolean.class, "consoleproxy.restart", "true", "Console proxy restart flag, defaulted to true", null),
	ConsoleProxyUrlDomain("Console Proxy", AgentManager.class, String.class, "consoleproxy.url.domain", "", "Console proxy url domain", null),
	ConsoleProxyLoadscanInterval("Console Proxy", AgentManager.class, String.class, "consoleproxy.loadscan.interval", "10000", "The time interval(in milliseconds) to scan console proxy working-load info", null),
	ConsoleProxySessionMax("Console Proxy", AgentManager.class, Integer.class, "consoleproxy.session.max", String.valueOf(ConsoleProxyManager.DEFAULT_PROXY_CAPACITY), "The max number of viewer sessions console proxy is configured to serve for", null),
	ConsoleProxySessionTimeout("Console Proxy", AgentManager.class, Integer.class, "consoleproxy.session.timeout", "300000", "Timeout(in milliseconds) that console proxy tries to maintain a viewer session before it times out the session for no activity", null),
	ConsoleProxyDisableRpFilter("Console Proxy", AgentManager.class, Integer.class, "consoleproxy.disable.rpfilter", "true", "disable rp_filter on console proxy VM public interface", null),
    ConsoleProxyLaunchMax("Console Proxy", AgentManager.class, Integer.class, "consoleproxy.launch.max", "10", "maximum number of console proxy instances per zone can be launched", null),
    ConsoleProxyManagementState("Console Proxy", AgentManager.class, String.class, "consoleproxy.management.state", com.cloud.consoleproxy.ConsoleProxyManagementState.Auto.toString(),
		"console proxy service management state", null),
    ConsoleProxyManagementLastState("Console Proxy", AgentManager.class, String.class, "consoleproxy.management.state.last", com.cloud.consoleproxy.ConsoleProxyManagementState.Auto.toString(),
		"last console proxy service management state", null),

	// Snapshots
    SnapshotHourlyMax("Snapshots", SnapshotManager.class, Integer.class, "snapshot.max.hourly", "8", "Maximum hourly snapshots for a volume", null),
    SnapshotDailyMax("Snapshots", SnapshotManager.class, Integer.class, "snapshot.max.daily", "8", "Maximum daily snapshots for a volume", null),
    SnapshotWeeklyMax("Snapshots", SnapshotManager.class, Integer.class, "snapshot.max.weekly", "8", "Maximum weekly snapshots for a volume", null),
    SnapshotMonthlyMax("Snapshots", SnapshotManager.class, Integer.class, "snapshot.max.monthly", "8", "Maximum monthly snapshots for a volume", null),
    SnapshotPollInterval("Snapshots", SnapshotManager.class, Integer.class, "snapshot.poll.interval", "300", "The time interval in seconds when the management server polls for snapshots to be scheduled.", null),
    SnapshotDeltaMax("Snapshots", SnapshotManager.class, Integer.class, "snapshot.delta.max", "16", "max delta snapshots between two full snapshots.", null),
    BackupSnapshotAfterTakingSnapshot("Snapshots", SnapshotManager.class, Boolean.class, "snapshot.backup.rightafter", "true", "backup snapshot right after snapshot is taken", null),
    KVMSnapshotEnabled("Snapshots", SnapshotManager.class, Boolean.class, "kvm.snapshot.enabled", "false", "whether snapshot is enabled for KVM hosts", null),

	// Advanced
    JobExpireMinutes("Advanced", ManagementServer.class, String.class, "job.expire.minutes", "1440", "Time (in minutes) for async-jobs to be kept in system", null),
    JobCancelThresholdMinutes("Advanced", ManagementServer.class, String.class, "job.cancel.threshold.minutes", "60", "Time (in minutes) for async-jobs to be forcely cancelled if it has been in process for long", null),
    EventPurgeInterval("Advanced", ManagementServer.class, Integer.class, "event.purge.interval", "86400", "The interval (in seconds) to wait before running the event purge thread", null),
	AccountCleanupInterval("Advanced", ManagementServer.class, Integer.class, "account.cleanup.interval", "86400", "The interval (in seconds) between cleanup for removed accounts", null),
	InstanceName("Advanced", AgentManager.class, String.class, "instance.name", "VM", "Name of the deployment instance.", "instanceName"),
	ExpungeDelay("Advanced", UserVmManager.class, Integer.class, "expunge.delay", "86400", "Determines how long (in seconds) to wait before actually expunging destroyed vm. The default value = the default value of expunge.interval", null),
	ExpungeInterval("Advanced", UserVmManager.class, Integer.class, "expunge.interval", "86400", "The interval (in seconds) to wait before running the expunge thread.", null),
	ExpungeWorkers("Advanced", UserVmManager.class, Integer.class, "expunge.workers",  "1", "Number of workers performing expunge ", null),
	ExtractURLCleanUpInterval("Advanced", ManagementServer.class, Integer.class, "extract.url.cleanup.interval",  "7200", "The interval (in seconds) to wait before cleaning up the extract URL's ", null),
	DisableExtraction("Advanced", ManagementServer.class, Boolean.class, "disable.extraction",  "false", "Flag for disabling extraction of template, isos and volumes", null),
	ExtractURLExpirationInterval("Advanced", ManagementServer.class, Integer.class, "extract.url.expiration.interval",  "14400", "The life of an extract URL after which it is deleted ", null),
	HostStatsInterval("Advanced", ManagementServer.class, Integer.class, "host.stats.interval", "60000", "The interval (in milliseconds) when host stats are retrieved from agents.", null),
	HostRetry("Advanced", AgentManager.class, Integer.class, "host.retry", "2", "Number of times to retry hosts for creating a volume", null),
	IntegrationAPIPort("Advanced", ManagementServer.class, Integer.class, "integration.api.port", null, "Default API port", null),
	InvestigateRetryInterval("Advanced", HighAvailabilityManager.class, Integer.class, "investigate.retry.interval", "60", "Time (in seconds) between VM pings when agent is disconnected", null),
	MigrateRetryInterval("Advanced", HighAvailabilityManager.class, Integer.class, "migrate.retry.interval", "120", "Time (in seconds) between migration retries", null),
	RouterCpuMHz("Advanced", NetworkOrchestrationService.class, Integer.class, "router.cpu.mhz", String.valueOf(VpcVirtualNetworkApplianceManager.DEFAULT_ROUTER_CPU_MHZ), "Default CPU speed (MHz) for router VM.", null),
	RestartRetryInterval("Advanced", HighAvailabilityManager.class, Integer.class, "restart.retry.interval", "600", "Time (in seconds) between retries to restart a vm", null),
	RouterStatsInterval("Advanced", NetworkOrchestrationService.class, Integer.class, "router.stats.interval", "300", "Interval (in seconds) to report router statistics.", null),
	ExternalNetworkStatsInterval("Advanced", NetworkOrchestrationService.class, Integer.class, "external.network.stats.interval", "300", "Interval (in seconds) to report external network statistics.", null),
	RouterCheckInterval("Advanced", NetworkOrchestrationService.class, Integer.class, "router.check.interval", "30", "Interval (in seconds) to report redundant router status.", null),
	RouterCheckPoolSize("Advanced", NetworkOrchestrationService.class, Integer.class, "router.check.poolsize", "10", "Numbers of threads using to check redundant router status.", null),
    RouterExtraPublicNics("Advanced", NetworkOrchestrationService.class, Integer.class, "router.extra.public.nics", "2", "specify extra public nics used for virtual router(up to 5)", "0-5"),
    ScaleRetry("Advanced", ManagementServer.class, Integer.class, "scale.retry", "2", "Number of times to retry scaling up the vm", null),
    StopRetryInterval("Advanced", HighAvailabilityManager.class, Integer.class, "stop.retry.interval", "600", "Time in seconds between retries to stop or destroy a vm" , null),
	StorageCleanupInterval("Advanced", StorageManager.class, Integer.class, "storage.cleanup.interval", "86400", "The interval (in seconds) to wait before running the storage cleanup thread.", null),
	StorageCleanupEnabled("Advanced", StorageManager.class, Boolean.class, "storage.cleanup.enabled", "true", "Enables/disables the storage cleanup thread.", null),
	UpdateWait("Advanced", AgentManager.class, Integer.class, "update.wait", "600", "Time to wait (in seconds) before alerting on a updating agent", null),
	XapiWait("Advanced", AgentManager.class, Integer.class, "xapiwait", "600", "Time (in seconds) to wait for XAPI to return", null),
    MigrateWait("Advanced", AgentManager.class, Integer.class, "migratewait", "3600", "Time (in seconds) to wait for VM migrate finish", null),
	HAWorkers("Advanced", AgentManager.class, Integer.class, "ha.workers", "5", "Number of ha worker threads.", null),
	MountParent("Advanced", ManagementServer.class, String.class, "mount.parent", "/var/cloudstack/mnt", "The mount point on the Management Server for Secondary Storage.", null),
//	UpgradeURL("Advanced", ManagementServer.class, String.class, "upgrade.url", "http://example.com:8080/client/agent/update.zip", "The upgrade URL is the URL of the management server that agents will connect to in order to automatically upgrade.", null),
	SystemVMUseLocalStorage("Advanced", ManagementServer.class, Boolean.class, "system.vm.use.local.storage", "false", "Indicates whether to use local storage pools or shared storage pools for system VMs.", null),
    SystemVMAutoReserveCapacity("Advanced", ManagementServer.class, Boolean.class, "system.vm.auto.reserve.capacity", "true", "Indicates whether or not to automatically reserver system VM standby capacity.", null),
	SystemVMDefaultHypervisor("Advanced", ManagementServer.class, String.class, "system.vm.default.hypervisor", null, "Hypervisor type used to create system vm", null),
    SystemVMRandomPassword("Advanced", ManagementServer.class, Boolean.class, "system.vm.random.password", "false", "Randomize system vm password the first time management server starts", null),
	LinkLocalIpNums("Advanced", ManagementServer.class, Integer.class, "linkLocalIp.nums", "10", "The number of link local ip that needed by domR(in power of 2)", null),
	HypervisorList("Advanced", ManagementServer.class, String.class, "hypervisor.list", HypervisorType.Hyperv + "," + HypervisorType.KVM + "," + HypervisorType.XenServer + "," + HypervisorType.VMware + "," + HypervisorType.BareMetal + "," + HypervisorType.Ovm + "," + HypervisorType.LXC, "The list of hypervisors that this deployment will use.", "hypervisorList"),
	ManagementNetwork("Advanced", ManagementServer.class, String.class, "management.network.cidr", null, "The cidr of management server network", null),
	EventPurgeDelay("Advanced", ManagementServer.class, Integer.class, "event.purge.delay", "15", "Events older than specified number days will be purged. Set this value to 0 to never delete events", null),
	SecStorageVmMTUSize("Advanced", AgentManager.class, Integer.class, "secstorage.vm.mtu.size", String.valueOf(SecondaryStorageVmManager.DEFAULT_SS_VM_MTUSIZE), "MTU size (in Byte) of storage network in secondary storage vms", null),
	MaxTemplateAndIsoSize("Advanced",  ManagementServer.class, Long.class, "max.template.iso.size", "50", "The maximum size for a downloaded template or ISO (in GB).", null),
	SecStorageAllowedInternalDownloadSites("Advanced", ManagementServer.class, String.class, "secstorage.allowed.internal.sites", null, "Comma separated list of cidrs internal to the datacenter that can host template download servers, please note 0.0.0.0 is not a valid site", null),
	SecStorageEncryptCopy("Advanced", ManagementServer.class, Boolean.class, "secstorage.encrypt.copy", "false", "Use SSL method used to encrypt copy traffic between zones", "true,false"),
	SecStorageSecureCopyCert("Advanced", ManagementServer.class, String.class, "secstorage.ssl.cert.domain", "", "SSL certificate used to encrypt copy traffic between zones", null),
	SecStorageCapacityStandby("Advanced", AgentManager.class, Integer.class, "secstorage.capacity.standby", "10", "The minimal number of command execution sessions that system is able to serve immediately(standby capacity)", null),
	SecStorageSessionMax("Advanced", AgentManager.class, Integer.class, "secstorage.session.max", "50", "The max number of command execution sessions that a SSVM can handle", null),
	SecStorageCmdExecutionTimeMax("Advanced", AgentManager.class, Integer.class, "secstorage.cmd.execution.time.max", "30", "The max command execution time in minute", null),
	SecStorageProxy("Advanced", AgentManager.class, String.class, "secstorage.proxy", null, "http proxy used by ssvm, in http://username:password@proxyserver:port format", null),
    AlertPurgeInterval("Advanced", ManagementServer.class, Integer.class, "alert.purge.interval", "86400", "The interval (in seconds) to wait before running the alert purge thread", null),
    AlertPurgeDelay("Advanced", ManagementServer.class, Integer.class, "alert.purge.delay", "0", "Alerts older than specified number days will be purged. Set this value to 0 to never delete alerts", null),
    HostReservationReleasePeriod("Advanced", ManagementServer.class, Integer.class, "host.reservation.release.period", "300000", "The interval in milliseconds between host reservation release checks", null),
    // LB HealthCheck Interval.
    LBHealthCheck("Advanced", ManagementServer.class, String.class, "healthcheck.update.interval", "600",
            "Time Interval to fetch the LB health check states (in sec)", null),

	DirectAttachNetworkEnabled("Advanced", ManagementServer.class, Boolean.class, "direct.attach.network.externalIpAllocator.enabled", "false", "Direct-attach VMs using external DHCP server", "true,false"),
	DirectAttachNetworkExternalAPIURL("Advanced", ManagementServer.class, String.class, "direct.attach.network.externalIpAllocator.url", null, "Direct-attach VMs using external DHCP server (API url)", null),
	CheckPodCIDRs("Advanced", ManagementServer.class, String.class, "check.pod.cidrs", "true", "If true, different pods must belong to different CIDR subnets.", "true,false"),
	NetworkGcWait("Advanced", ManagementServer.class, Integer.class, "network.gc.wait", "600", "Time (in seconds) to wait before shutting down a network that's not in used", null),
	NetworkGcInterval("Advanced", ManagementServer.class, Integer.class, "network.gc.interval", "600", "Seconds to wait before checking for networks to shutdown", null),
	CapacitySkipcountingHours("Advanced", ManagementServer.class, Integer.class, "capacity.skipcounting.hours", "3600", "Time (in seconds) to wait before release VM's cpu and memory when VM in stopped state", null),
	VmStatsInterval("Advanced", ManagementServer.class, Integer.class, "vm.stats.interval", "60000", "The interval (in milliseconds) when vm stats are retrieved from agents.", null),
	VmDiskStatsInterval("Advanced", ManagementServer.class, Integer.class, "vm.disk.stats.interval", "0", "Interval (in seconds) to report vm disk statistics.", null),
	VmTransitionWaitInterval("Advanced", ManagementServer.class, Integer.class, "vm.tranisition.wait.interval", "3600", "Time (in seconds) to wait before taking over a VM in transition state", null),
        VmDiskThrottlingIopsReadRate("Advanced", ManagementServer.class, Integer.class, "vm.disk.throttling.iops_read_rate", "0", "Default disk I/O read rate in requests per second allowed in User vm's disk.", null),
        VmDiskThrottlingIopsWriteRate("Advanced", ManagementServer.class, Integer.class, "vm.disk.throttling.iops_write_rate", "0", "Default disk I/O writerate in requests per second allowed in User vm's disk.", null),
        VmDiskThrottlingBytesReadRate("Advanced", ManagementServer.class, Integer.class, "vm.disk.throttling.bytes_read_rate", "0", "Default disk I/O read rate in bytes per second allowed in User vm's disk.", null),
        VmDiskThrottlingBytesWriteRate("Advanced", ManagementServer.class, Integer.class, "vm.disk.throttling.bytes_write_rate", "0", "Default disk I/O writerate in bytes per second allowed in User vm's disk.", null),

	ControlCidr("Advanced", ManagementServer.class, String.class, "control.cidr", "169.254.0.0/16", "Changes the cidr for the control network traffic.  Defaults to using link local.  Must be unique within pods", null),
	ControlGateway("Advanced", ManagementServer.class, String.class, "control.gateway", "169.254.0.1", "gateway for the control network traffic", null),
	HostCapacityTypeToOrderClusters("Advanced", ManagementServer.class, String.class, "host.capacityType.to.order.clusters", "CPU", "The host capacity type (CPU or RAM) is used by deployment planner to order clusters during VM resource allocation", "CPU,RAM"),
	ApplyAllocationAlgorithmToPods("Advanced", ManagementServer.class, Boolean.class, "apply.allocation.algorithm.to.pods", "false", "If true, deployment planner applies the allocation heuristics at pods first in the given datacenter during VM resource allocation", "true,false"),
	VmUserDispersionWeight("Advanced", ManagementServer.class, Float.class, "vm.user.dispersion.weight", "1", "Weight for user dispersion heuristic (as a value between 0 and 1) applied to resource allocation during vm deployment. Weight for capacity heuristic will be (1 - weight of user dispersion)", null),
    VmAllocationAlgorithm("Advanced", ManagementServer.class, String.class, "vm.allocation.algorithm", "random", "'random', 'firstfit', 'userdispersing', 'userconcentratedpod_random', 'userconcentratedpod_firstfit' : Order in which hosts within a cluster will be considered for VM/volume allocation.", null),
    VmDeploymentPlanner("Advanced", ManagementServer.class, String.class, "vm.deployment.planner", "FirstFitPlanner", "'FirstFitPlanner', 'UserDispersingPlanner', 'UserConcentratedPodPlanner': DeploymentPlanner heuristic that will be used for VM deployment.", null),
	EndpointeUrl("Advanced", ManagementServer.class, String.class, "endpointe.url", "http://localhost:8080/client/api", "Endpointe Url", null),
	ElasticLoadBalancerEnabled("Advanced", ManagementServer.class, String.class, "network.loadbalancer.basiczone.elb.enabled", "false", "Whether the load balancing service is enabled for basic zones", "true,false"),
	ElasticLoadBalancerNetwork("Advanced", ManagementServer.class, String.class, "network.loadbalancer.basiczone.elb.network", "guest", "Whether the elastic load balancing service public ips are taken from the public or guest network", "guest,public"),
	ElasticLoadBalancerVmMemory("Advanced", ManagementServer.class, Integer.class, "network.loadbalancer.basiczone.elb.vm.ram.size", "128", "Memory in MB for the elastic load balancer vm", null),
    ElasticLoadBalancerVmCpuMhz("Advanced", ManagementServer.class, Integer.class, "network.loadbalancer.basiczone.elb.vm.cpu.mhz", "128", "CPU speed for the elastic load balancer vm", null),
    ElasticLoadBalancerVmNumVcpu("Advanced", ManagementServer.class, Integer.class, "network.loadbalancer.basiczone.elb.vm.vcpu.num", "1", "Number of VCPU  for the elastic load balancer vm", null),
    ElasticLoadBalancerVmGcInterval("Advanced", ManagementServer.class, Integer.class, "network.loadbalancer.basiczone.elb.gc.interval.minutes", "30", "Garbage collection interval to destroy unused ELB vms in minutes. Minimum of 5", null),
    SortKeyAlgorithm("Advanced", ManagementServer.class, Boolean.class, "sortkey.algorithm", "false", "Sort algorithm for those who use sort key(template, disk offering, service offering, network offering), true means ascending sort while false means descending sort", null),
    EnableEC2API("Advanced", ManagementServer.class, Boolean.class, "enable.ec2.api", "false", "enable EC2 API on CloudStack", null),
    EnableS3API("Advanced", ManagementServer.class, Boolean.class, "enable.s3.api", "false", "enable Amazon S3 API on CloudStack", null),
    RecreateSystemVmEnabled("Advanced", ManagementServer.class, Boolean.class, "recreate.systemvm.enabled", "false", "If true, will recreate system vm root disk whenever starting system vm", "true,false"),
    SetVmInternalNameUsingDisplayName("Advanced", ManagementServer.class, Boolean.class, "vm.instancename.flag", "false",
            "If set to true, will set guest VM's name as it appears on the hypervisor, to its hostname", "true,false"),
    IncorrectLoginAttemptsAllowed("Advanced", ManagementServer.class, Integer.class, "incorrect.login.attempts.allowed", "5", "Incorrect login attempts allowed before the user is disabled", null),
    // Ovm
    OvmPublicNetwork("Hidden", ManagementServer.class, String.class, "ovm.public.network.device", null, "Specify the public bridge on host for public network", null),
    OvmPrivateNetwork("Hidden", ManagementServer.class, String.class, "ovm.private.network.device", null, "Specify the private bridge on host for private network", null),
    OvmGuestNetwork("Hidden", ManagementServer.class, String.class, "ovm.guest.network.device", null, "Specify the private bridge on host for private network", null),

	// XenServer
    XenPublicNetwork("Hidden", ManagementServer.class, String.class, "xen.public.network.device", null, "[ONLY IF THE PUBLIC NETWORK IS ON A DEDICATED NIC]:The network name label of the physical device dedicated to the public network on a XenServer host", null),
    XenStorageNetwork1("Hidden", ManagementServer.class, String.class, "xen.storage.network.device1", null, "Specify when there are storage networks", null),
    XenStorageNetwork2("Hidden", ManagementServer.class, String.class, "xen.storage.network.device2", null, "Specify when there are storage networks", null),
    XenPrivateNetwork("Hidden", ManagementServer.class, String.class, "xen.private.network.device", null, "Specify when the private network name is different", null),
    NetworkGuestCidrLimit("Network", NetworkOrchestrationService.class, Integer.class, "network.guest.cidr.limit", "22", "size limit for guest cidr; can't be less than this value", null),
    XenSetupMultipath("Advanced", ManagementServer.class, String.class, "xen.setup.multipath", "false", "Setup the host to do multipath", null),
    XenBondStorageNic("Advanced", ManagementServer.class, String.class, "xen.bond.storage.nics", null, "Attempt to bond the two networks if found", null),
    XenHeartBeatInterval("Advanced", ManagementServer.class, Integer.class, "xen.heartbeat.interval", "60", "heartbeat to use when implementing XenServer Self Fencing", null),
    XenGuestNetwork("Hidden", ManagementServer.class, String.class, "xen.guest.network.device", null, "Specify for guest network name label", null),
    XenMaxNics("Advanced", AgentManager.class, Integer.class, "xen.nics.max", "7", "Maximum allowed nics for Vms created on Xen", null),
    XenPVdriverVersion("Advanced", ManagementServer.class, String.class, "xen.pvdriver.version", "xenserver61", "default Xen PV driver version for registered template, valid value:xenserver56,xenserver61 ", "xenserver56,xenserver61"),
    XenServerHotFix("Advanced", ManagementServer.class, Boolean.class, "xen.hotfix.enabled", "false", "Enable/Disable xenserver hot fix", null),

    // VMware
    VmwareUseNexusVSwitch("Network", ManagementServer.class, Boolean.class, "vmware.use.nexus.vswitch", "false", "Enable/Disable Cisco Nexus 1000v vSwitch in VMware environment", null),
    VmwareUseDVSwitch("Network", ManagementServer.class, Boolean.class, "vmware.use.dvswitch", "false", "Enable/Disable Nexus/Vmware dvSwitch in VMware environment", null),
    VmwarePortsPerDVPortGroup("Network", ManagementServer.class, Integer.class, "vmware.ports.per.dvportgroup", "256", "Default number of ports per Vmware dvPortGroup in VMware environment", null),
    VmwareCreateFullClone("Advanced", ManagementServer.class, Boolean.class, "vmware.create.full.clone", "true", "If set to true, creates guest VMs as full clones on ESX", null),
    VmwareServiceConsole("Advanced", ManagementServer.class, String.class, "vmware.service.console", "Service Console", "Specify the service console network name(for ESX hosts)", null),
    VmwareManagementPortGroup("Advanced", ManagementServer.class, String.class, "vmware.management.portgroup", "Management Network", "Specify the management network name(for ESXi hosts)", null),
    VmwareAdditionalVncPortRangeStart("Advanced", ManagementServer.class, Integer.class, "vmware.additional.vnc.portrange.start", "50000", "Start port number of additional VNC port range", null),
    VmwareAdditionalVncPortRangeSize("Advanced", ManagementServer.class, Integer.class, "vmware.additional.vnc.portrange.size", "1000", "Start port number of additional VNC port range", null),
    //VmwareGuestNicDeviceType("Advanced", ManagementServer.class, String.class, "vmware.guest.nic.device.type", "E1000", "Ethernet card type used in guest VM, valid values are E1000, PCNet32, Vmxnet2, Vmxnet3", null),
    VmwareReserveCpu("Advanced", ManagementServer.class, Boolean.class, "vmware.reserve.cpu", "false", "Specify whether or not to reserve CPU based on CPU overprovisioning factor", null),
    VmwareReserveMem("Advanced", ManagementServer.class, Boolean.class, "vmware.reserve.mem", "false", "Specify whether or not to reserve memory based on memory overprovisioning factor", null),
    VmwareRootDiskControllerType("Advanced", ManagementServer.class, String.class, "vmware.root.disk.controller", "ide", "Specify the default disk controller for root volumes, valid values are scsi, ide", null),
    VmwareSystemVmNicDeviceType("Advanced", ManagementServer.class, String.class, "vmware.systemvm.nic.device.type", "E1000", "Specify the default network device type for system VMs, valid values are E1000, PCNet32, Vmxnet2, Vmxnet3", null),
    VmwareRecycleHungWorker("Advanced", ManagementServer.class, Boolean.class, "vmware.recycle.hung.wokervm", "false", "Specify whether or not to recycle hung worker VMs", null),
    VmwareHungWorkerTimeout("Advanced", ManagementServer.class, Long.class, "vmware.hung.wokervm.timeout", "7200", "Worker VM timeout in seconds", null),
    VmwareEnableNestedVirtualization("Advanced", ManagementServer.class, Boolean.class, "vmware.nested.virtualization", "false", "When set to true this will enable nested virtualization when this is supported by the hypervisor", null),
    VmwareVcenterSessionTimeout("Advanced", ManagementServer.class, Long.class, "vmware.vcenter.session.timeout", "1200", "VMware client timeout in seconds", null),

    // Midonet
    MidoNetAPIServerAddress("Network", ManagementServer.class, String.class, "midonet.apiserver.address", "http://localhost:8081", "Specify the address at which the Midonet API server can be contacted (if using Midonet)", null),
    MidoNetProviderRouterId("Network", ManagementServer.class, String.class, "midonet.providerrouter.id", "d7c5e6a3-e2f4-426b-b728-b7ce6a0448e5", "Specifies the UUID of the Midonet provider router (if using Midonet)", null),

    // KVM
    KvmPublicNetwork("Hidden", ManagementServer.class, String.class, "kvm.public.network.device", null, "Specify the public bridge on host for public network", null),
    KvmPrivateNetwork("Hidden", ManagementServer.class, String.class, "kvm.private.network.device", null, "Specify the private bridge on host for private network", null),
    KvmGuestNetwork("Hidden", ManagementServer.class, String.class, "kvm.guest.network.device", null, "Specify the private bridge on host for private network", null),
    KvmSshToAgentEnabled("Advanced", ManagementServer.class, Boolean.class, "kvm.ssh.to.agent", "true", "Specify whether or not the management server is allowed to SSH into KVM Agents", null),
    
    // Hyperv
    HypervPublicNetwork("Hidden", ManagementServer.class, String.class, "hyperv.public.network.device", null, "Specify the public virtual switch on host for public network", null),
    HypervPrivateNetwork("Hidden", ManagementServer.class, String.class, "hyperv.private.network.device", null, "Specify the virtual switch on host for private network", null),
    HypervGuestNetwork("Hidden", ManagementServer.class, String.class, "hyperv.guest.network.device", null, "Specify the virtual switch on host for guest network", null),

	// Usage
	UsageExecutionTimezone("Usage", ManagementServer.class, String.class, "usage.execution.timezone", null, "The timezone to use for usage job execution time", null),
	UsageStatsJobAggregationRange("Usage", ManagementServer.class, Integer.class, "usage.stats.job.aggregation.range", "1440", "The range of time for aggregating the user statistics specified in minutes (e.g. 1440 for daily, 60 for hourly.", null),
	UsageStatsJobExecTime("Usage", ManagementServer.class, String.class, "usage.stats.job.exec.time", "00:15", "The time at which the usage statistics aggregation job will run as an HH24:MM time, e.g. 00:30 to run at 12:30am.", null),
    EnableUsageServer("Usage", ManagementServer.class, Boolean.class, "enable.usage.server", "true", "Flag for enabling usage", null),
    DirectNetworkStatsInterval("Usage", ManagementServer.class, Integer.class, "direct.network.stats.interval", "86400", "Interval (in seconds) to collect stats from Traffic Monitor", null),
    UsageSanityCheckInterval("Usage", ManagementServer.class, Integer.class, "usage.sanity.check.interval", null, "Interval (in days) to check sanity of usage data", null),
    UsageAggregationTimezone("Usage", ManagementServer.class, String.class, "usage.aggregation.timezone", "GMT", "The timezone to use for usage stats aggregation", null),
    TrafficSentinelIncludeZones("Usage", ManagementServer.class, Integer.class, "traffic.sentinel.include.zones", "EXTERNAL", "Traffic going into specified list of zones is metered. For metering all traffic leave this parameter empty", null),
    TrafficSentinelExcludeZones("Usage", ManagementServer.class, Integer.class, "traffic.sentinel.exclude.zones", "", "Traffic going into specified list of zones is not metered.", null),

	// Hidden
	UseSecondaryStorageVm("Hidden", ManagementServer.class, Boolean.class, "secondary.storage.vm", "false", "Deploys a VM per zone to manage secondary storage if true, otherwise secondary storage is mounted on management server", null),
    CreatePoolsInPod("Hidden", ManagementServer.class, Boolean.class, "xen.create.pools.in.pod", "false", "Should we automatically add XenServers into pools that are inside a Pod", null),
    CloudIdentifier("Hidden", ManagementServer.class, String.class, "cloud.identifier", null, "A unique identifier for the cloud.", null),
    SSOKey("Secure", ManagementServer.class, String.class, "security.singlesignon.key", null, "A Single Sign-On key used for logging into the cloud", null),
    SSOAuthTolerance("Advanced", ManagementServer.class, Long.class, "security.singlesignon.tolerance.millis", "300000", "The allowable clock difference in milliseconds between when an SSO login request is made and when it is received.", null),
	//NetworkType("Hidden", ManagementServer.class, String.class, "network.type", "vlan", "The type of network that this deployment will use.", "vlan,direct"),
	HashKey("Hidden", ManagementServer.class, String.class, "security.hash.key", null, "for generic key-ed hash", null),
	EncryptionKey("Hidden", ManagementServer.class, String.class, "security.encryption.key", null, "base64 encoded key data", null),
	EncryptionIV("Hidden", ManagementServer.class, String.class, "security.encryption.iv", null, "base64 encoded IV data", null),
	RouterRamSize("Hidden", NetworkOrchestrationService.class, Integer.class, "router.ram.size", "128", "Default RAM for router VM (in MB).", null),

	DefaultPageSize("Advanced", ManagementServer.class, Long.class, "default.page.size", "500", "Default page size for API list* commands", null),

	TaskCleanupRetryInterval("Advanced", ManagementServer.class, Integer.class, "task.cleanup.retry.interval", "600", "Time (in seconds) to wait before retrying cleanup of tasks if the cleanup failed previously.  0 means to never retry.", "Seconds"),

	// Account Default Limits
	DefaultMaxAccountUserVms("Account Defaults", ManagementServer.class, Long.class, "max.account.user.vms", "20", "The default maximum number of user VMs that can be deployed for an account", null),
	DefaultMaxAccountPublicIPs("Account Defaults", ManagementServer.class, Long.class, "max.account.public.ips", "20", "The default maximum number of public IPs that can be consumed by an account", null),
	DefaultMaxAccountTemplates("Account Defaults", ManagementServer.class, Long.class, "max.account.templates", "20", "The default maximum number of templates that can be deployed for an account", null),
	DefaultMaxAccountSnapshots("Account Defaults", ManagementServer.class, Long.class, "max.account.snapshots", "20", "The default maximum number of snapshots that can be created for an account", null),
	DefaultMaxAccountVolumes("Account Defaults", ManagementServer.class, Long.class, "max.account.volumes", "20", "The default maximum number of volumes that can be created for an account", null),
	DefaultMaxAccountNetworks("Account Defaults", ManagementServer.class, Long.class, "max.account.networks", "20", "The default maximum number of networks that can be created for an account", null),
	DefaultMaxAccountVpcs("Account Defaults", ManagementServer.class, Long.class, "max.account.vpcs", "20", "The default maximum number of vpcs that can be created for an account", null),
	DefaultMaxAccountCpus("Account Defaults", ManagementServer.class, Long.class, "max.account.cpus", "40", "The default maximum number of cpu cores that can be used for an account", null),
	DefaultMaxAccountMemory("Account Defaults", ManagementServer.class, Long.class, "max.account.memory", "40960", "The default maximum memory (in MB) that can be used for an account", null),
	DefaultMaxAccountPrimaryStorage("Account Defaults", ManagementServer.class, Long.class, "max.account.primary.storage", "200", "The default maximum primary storage space (in GiB) that can be used for an account", null),
	DefaultMaxAccountSecondaryStorage("Account Defaults", ManagementServer.class, Long.class, "max.account.secondary.storage", "400", "The default maximum secondary storage space (in GiB) that can be used for an account", null),

	ResourceCountCheckInterval("Advanced", ManagementServer.class, Long.class, "resourcecount.check.interval", "0", "Time (in seconds) to wait before retrying resource count check task. Default is 0 which is to never run the task", "Seconds"),

	//disabling lb as cluster sync does not work with distributed cluster
	SubDomainNetworkAccess("Advanced", NetworkOrchestrationService.class, Boolean.class, "allow.subdomain.network.access", "true", "Allow subdomains to use networks dedicated to their parent domain(s)", null),
	EncodeApiResponse("Advanced", ManagementServer.class, Boolean.class, "encode.api.response", "false", "Do URL encoding for the api response, false by default", null),
	DnsBasicZoneUpdates("Advanced", NetworkOrchestrationService.class, String.class, "network.dns.basiczone.updates", "all", "This parameter can take 2 values: all (default) and pod. It defines if DHCP/DNS requests have to be send to all dhcp servers in cloudstack, or only to the one in the same pod", "all,pod"),

	ClusterMessageTimeOutSeconds("Advanced", ManagementServer.class, Integer.class, "cluster.message.timeout.seconds", "300", "Time (in seconds) to wait before a inter-management server message post times out.", null),
	AgentLoadThreshold("Advanced", ManagementServer.class, Float.class, "agent.load.threshold", "0.7", "Percentage (as a value between 0 and 1) of connected agents after which agent load balancing will start happening", null),

	JavaScriptDefaultContentType("Advanced", ManagementServer.class, String.class, "json.content.type", "text/javascript", "Http response content type for .js files (default is text/javascript)", null),

	DefaultMaxProjectUserVms("Project Defaults", ManagementServer.class, Long.class, "max.project.user.vms", "20", "The default maximum number of user VMs that can be deployed for a project", null),
    DefaultMaxProjectPublicIPs("Project Defaults", ManagementServer.class, Long.class, "max.project.public.ips", "20", "The default maximum number of public IPs that can be consumed by a project", null),
    DefaultMaxProjectTemplates("Project Defaults", ManagementServer.class, Long.class, "max.project.templates", "20", "The default maximum number of templates that can be deployed for a project", null),
    DefaultMaxProjectSnapshots("Project Defaults", ManagementServer.class, Long.class, "max.project.snapshots", "20", "The default maximum number of snapshots that can be created for a project", null),
    DefaultMaxProjectVolumes("Project Defaults", ManagementServer.class, Long.class, "max.project.volumes", "20", "The default maximum number of volumes that can be created for a project", null),
    DefaultMaxProjectNetworks("Project Defaults", ManagementServer.class, Long.class, "max.project.networks", "20", "The default maximum number of networks that can be created for a project", null),
    DefaultMaxProjectVpcs("Project Defaults", ManagementServer.class, Long.class, "max.project.vpcs", "20", "The default maximum number of vpcs that can be created for a project", null),
    DefaultMaxProjectCpus("Project Defaults", ManagementServer.class, Long.class, "max.project.cpus", "40", "The default maximum number of cpu cores that can be used for a project", null),
    DefaultMaxProjectMemory("Project Defaults", ManagementServer.class, Long.class, "max.project.memory", "40960", "The default maximum memory (in MB) that can be used for a project", null),
    DefaultMaxProjectPrimaryStorage("Project Defaults", ManagementServer.class, Long.class, "max.project.primary.storage", "200", "The default maximum primary storage space (in GiB) that can be used for an project", null),
    DefaultMaxProjectSecondaryStorage("Project Defaults", ManagementServer.class, Long.class, "max.project.secondary.storage", "400", "The default maximum secondary storage space (in GiB) that can be used for an project", null),

    ProjectInviteRequired("Project Defaults", ManagementServer.class, Boolean.class, "project.invite.required", "false", "If invitation confirmation is required when add account to project. Default value is false", null),
    ProjectInvitationExpirationTime("Project Defaults", ManagementServer.class, Long.class, "project.invite.timeout", "86400", "Invitation expiration time (in seconds). Default is 1 day - 86400 seconds", null),
    AllowUserToCreateProject("Project Defaults", ManagementServer.class, Long.class, "allow.user.create.projects", "true", "If regular user can create a project; true by default", null),

    ProjectEmailSender("Project Defaults", ManagementServer.class, String.class, "project.email.sender", null, "Sender of project invitation email (will be in the From header of the email)", null),
    ProjectSMTPHost("Project Defaults", ManagementServer.class, String.class, "project.smtp.host", null, "SMTP hostname used for sending out email project invitations", null),
    ProjectSMTPPassword("Secure", ManagementServer.class, String.class, "project.smtp.password", null, "Password for SMTP authentication (applies only if project.smtp.useAuth is true)", null),
    ProjectSMTPPort("Project Defaults", ManagementServer.class, Integer.class, "project.smtp.port", "465", "Port the SMTP server is listening on", null),
    ProjectSMTPUseAuth("Project Defaults", ManagementServer.class, String.class, "project.smtp.useAuth", null, "If true, use SMTP authentication when sending emails", null),
    ProjectSMTPUsername("Project Defaults", ManagementServer.class, String.class, "project.smtp.username", null, "Username for SMTP authentication (applies only if project.smtp.useAuth is true)", null),

    DefaultExternalLoadBalancerCapacity("Advanced", ManagementServer.class, String.class, "external.lb.default.capacity", "50", "default number of networks permitted per external load balancer device", null),
    DefaultExternalFirewallCapacity("Advanced", ManagementServer.class, String.class, "external.firewall.default.capacity", "50", "default number of networks permitted per external load firewall device", null),
    EIPWithMultipleNetScalersEnabled("Advanced", ManagementServer.class, Boolean.class, "eip.use.multiple.netscalers", "false", "Should be set to true, if there will be multiple NetScaler devices providing EIP service in a zone", null),
	ConsoleProxyServiceOffering("Advanced", ManagementServer.class, Long.class, "consoleproxy.service.offering", null, "Uuid of the service offering used by console proxy; if NULL - system offering will be used", null),
	SecondaryStorageServiceOffering("Advanced", ManagementServer.class, Long.class, "secstorage.service.offering", null, "Service offering used by secondary storage; if NULL - system offering will be used", null),
	HaTag("Advanced", ManagementServer.class, String.class, "ha.tag", null, "HA tag defining that the host marked with this tag can be used for HA purposes only", null),
	VpcCleanupInterval("Advanced", ManagementServer.class, Integer.class, "vpc.cleanup.interval", "3600", "The interval (in seconds) between cleanup for Inactive VPCs", null),
    VpcMaxNetworks("Advanced", ManagementServer.class, Integer.class, "vpc.max.networks", "3", "Maximum number of networks per vpc", null),
    DetailBatchQuerySize("Advanced", ManagementServer.class, Integer.class, "detail.batch.query.size", "2000", "Default entity detail batch query size for listing", null),
	ConcurrentSnapshotsThresholdPerHost("Advanced", ManagementServer.class, Long.class, "concurrent.snapshots.threshold.perhost",
	                null, "Limits number of snapshots that can be handled by the host concurrently; default is NULL - unlimited", null),
	NetworkIPv6SearchRetryMax("Network", ManagementServer.class, Integer.class, "network.ipv6.search.retry.max", "10000", "The maximum number of retrying times to search for an available IPv6 address in the table", null),

	ExternalBaremetalSystemUrl("Advanced", ManagementServer.class, String.class, "external.baremetal.system.url", null, "url of external baremetal system that CloudStack will talk to", null),
	ExternalBaremetalResourceClassName("Advanced", ManagementServer.class, String.class, "external.baremetal.resource.classname", null, "class name for handling external baremetal resource", null),
	EnableBaremetalSecurityGroupAgentEcho("Advanced", ManagementServer.class, Boolean.class, "enable.baremetal.securitygroup.agent.echo", "false", "After starting provision process, periodcially echo security agent installed in the template. Treat provisioning as success only if echo successfully", null),
	IntervalToEchoBaremetalSecurityGroupAgent("Advanced", ManagementServer.class, Integer.class, "interval.baremetal.securitygroup.agent.echo", "10", "Interval to echo baremetal security group agent, in seconds", null),
	TimeoutToEchoBaremetalSecurityGroupAgent("Advanced", ManagementServer.class, Integer.class, "timeout.baremetal.securitygroup.agent.echo", "3600", "Timeout to echo baremetal security group agent, in seconds, the provisioning process will be treated as a failure", null),
    BaremetalIpmiLanInterface("Advanced", ManagementServer.class, String.class, "baremetal.ipmi.lan.interface", "default", "option specified in -I option of impitool. candidates are: open/bmc/lipmi/lan/lanplus/free/imb, see ipmitool man page for details. default valule 'default' means using default option of ipmitool", null),
    BaremetalIpmiRetryTimes("Advanced", ManagementServer.class, String.class, "baremetal.ipmi.fail.retry", "5", "ipmi interface will be temporary out of order after power opertions(e.g. cycle, on), it leads following commands fail immediately. The value specifies retry times before accounting it as real failure", null),

    ApiLimitEnabled("Advanced", ManagementServer.class, Boolean.class, "api.throttling.enabled", "false", "Enable/disable Api rate limit", null),
	ApiLimitInterval("Advanced", ManagementServer.class, Integer.class, "api.throttling.interval", "1", "Time interval (in seconds) to reset API count", null),
    ApiLimitMax("Advanced", ManagementServer.class, Integer.class, "api.throttling.max", "25", "Max allowed number of APIs within fixed interval", null),
    ApiLimitCacheSize("Advanced", ManagementServer.class, Integer.class, "api.throttling.cachesize", "50000", "Account based API count cache size", null),

    // object store
    S3EnableRRS("Advanced", ManagementServer.class, Boolean.class, "s3.rrs.enabled", "false", "enable s3 reduced redundancy storage", null),
    S3MaxSingleUploadSize("Advanced",  ManagementServer.class, Integer.class, "s3.singleupload.max.size", "5", "The maximum size limit for S3 single part upload API(in GB). If it is set to 0, then it means always use multi-part upload to upload object to S3. " +
            "If it is set to -1, then it means always use single-part upload to upload object to S3. ", null),

    // Ldap
    LdapBasedn("Advanced", ManagementServer.class, String.class, "ldap.basedn", null, "Sets the basedn for LDAP", null),
    LdapBindPassword("Advanced", ManagementServer.class, String.class, "ldap.bind.password", null, "Sets the bind password for LDAP", null),
    LdapBindPrincipal("Advanced", ManagementServer.class, String.class, "ldap.bind.principal", null, "Sets the bind principal for LDAP", null),
    LdapEmailAttribute("Advanced", ManagementServer.class, String.class, "ldap.email.attribute", "mail", "Sets the email attribute used within LDAP", null),
    LdapFirstnameAttribute("Advanced", ManagementServer.class, String.class, "ldap.firstname.attribute", "givenname", "Sets the firstname attribute used within LDAP", null),
    LdapLastnameAttribute("Advanced", ManagementServer.class, String.class, "ldap.lastname.attribute", "sn", "Sets the lastname attribute used within LDAP", null),
    LdapUsernameAttribute("Advanced", ManagementServer.class, String.class, "ldap.username.attribute", "uid", "Sets the username attribute used within LDAP", null),
    LdapUserObject("Advanced", ManagementServer.class, String.class, "ldap.user.object", "inetOrgPerson", "Sets the object type of users within LDAP", null),
    LdapSearchGroupPrinciple("Advanced", ManagementServer.class, String.class, "ldap.search.group.principle", null, "Sets the principle of the group that users must be a member of", null),
    LdapTrustStore("Advanced", ManagementServer.class, String.class, "ldap.truststore", null, "Sets the path to the truststore to use for SSL", null),
    LdapTrustStorePassword("Advanced", ManagementServer.class, String.class, "ldap.truststore.password", null, "Sets the password for the truststore", null),
    LdapGroupObject("Advanced", ManagementServer.class, String.class, "ldap.group.object", "groupOfUniqueNames", "Sets the object type of groups within LDAP", null),
    LdapGroupUniqueMemberAttribute("Advanced", ManagementServer.class, String.class, "ldap.group.user.uniquemember", "uniquemember",
				   "Sets the attribute for uniquemembers within a group", null),

	// VMSnapshots
    VMSnapshotMax("Advanced", VMSnapshotManager.class, Integer.class, "vmsnapshot.max", "10", "Maximum vm snapshots for a vm", null),
    VMSnapshotCreateWait("Advanced", VMSnapshotManager.class, Integer.class, "vmsnapshot.create.wait", "1800", "In second, timeout for create vm snapshot", null),

    CloudDnsName("Advanced", ManagementServer.class, String.class, "cloud.dns.name", null, "DNS name of the cloud for the GSLB service", null),

    BlacklistedRoutes("Advanced", VpcManager.class, String.class, "blacklisted.routes", null, "Routes that are blacklisted, can not be used for Static Routes creation for the VPC Private Gateway",
	           "routes", ConfigKey.Scope.Zone.toString()),
    InternalLbVmServiceOfferingId("Advanced", ManagementServer.class, String.class, "internallbvm.service.offering", null, "Uuid of the service offering used by internal lb vm; if NULL - default system internal lb offering will be used", null),
    ExecuteInSequenceNetworkElementCommands("Advanced", NetworkOrchestrationService.class, Boolean.class, "execute.in.sequence.network.element.commands", "false", "If set to true, DhcpEntryCommand, SavePasswordCommand, UserDataCommand, VmDataCommand will be synchronized on the agent side." +
            " If set to false, these commands become asynchronous. Default value is false.", null),

	UCSSyncBladeInterval("Advanced", ManagementServer.class, Integer.class, "ucs.sync.blade.interval", "3600", "the interval cloudstack sync with UCS manager for available blades in case user remove blades from chassis without notifying CloudStack", null),

    ManagementServerVendor("Advanced", ManagementServer.class, String.class, "mgt.server.vendor", "ACS", "the vendor of management server", null);

    private final String _category;
	private final Class<?> _componentClass;
	private final Class<?> _type;
    private final String _name;
    private final String _defaultValue;
    private final String _description;
    private final String _range;
    private final String _scope; // Parameter can be at different levels (Zone/cluster/pool/account), by default every parameter is at global

    private static final HashMap<String, List<Config>> _scopeLevelConfigsMap = new HashMap<String, List<Config>>();
    static {
        _scopeLevelConfigsMap.put(ConfigKey.Scope.Zone.toString(), new ArrayList<Config>());
        _scopeLevelConfigsMap.put(ConfigKey.Scope.Cluster.toString(), new ArrayList<Config>());
        _scopeLevelConfigsMap.put(ConfigKey.Scope.StoragePool.toString(), new ArrayList<Config>());
        _scopeLevelConfigsMap.put(ConfigKey.Scope.Account.toString(), new ArrayList<Config>());
        _scopeLevelConfigsMap.put(ConfigKey.Scope.Global.toString(), new ArrayList<Config>());

        for (Config c : Config.values()) {
            //Creating group of parameters per each level (zone/cluster/pool/account)
            StringTokenizer tokens = new StringTokenizer(c.getScope(), ",");
            while (tokens.hasMoreTokens()) {
                String scope = tokens.nextToken().trim();
                List<Config> currentConfigs = _scopeLevelConfigsMap.get(scope);
                currentConfigs.add(c);
                _scopeLevelConfigsMap.put(scope, currentConfigs);
            }
        }
    }

    private static final HashMap<String, List<Config>> _configs = new HashMap<String, List<Config>>();
    static {
    	// Add categories
    	_configs.put("Alert", new ArrayList<Config>());
    	_configs.put("Storage", new ArrayList<Config>());
    	_configs.put("Snapshots", new ArrayList<Config>());
    	_configs.put("Network", new ArrayList<Config>());
    	_configs.put("Usage", new ArrayList<Config>());
    	_configs.put("Console Proxy", new ArrayList<Config>());
    	_configs.put("Advanced", new ArrayList<Config>());
    	_configs.put("Usage", new ArrayList<Config>());
    	_configs.put("Developer", new ArrayList<Config>());
    	_configs.put("Hidden", new ArrayList<Config>());
    	_configs.put("Account Defaults", new ArrayList<Config>());
    	_configs.put("Project Defaults", new ArrayList<Config>());
    	_configs.put("Secure", new ArrayList<Config>());

    	// Add values into HashMap
        for (Config c : Config.values()) {
        	String category = c.getCategory();
        	List<Config> currentConfigs = _configs.get(category);
        	currentConfigs.add(c);
        	_configs.put(category, currentConfigs);
        }
    }

    private Config(String category, Class<?> componentClass, Class<?> type, String name, String defaultValue, String description, String range) {
    	_category = category;
    	_componentClass = componentClass;
    	_type = type;
    	_name = name;
    	_defaultValue = defaultValue;
    	_description = description;
    	_range = range;
        _scope = ConfigKey.Scope.Global.toString();
    }
    private Config(String category, Class<?> componentClass, Class<?> type, String name, String defaultValue, String description, String range, String scope) {
        _category = category;
        _componentClass = componentClass;
        _type = type;
        _name = name;
        _defaultValue = defaultValue;
        _description = description;
        _range = range;
        _scope = scope;
    }

    public String getCategory() {
    	return _category;
    }

    public String key() {
        return _name;
    }

    public String getDescription() {
        return _description;
    }

    public String getDefaultValue() {
        return _defaultValue;
    }

    public Class<?> getType() {
        return _type;
    }

    public Class<?> getComponentClass() {
        return _componentClass;
    }

    public String getScope() {
        return _scope;
    }

    public String getComponent() {
    	if (_componentClass == ManagementServer.class) {
            return "management-server";
        } else if (_componentClass == AgentManager.class) {
            return "AgentManager";
        } else if (_componentClass == UserVmManager.class) {
            return "UserVmManager";
        } else if (_componentClass == HighAvailabilityManager.class) {
            return "HighAvailabilityManager";
        } else if (_componentClass == StoragePoolAllocator.class) {
            return "StorageAllocator";
        } else if (_componentClass == NetworkOrchestrationService.class) {
            return "NetworkManager";
        } else if (_componentClass == StorageManager.class) {
            return "StorageManager";
        } else if (_componentClass == TemplateManager.class) {
            return "TemplateManager";
        } else if (_componentClass == VpcManager.class) {
            return "VpcManager";
        } else if (_componentClass == SnapshotManager.class) {
            return "SnapshotManager";
        } else if (_componentClass == VMSnapshotManager.class) {
            return "VMSnapshotManager";
        } else {
            return "none";
        }
    }

    public String getRange() {
        return _range;
    }

    @Override
	public String toString() {
        return _name;
    }

    public static List<Config> getConfigs(String category) {
    	return _configs.get(category);
    }

    public static Config getConfig(String name) {
    	List<String> categories = getCategories();
    	for (String category : categories) {
    		List<Config> currentList = getConfigs(category);
    		for (Config c : currentList) {
    			if (c.key().equals(name)) {
                    return c;
                }
    		}
    	}

    	return null;
    }

    public static List<String> getCategories() {
    	Object[] keys = _configs.keySet().toArray();
    	List<String> categories = new ArrayList<String>();
    	for (Object key : keys) {
    		categories.add((String) key);
    	}
    	return categories;
    }

    public static List<Config> getConfigListByScope(String scope) {
        return _scopeLevelConfigsMap.get(scope);
    }
}

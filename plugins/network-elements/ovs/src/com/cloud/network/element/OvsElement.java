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
package com.cloud.network.element;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.ejb.Local;
import javax.inject.Inject;

import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.Networks;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.ovs.OvsTunnelManager;
import com.cloud.offering.NetworkOffering;
import com.cloud.utils.component.AdapterBase;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;

@Local(value = {NetworkElement.class})
public class OvsElement extends AdapterBase implements NetworkElement {
    @Inject
    OvsTunnelManager _ovsTunnelMgr;

    @Override
    public boolean destroy(Network network, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public Map<Service, Map<Capability, String>> getCapabilities() {
        Map<Service, Map<Capability, String>> capabilities = new HashMap<Service, Map<Capability, String>>();
        capabilities.put(Service.Connectivity, null);
        return capabilities;
    }

    @Override
    public Provider getProvider() {
        return Network.Provider.Ovs;
    }

    @Override
    public boolean implement(Network network, NetworkOffering offering,
            DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException,
            InsufficientCapacityException {
        //Consider actually implementing the network here
    	return true;
    }

    @Override
    public boolean prepare(Network network, NicProfile nic,
            VirtualMachineProfile vm,
            DeployDestination dest, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException,
            InsufficientCapacityException {
        if (nic.getBroadcastType() != Networks.BroadcastDomainType.Vswitch) {
            return true;
        }

        if (nic.getTrafficType() != Networks.TrafficType.Guest) {
            return true;
        }

        _ovsTunnelMgr.VmCheckAndCreateTunnel(vm, network, dest);
        //_ovsTunnelMgr.applyDefaultFlow(vm.getVirtualMachine(), dest);

        return true;
    }

    @Override
    public boolean release(Network network, NicProfile nic,
            VirtualMachineProfile vm,
            ReservationContext context) throws ConcurrentOperationException,
            ResourceUnavailableException {
        if (nic.getBroadcastType() != Networks.BroadcastDomainType.Vswitch) {
            return true;
        }

        if (nic.getTrafficType() != Networks.TrafficType.Guest) {
            return true;
        }

        _ovsTunnelMgr.CheckAndDestroyTunnel(vm.getVirtualMachine(), network);
        return true;
    }

    @Override
    public boolean shutdown(Network network, ReservationContext context, boolean cleanup)
            throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean isReady(PhysicalNetworkServiceProvider provider) {
    	return true;
    }

    @Override
    public boolean shutdownProviderInstances(PhysicalNetworkServiceProvider provider, ReservationContext context)
            throws ConcurrentOperationException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean canEnableIndividualServices() {
        return false;
    }

    @Override
    public boolean verifyServicesCombination(Set<Service> services) {
        return true;
    }
}

package com.globo.networkapi.commands;

import com.cloud.agent.api.Command;

public class ActivateNetworkCmd extends Command {
	
	private long networkId;
	
	private long vlanId;

	public ActivateNetworkCmd(long vlanId, long networkId) {
		this.vlanId = vlanId;
		this.networkId = networkId;
	}
	
	@Override
	public boolean executeInSequence() {
		return false;
	}

	public long getNetworkId() {
		return networkId;
	}
	
	public long getVlanId() {
		return vlanId;
	}

}
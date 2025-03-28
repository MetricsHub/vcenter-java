package org.metricshub.vcenter;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * VCenter Java Client
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import com.vmware.vim25.HostServiceTicket;
import com.vmware.vim25.InvalidLogin;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;
import java.net.InetAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * VCenterClient class for interacting with VMware vCenter server.
 */
public final class VCenterClient {

	private static final String ENTITY_HOST_SYSTEM = "HostSystem";
	private static final String ENTITY_DATA_CENTER = "Datacenter";

	private static Supplier<Boolean> isDebugEnabled;
	private static Consumer<String> debug;

	/**
	 * Private constructor to prevent instantiation of this class
	 */
	private VCenterClient() {}

	/**
	 * Sets the debug methods to be used by the VCenterClient class.
	 * <p>
	 * The VCenterClient class may need to write debug information. However,
	 * depending on whether it runs in the context of MatsyaEngine or as a CLI,
	 * the debug will need to be handled differently.
	 * <p>
	 * In the case of MatsyaEngine, before making any further call to this class,
	 * use setDebug(MatsyaEngine::getDebugMode, MatsyaEngine::debug).
	 * <p>
	 * In the case of CLI, use setDebug() with your own methods (which will probably
	 * simply print to stdout).
	 *
	 * @param isDebugEnabledMethod The static method that returns a boolean whether the debug mode is enabled or not
	 * @param debugMethod The static method that will print the debug message somewhere
	 */
	public static void setDebug(final Supplier<Boolean> isDebugEnabledMethod, final Consumer<String> debugMethod) {
		isDebugEnabled = isDebugEnabledMethod;
		debug = debugMethod;
	}

	/**
	 * Request an authentication certificate for the specified hostname from the specified
	 * VMware vCenter server. <p>
	 * The specified hostname must be registered in VMware vCenter so we can get an authentication
	 * "token" for it. <p>
	 * To get this token, we first need to authenticate against VMware vCenter (using the good old
	 * username and password mechanism). Then, we will be able to connect to the specified hostname
	 * VMware ESX just using this "token"
	 *
	 * @param vCenterName The hostname of IP address of the VMware vCenter system
	 * @param username Credentials to connect to VMware vCenter
	 * @param password Associated password
	 * @param hostname The hostname or IP address of the ESX host that we need an authentication token for
	 * @return The authentication token in the form of a String
	 * @throws InvalidLogin when the specified username/password is... well, invalid
	 * @throws Exception when anything else happens
	 */
	public static String requestCertificate(
		final String vCenterName,
		final String username,
		final String password,
		final String hostname
	) throws InvalidLogin, Exception {
		ServiceInstance serviceInstance = null;

		try {
			// Connect to VCenter
			URL vCenterURL = new URL("https://" + vCenterName + "/sdk");
			debug.accept("Connecting to " + vCenterURL.toString() + "...");
			serviceInstance = new ServiceInstance(vCenterURL, username, password, true);

			// Try to find the specified host in VCenter

			HostSystem hostSystem = getHostSystemManagedEntity(serviceInstance, hostname);

			if (hostSystem == null) {
				throw new Exception("Unable to find host " + hostname + " in VCenter " + vCenterName);
			} else {
				HostServiceTicket ticket = hostSystem.acquireCimServicesTicket();
				return ticket.getSessionId();
			}
		} finally {
			if (serviceInstance != null) {
				serviceInstance.getServerConnection().logout();
			}
		}
	}

	/**
	 * Retrieve in VCenter the managed entity that corresponds to the specified systemName
	 * @param serviceInstance VCenter service instance
	 * @param systemName The host name to be found in VCenter
	 * @return The HostSystem instance that matches with specified systemName. <p>null if not found.
	 * @throws Exception
	 */
	private static HostSystem getHostSystemManagedEntity(final ServiceInstance serviceInstance, final String systemName)
		throws Exception {
		// Declarations
		String entityName;
		String shortEntityName;
		ManagedEntity[] managedEntities;

		// Get the root folder (we'll search things from there)
		Folder rootFolder = serviceInstance.getRootFolder();
		if (rootFolder == null) {
			throw new Exception("Couldn't get the root folder");
		}

		// First pass: Search for all managed entities in the root folder
		// Try an exact match of the managed entity name with the specified hostname (case insensitive, of course)
		InventoryNavigator inventoryNavigator = new InventoryNavigator(rootFolder);
		managedEntities = inventoryNavigator.searchManagedEntities(ENTITY_HOST_SYSTEM);
		if (managedEntities != null) {
			// We did get a list of managed entities, let's parse them
			for (ManagedEntity managedEntity : managedEntities) {
				entityName = managedEntity.getName();
				if (systemName.equalsIgnoreCase(entityName)) {
					// Found it! Return immediately the corresponding HostSystem object
					return (HostSystem) managedEntity;
				}
			}

			// Second pass: try again, but compare with a "shortened" version of the hostname of the managed entities (i.e. what before the first dot)
			if (systemName.indexOf('.') == -1) {
				// Of course, we do this 2nd pass only if the specified system name doesn't have a dot, i.e. is a short name
				for (ManagedEntity managedEntity : managedEntities) {
					entityName = managedEntity.getName();
					int dotIndex = entityName.indexOf('.');
					if (dotIndex > 1) {
						shortEntityName = entityName.substring(0, dotIndex);
						if (systemName.equalsIgnoreCase(shortEntityName)) {
							// Found it! Return immediately the corresponding HostSystem object
							return (HostSystem) managedEntity;
						}
					}
				}
			}

			// We're here, which means that we did get a list of managed entities, but none of them match with the specified hostname
			if (isDebugEnabled.get()) {
				String managedEntitiesString = "";
				for (ManagedEntity managedEntity : managedEntities) {
					managedEntitiesString = managedEntitiesString + " - " + managedEntity.getName() + "\n";
				}
				StringBuilder entityList = new StringBuilder();
				for (ManagedEntity managedEntity : managedEntities) {
					entityList.append(" - ").append(managedEntity.getName()).append("\n");
				}
				debug.accept(
					"VCenterClient: Couldn't find host " +
					systemName +
					" in the list of managed entities in VCenter " +
					serviceInstance.getServerConnection().getUrl().getHost() +
					":\n" +
					entityList
				);
				debug.accept("VCenterClient: Will now try with the IP address of " + systemName);
			}
		}

		// Third pass: Try to find the host in another way, through the Datacenter entities
		managedEntities = inventoryNavigator.searchManagedEntities(ENTITY_DATA_CENTER);
		if (null == managedEntities || managedEntities.length == 0) {
			throw new Exception("No Datacenter-type managed entity");
		}

		// And we will try that using the IP address instead of the host name
		InetAddress[] hostIPaddresses;
		try {
			hostIPaddresses = InetAddress.getAllByName(systemName);
		} catch (Exception e) {
			throw new Exception("Couldn't resolve " + systemName + " into a valid IP address");
		}

		// Go through each datacenter
		for (ManagedEntity datacenterEntity : managedEntities) {
			// Go through each IP address of the specified system name
			for (InetAddress hostIPaddress : hostIPaddresses) {
				// Use the index to find the managed entity we want, by IP address
				ManagedEntity[] managedEntitiesInDatacenter = serviceInstance
					.getSearchIndex()
					.findAllByIp((Datacenter) datacenterEntity, hostIPaddress.getHostAddress(), false);

				// If we got something, return immediately the corresponding HostSystem instance, of the first match!
				if (managedEntitiesInDatacenter != null && managedEntitiesInDatacenter.length != 0) {
					return (HostSystem) managedEntitiesInDatacenter[0];
				}
			}
		}

		return null;
	}

	/**
	 * Retrieve all managed entities of type "HostSystem" in the specified VCenter.
	 * @param vCenterName The hostname of IP address of the VMware vCenter system
	 * @param username Credentials to connect to VMware vCenter
	 * @param password Associated password
	 * @return The list of hostnames (or IP addresses) registered in the VCenter
	 * @throws InvalidLogin for bad credentials
	 * @throws Exception when anything else goes south
	 */
	public static List<String> getAllHostSystemManagedEntities(
		final String vCenterName,
		final String username,
		final String password
	) throws Exception {
		ServiceInstance serviceInstance = null;

		try {
			// Connect to VCenter
			URL vCenterURL = new URL("https://" + vCenterName + "/sdk");
			debug.accept("Connecting to " + vCenterURL.toString() + "...");
			serviceInstance = new ServiceInstance(vCenterURL, username, password, true);

			// Get the root folder (we'll search things from there)
			Folder rootFolder = serviceInstance.getRootFolder();
			if (rootFolder == null) {
				throw new Exception("Couldn't get the root folder");
			}

			// InventoryNavigator allows us to get all managed entities
			InventoryNavigator inventoryNavigator = new InventoryNavigator(rootFolder);

			// Get all entities of type "HostSystem" and return as a list of HostSystem
			return Arrays
				.stream(inventoryNavigator.searchManagedEntities(ENTITY_HOST_SYSTEM))
				.map(ManagedEntity::getName)
				.collect(Collectors.toList());
		} finally {
			if (serviceInstance != null) {
				serviceInstance.getServerConnection().logout();
			}
		}
	}
}

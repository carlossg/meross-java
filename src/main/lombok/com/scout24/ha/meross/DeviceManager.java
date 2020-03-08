package com.scout24.ha.meross;

import com.google.common.collect.Maps;
import com.scout24.ha.meross.mqtt.MQTTException;
import com.scout24.ha.meross.mqtt.MerossDevice;
import com.scout24.ha.meross.mqtt.MqttConnection;
import com.scout24.ha.meross.rest.AttachedDevice;
import com.scout24.ha.meross.rest.MerossHttpClient;

import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class DeviceManager {

	protected static final String MEROSS_BROKER_DOMAIN = "eu-iot.meross.com";
	private String email;
	private String password;

	private Map<String, MerossDevice> merossDeviceList;
	private MerossHttpClient merossHttpClient;
	private Map<String, AttachedDevice> deviceList;
	private MqttConnection connection;

	@Data
	@ToString
	static class ConnectionDetails {
		private String key;
		private long userId;
		private String token;
		private String brokerDomain;
		private int port;
		private String usertopic;
		private String clientResponseTopic;
		private String hashedPassword;
	}

	public DeviceManager(String email, String password) {
		this.email = email;
		this.password = password;
	}

	public DeviceManager initializeDevices() throws MQTTException {
		merossDeviceList = Maps.newLinkedHashMap();
		for (String deviceUuid : deviceList.keySet()) {
			final AttachedDevice attachedDevice = deviceList.get(deviceUuid);
			log.info("Found device with uuid = " + deviceUuid + " and name = " +
					attachedDevice.getDevName() + " with channels = " + attachedDevice.getChannels());
			if (attachedDevice.isOnline()) {
				MerossDevice device = new MerossDevice(attachedDevice, connection);
				device.initialize();
				merossDeviceList.put(deviceUuid, device);
			} else {
				log.info("Did not connect to broker for offline device " + deviceUuid);
			}
		}
		return this;
	}

	public Map<String, MerossDevice> getSupportedDevices() {
		return merossDeviceList;
	}

	public boolean listenToUpdates()  {
		if (merossDeviceList == null || merossDeviceList.isEmpty()) {
			log.warn("No devices fetched, not listening to any updates");
		} else {
			try {
				connection.onGlobalMessage(map -> {
					log.info("received message");
					for (String uuid : merossDeviceList.keySet()) {
						try {
							connection.subscribeToDeviceTopic(merossDeviceList.get(uuid));
						} catch (MQTTException e) {
							log.error("Error subscribing to device topic", e);
						}
					}
				});
				return true;
			} catch (Exception e) {
				log.error("Error receiving message from broker", e);
			}
		}
		return false;
	}
	public boolean connect()  {
		try {
			ConnectionDetails details = getConnectionDetails();
			log.info("Connecting to {} {} {} {}", merossHttpClient.getKey(), merossHttpClient.getUserId(),
					merossHttpClient.getToken(), MEROSS_BROKER_DOMAIN);
			connection = new MqttConnection(details.getKey(), details.getUserId(),
					details.getToken(), details.getBrokerDomain());
			connection.connectToBroker();
			connection.subscribeToTopic(details.getUsertopic());
			connection.subscribeToTopic(details.getClientResponseTopic());
		} catch (MQTTException e) {
			log.error("Error connecting ");
		}
		return true;
	}

	public ConnectionDetails getConnectionDetails()  {
		ConnectionDetails details = new ConnectionDetails();
		try {
			merossHttpClient = new MerossHttpClient(email, password);
			deviceList = merossHttpClient.mapSupportedDevices(false);
			connection = new MqttConnection(merossHttpClient.getKey(), merossHttpClient.getUserId(),
					merossHttpClient.getToken(), MEROSS_BROKER_DOMAIN);
			details.setKey(merossHttpClient.getKey());
			details.setUserId(merossHttpClient.getUserId());
			details.setToken(merossHttpClient.getToken());
			details.setBrokerDomain(MEROSS_BROKER_DOMAIN);
			details.setPort(connection.getPort());
			details.setUsertopic(connection.getUsertopic());
			details.setClientResponseTopic(connection.getClientResponseTopic());
			details.setHashedPassword(connection.getHashedPassword());
		} catch (MQTTException e) {
			log.error("Error connecting ");
		}
		return details;
	}
}

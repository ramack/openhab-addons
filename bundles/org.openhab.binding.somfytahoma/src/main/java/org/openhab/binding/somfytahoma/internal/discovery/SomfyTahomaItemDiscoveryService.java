/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.somfytahoma.internal.discovery;

import static org.openhab.binding.somfytahoma.internal.SomfyTahomaBindingConstants.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryServiceCallback;
import org.eclipse.smarthome.config.discovery.ExtendedDiscoveryService;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.somfytahoma.internal.handler.SomfyTahomaBridgeHandler;
import org.openhab.binding.somfytahoma.internal.model.*;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SomfyTahomaItemDiscoveryService} discovers rollershutters and
 * action groups associated with your TahomaLink cloud account.
 *
 * @author Ondrej Pecta - Initial contribution
 */
@NonNullByDefault
public class SomfyTahomaItemDiscoveryService extends AbstractDiscoveryService implements ExtendedDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(SomfyTahomaItemDiscoveryService.class);

    private SomfyTahomaBridgeHandler bridge;

    private @Nullable DiscoveryServiceCallback discoveryServiceCallback;

    private @Nullable ScheduledFuture<?> discoveryJob;

    private static final int DISCOVERY_TIMEOUT_SEC = 10;
    private static final int DISCOVERY_REFRESH_SEC = 1800;

    public SomfyTahomaItemDiscoveryService(SomfyTahomaBridgeHandler bridgeHandler) {
        super(DISCOVERY_TIMEOUT_SEC);
        logger.debug("Creating discovery service");
        this.bridge = bridgeHandler;
    }

    /**
     * Called on component activation.
     */
    @Override
    @Activate
    public void activate(@Nullable Map<String, @Nullable Object> configProperties) {
        super.activate(configProperties);
    }

    @Override
    @Deactivate
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public void setDiscoveryServiceCallback(DiscoveryServiceCallback discoveryServiceCallback) {
        this.discoveryServiceCallback = discoveryServiceCallback;
    }

    @Override
    protected void startBackgroundDiscovery() {
        logger.debug("Starting SomfyTahoma background discovery");

        if (discoveryJob == null || discoveryJob.isCancelled()) {
            discoveryJob = scheduler.scheduleWithFixedDelay(this::runDiscovery, 10, DISCOVERY_REFRESH_SEC,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        logger.debug("Stopping SomfyTahoma background discovery");
        if (discoveryJob != null && !discoveryJob.isCancelled()) {
            discoveryJob.cancel(true);
            discoveryJob = null;
        }
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return SUPPORTED_THING_TYPES_UIDS;
    }

    @Override
    protected void startScan() {
        runDiscovery();
    }

    private synchronized void runDiscovery() {
        logger.debug("Starting scanning for things...");

        if (bridge.getThing().getStatus().equals(ThingStatus.ONLINE)) {
            SomfyTahomaSetup setup = bridge.getSetup();

            if (setup == null) {
                return;
            }

            for (SomfyTahomaDevice device : setup.getDevices()) {
                discoverDevice(device);
            }
            for (SomfyTahomaGateway gw : setup.getGateways()) {
                gatewayDiscovered(gw);
            }

            List<SomfyTahomaActionGroup> actions = bridge.listActionGroups();

            for (SomfyTahomaActionGroup group : actions) {
                String oid = group.getOid();
                String label = group.getLabel();

                //actiongroups use oid as deviceURL
                actionGroupDiscovered(label, oid, oid);
            }
        } else {
            logger.debug("Cannot start discovery since the bridge is not online! Rescheduling...");
            scheduler.schedule(this::runDiscovery, 60, TimeUnit.SECONDS);
        }
    }

    private void discoverDevice(SomfyTahomaDevice device) {
        logger.debug("url: {}", device.getDeviceURL());
        switch (device.getUiClass()) {
            case THING_AWNING:
                deviceDiscovered(device, THING_TYPE_AWNING);
                break;
            case THING_CONTACT_SENSOR:
                deviceDiscovered(device, THING_TYPE_CONTACTSENSOR);
                break;
            case THING_CURTAIN:
                deviceDiscovered(device, THING_TYPE_CURTAIN);
                break;
            case THING_EXTERIOR_SCREEN:
                deviceDiscovered(device, THING_TYPE_EXTERIORSCREEN);
                break;
            case THING_EXTERIOR_VENETIAN_BLIND:
                deviceDiscovered(device, THING_TYPE_EXTERIORVENETIANBLIND);
                break;
            case THING_GARAGE_DOOR:
                deviceDiscovered(device, THING_TYPE_GARAGEDOOR);
                break;
            case THING_LIGHT:
                deviceDiscovered(device, THING_TYPE_LIGHT);
                break;
            case THING_LIGHT_SENSOR:
                deviceDiscovered(device, THING_TYPE_LIGHTSENSOR);
                break;
            case THING_OCCUPANCY_SENSOR:
                deviceDiscovered(device, THING_TYPE_OCCUPANCYSENSOR);
                break;
            case THING_ON_OFF:
                deviceDiscovered(device, THING_TYPE_ONOFF);
                break;
            case THING_ROLLER_SHUTTER:
                if (isSilentRollerShutter(device)) {
                    deviceDiscovered(device, THING_TYPE_ROLLERSHUTTER_SILENT);
                } else {
                    deviceDiscovered(device, THING_TYPE_ROLLERSHUTTER);
                }
                break;
            case THING_SCREEN:
                deviceDiscovered(device, THING_TYPE_SCREEN);
                break;
            case THING_SMOKE_SENSOR:
                deviceDiscovered(device, THING_TYPE_SMOKESENSOR);
                break;
            case THING_VENETIAN_BLIND:
                deviceDiscovered(device, THING_TYPE_VENETIANBLIND);
                break;
            case THING_WINDOW:
                deviceDiscovered(device, THING_TYPE_WINDOW);
                break;
            case THING_ALARM:
                if (device.getDeviceURL().startsWith("internal:")) {
                    deviceDiscovered(device, THING_TYPE_INTERNAL_ALARM);
                } else {
                    deviceDiscovered(device, THING_TYPE_EXTERNAL_ALARM);
                }
                break;
            case THING_POD:
                if (hasState(device, CYCLIC_BUTTON_STATE)) {
                    deviceDiscovered(device, THING_TYPE_POD);
                }
                break;
            case THING_HEATING_SYSTEM:
                if (isOnOffHeatingSystem(device)) {
                    deviceDiscovered(device, THING_TYPE_ONOFF_HEATING_SYSTEM);
                } else {
                    deviceDiscovered(device, THING_TYPE_HEATING_SYSTEM);
                }
                break;
            case THING_DOOR_LOCK:
                deviceDiscovered(device, THING_TYPE_DOOR_LOCK);
                break;
            case THING_PERGOLA:
                deviceDiscovered(device, THING_TYPE_PERGOLA);
                break;
            case THING_WINDOW_HANDLE:
                deviceDiscovered(device, THING_TYPE_WINDOW_HANDLE);
                break;
            case THING_TEMPERATURE_SENSOR:
                deviceDiscovered(device, THING_TYPE_TEMPERATURESENSOR);
                break;
            case THING_GATE:
                deviceDiscovered(device, THING_TYPE_GATE);
                break;
            case THING_ELECTRICITY_SENSOR:
                if (hasEnergyConsumption(device)) {
                    deviceDiscovered(device, THING_TYPE_ELECTRICITYSENSOR);
                } else {
                    logUnsupportedDevice(device);
                }
                break;
            case THING_DOCK:
                deviceDiscovered(device, THING_TYPE_DOCK);
                break;
            case THING_SIREN:
                deviceDiscovered(device, THING_TYPE_SIREN);
                break;
            case THING_ADJUSTABLE_SLATS_ROLLER_SHUTTER:
                deviceDiscovered(device, THING_TYPE_ADJUSTABLE_SLATS_ROLLERSHUTTER);
                break;
            case THING_PROTOCOL_GATEWAY:
            case THING_REMOTE_CONTROLLER:
            case THING_NETWORK_COMPONENT:
                break;
            default:
                logUnsupportedDevice(device);
        }
    }

    private boolean isStateLess(SomfyTahomaDevice device) {
        return device.getStates().size() == 0 || (device.getStates().size() == 1 && hasState(device, STATUS_STATE));
    }

    private void logUnsupportedDevice(SomfyTahomaDevice device) {
        if (!isStateLess(device)) {
            logger.info("Detected a new unsupported device: {}", device.getUiClass());
            logger.info("If you want to add the support, please create a new issue and attach the information below");
            logger.info("Supported commands: {}", device.getDefinition());

            StringBuilder sb = new StringBuilder().append('\n');
            for (SomfyTahomaState state : device.getStates()) {
                sb.append(state.toString()).append('\n');
            }
            logger.info("Device states: {}", sb);
        }
    }

    private boolean hasState(SomfyTahomaDevice device, String state) {
        for (SomfyTahomaState st : device.getStates()) {
            if (state.equals(st.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEnergyConsumption(SomfyTahomaDevice device) {
        return hasState(device, ENERGY_CONSUMPTION_STATE);
    }

    private boolean isSilentRollerShutter(SomfyTahomaDevice device) {
        return hasCommmand(device, COMMAND_SET_CLOSURESPEED);
    }

    private boolean isOnOffHeatingSystem(SomfyTahomaDevice device) {
        return hasCommmand(device, COMMAND_SET_HEATINGLEVEL);
    }

    private boolean hasCommmand(SomfyTahomaDevice device, String command) {
        SomfyTahomaDeviceDefinition def = device.getDefinition();
        for (SomfyTahomaDeviceDefinitionCommand cmd : def.getCommands()) {
            if (command.equals(cmd.getCommandName())) {
                return true;
            }
        }
        return false;
    }

    private void deviceDiscovered(SomfyTahomaDevice device, ThingTypeUID thingTypeUID) {
        deviceDiscovered(device.getLabel(), device.getDeviceURL(), device.getOid(), thingTypeUID);
    }

    private void deviceDiscovered(String label, String deviceURL, String oid, ThingTypeUID thingTypeUID) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("url", deviceURL);
        properties.put("label", label);

        ThingUID thingUID = new ThingUID(thingTypeUID, bridge.getThing().getUID(), oid);

        logger.debug("Detected a/an {} - label: {} oid: {}", thingTypeUID.getId(), label, oid);
        thingDiscovered(DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID)
                .withProperties(properties).withRepresentationProperty("url").withLabel(label)
                .withBridge(bridge.getThing().getUID()).build());
    }

    private void actionGroupDiscovered(String label, String deviceURL, String oid) {
        deviceDiscovered(label, deviceURL, oid, THING_TYPE_ACTIONGROUP);
    }

    private void gatewayDiscovered(SomfyTahomaGateway gw) {
        Map<String, Object> properties = new HashMap<>(1);
        String type = gatewayTypes.getOrDefault(gw.getType(), "UNKNOWN");
        String id = gw.getGatewayId();
        properties.put("id", id);
        properties.put("type", type);

        ThingUID thingUID = new ThingUID(THING_TYPE_GATEWAY, bridge.getThing().getUID(), id);

        logger.debug("Detected a gateway with id: {} and type: {}", id, type);
        thingDiscovered(DiscoveryResultBuilder.create(thingUID).withThingType(THING_TYPE_GATEWAY)
                .withProperties(properties).withRepresentationProperty("id").withLabel("Somfy Gateway (" + type + ")")
                .withBridge(bridge.getThing().getUID()).build());
    }
}

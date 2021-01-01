/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.resol.handler;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.resol.internal.ResolEmuEMConfiguration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.resol.vbus.Connection;
import de.resol.vbus.Packet;
import de.resol.vbus.deviceemulators.EmDeviceEmulator;

/**
 * The {@link ResolEmuEMThingHandler} is responsible for emulating a EM device
 *
 * @author Raphael Mack - Initial contribution
 */
@NonNullByDefault
public class ResolEmuEMThingHandler extends BaseThingHandler implements PropertyChangeListener {
    public static final String CHANNEL_RELAY = "relay_";
    public static final String CHANNEL_TEMP = "temperature_";
    public static final String CHANNEL_RESIST = "resistor_";
    public static final String CHANNEL_SWITCH = "switch_";
    public static final String CHANNEL_TEMP_ADJUST = "bas_temp_adjust_";
    public static final String CHANNEL_MODE = "bas_mode_";

    private final Logger logger = LoggerFactory.getLogger(ResolEmuEMThingHandler.class);

    private int vbusAddress = 0x6650;
    private int deviceId = 1;
    private @Nullable EmDeviceEmulator device;

    private @Nullable ResolBridgeHandler bridgeHandler;

    private class BasSetting {
        float temperatureOffset = 0.0f;
        int mode = 4;
    }

    private BasSetting[] basValues = { new BasSetting(), new BasSetting(), new BasSetting(), new BasSetting(),
            new BasSetting(), new BasSetting() };
    private long lastTime = System.currentTimeMillis();

    // Background Runnable
    @Nullable
    private ScheduledFuture<?> updateJob;

    public ResolEmuEMThingHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        ResolEmuEMConfiguration configuration = getConfigAs(ResolEmuEMConfiguration.class);
        deviceId = configuration.deviceId;
        vbusAddress = 0x6650 + deviceId;

        bridgeHandler = getBridgeHandler();
        registerResolThingListener(bridgeHandler);
    }

    @Override
    public void dispose() {
        EmDeviceEmulator dev = device;
        if (dev != null) {
            dev.stop();
            dev.removePropertyChangeListener(this);
        }
        unregisterResolThingListener(bridgeHandler);
    }

    private void updateRunnable() {
        EmDeviceEmulator d = device;
        if (d != null) {
            long now = System.currentTimeMillis();
            int diff = (int) (now - lastTime);
            lastTime = now;

            d.update(diff);
        }
    }

    private void startAutomaticUpdate() {
        ScheduledFuture<?> job = updateJob;
        if (job == null || job.isCancelled()) {
            updateJob = scheduler.scheduleWithFixedDelay(this::updateRunnable, 0, 1, TimeUnit.SECONDS);
        }
    }

    private synchronized @Nullable ResolBridgeHandler getBridgeHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            logger.debug("Required bridge not defined for thing {}.", thing.getThingTypeUID());
            return null;
        } else {
            return getBridgeHandler(bridge);
        }
    }

    private synchronized @Nullable ResolBridgeHandler getBridgeHandler(Bridge bridge) {
        ResolBridgeHandler bridgeHandler = null;

        ThingHandler handler = bridge.getHandler();
        if (handler instanceof ResolBridgeHandler) {
            bridgeHandler = (ResolBridgeHandler) handler;
        } else {
            logger.debug("No available bridge handler found yet. Bridge: {} .", bridge.getUID());
        }
        return bridgeHandler;
    }

    private void registerResolThingListener(@Nullable ResolBridgeHandler bridgeHandler) {
        if (bridgeHandler != null) {
            bridgeHandler.registerResolThingListener(this);
        } else {
            logger.debug("Can't register {} at bridge as bridgeHandler is null.", this.getThing().getUID());
        }
    }

    private void unregisterResolThingListener(@Nullable ResolBridgeHandler bridgeHandler) {
        if (bridgeHandler != null) {
            bridgeHandler.unregisterThingListener(this);
        } else {
            logger.debug("Can't unregister {} at bridge as bridgeHandler is null.", this.getThing().getUID());
        }
    }

    @Override
    public void updateStatus(ThingStatus status) {
        super.updateStatus(status);
    }

    @Nullable
    EmDeviceEmulator getDevice() {
        return device;
    }

    public int getVbusAddress() {
        return vbusAddress;
    }

    public void useConnection(Connection connection) {
        EmDeviceEmulator device = this.device;
        if (device != null) {
            device.stop();
            device.removePropertyChangeListener(this);
        }
        device = new EmDeviceEmulator(connection, deviceId);
        this.device = device;
        device.addPropertyChangeListener(this);
        device.start();
        for (int i = 1; i <= 5; i++) {
            setRelayChannelValue(i, device.getRelayValueByNr(i));
        }
        startAutomaticUpdate();
    }

    public void stop() {
        if (device != null) {
            device.stop();
        }
        if (updateJob != null) {
            updateJob.cancel(false);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String chID = channelUID.getId();
        boolean update = false;
        int channel = chID.charAt(chID.length() - 1) - '0';
        float value = 0;
        int intValue = 0;

        if (command instanceof QuantityType<?>) {
            value = ((QuantityType<?>) command).floatValue();
            update = true;
        } else if (command instanceof OnOffType) {
            intValue = ((OnOffType) command).equals(OnOffType.ON) ? 1 : 0;
            update = true;
        } else if (command instanceof DecimalType) {
            intValue = ((DecimalType) command).intValue();
            value = intValue;
            update = true;
        } else {
            update = false;
        }

        if (update) {
            EmDeviceEmulator dev = device;
            if (dev != null) {
                if (chID.startsWith(CHANNEL_TEMP)) {
                    dev.setResistorValueByNrAndPt1000Temperatur(channel, value);
                    updateState(channelUID, new DecimalType(value));
                } else if (chID.startsWith(CHANNEL_SWITCH)) {
                    if (intValue == 0) {
                        /* switch is open => 1 megaohm */
                        dev.setResistorValueByNr(channel, 1000000000);
                        updateState(channelUID, OnOffType.OFF);
                    } else {
                        /* switch is closed */
                        dev.setResistorValueByNr(channel, 0);
                        updateState(channelUID, OnOffType.ON);
                    }
                } else if (chID.startsWith(CHANNEL_RESIST)) {
                    dev.setResistorValueByNr(channel, (int) (value * 1000.0));
                    updateState(channelUID, new QuantityType<>(intValue, Units.OHM));
                } else if (chID.startsWith(CHANNEL_TEMP_ADJUST)) {
                    basValues[channel - 1].temperatureOffset = value;
                    updateBas(channel);
                    updateState(channelUID, new DecimalType(value));
                } else if (chID.startsWith(CHANNEL_MODE)) {
                    basValues[channel - 1].mode = intValue;
                    updateBas(channel);
                    updateState(channelUID, new DecimalType(intValue));
                } else {
                    /* set resistor value for Open Connection, 1 megaohm */
                    dev.setResistorValueByNr(channel, 1000000000);
                    updateState(channelUID, new QuantityType<>(1000000, Units.OHM));
                }
            }
        }
    }

    private void updateBas(int channel) {
        int resistor = 0; /* in milliohm */
        int delta = (int) ((basValues[channel - 1].temperatureOffset * 210.0f / 15.0f) * 1000.0f);
        switch (basValues[channel - 1].mode) {
            case 4: /* Automatic range 76 - 496 ohm */
                resistor = 286 * 1000 + delta;
                break;
            case 0: /* OFF range 1840 - 2260 ohm */
                resistor = 2050 * 1000 + delta;
                break;
            case 2: /* Night range 660 - 1080 ohm */
                resistor = 870 * 1000 + delta;
                break;
            case 3: /* Party is automatic mode with +15K */
                resistor = 286 * 1000 + 210 * 1000;
                break;
            case 1: /* Summer range 1240 - 1660 ohm */
                resistor = 1450 * 1000 + delta;
                break;
            default:
                /* signal a shortcut as error */
                resistor = 0;
                break;
        }
        if (device != null) {
            device.setResistorValueByNr(channel, resistor);
        }
    }

    @Override
    public void propertyChange(@Nullable PropertyChangeEvent evt) {
        if (evt != null) {
            String s = evt.getPropertyName();
            if (s.startsWith("relay") && s.endsWith("Value")) {
                int v = (Integer) evt.getNewValue();
                int i = Integer.parseInt(s.substring(5, 6));
                setRelayChannelValue(i, v);
            }
        }
    }

    public void handle(Packet packet) {
        updateStatus(ThingStatus.ONLINE);
    }

    private void setRelayChannelValue(int relay, double value) {
        String channelId = CHANNEL_RELAY + relay;
        updateState(channelId, new DecimalType(value));
    }
}

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
package org.openhab.binding.somfytahoma.internal.handler;

import static org.openhab.binding.somfytahoma.internal.SomfyTahomaBindingConstants.*;

import java.util.HashMap;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.*;
import org.eclipse.smarthome.core.thing.*;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.somfytahoma.internal.model.SomfyTahomaState;
import org.openhab.binding.somfytahoma.internal.model.SomfyTahomaStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SomfyTahomaBaseThingHandler} is base thing handler for all things.
 *
 * @author Ondrej Pecta - Initial contribution
 */
@NonNullByDefault
public abstract class SomfyTahomaBaseThingHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(SomfyTahomaBaseThingHandler.class);
    private HashMap<String, Integer> typeTable = new HashMap<>();
    protected HashMap<String, String> stateNames = new HashMap<>();

    public SomfyTahomaBaseThingHandler(Thing thing) {
        super(thing);
    }

    public HashMap<String, String> getStateNames() {
        return stateNames;
    }

    @Override
    public void initialize() {
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Received command {} for channel {}", command, channelUID);
    }

    protected boolean isAlwaysOnline() {
        return false;
    }

    protected @Nullable SomfyTahomaBridgeHandler getBridgeHandler() {
        return this.getBridge() != null ? (SomfyTahomaBridgeHandler) this.getBridge().getHandler() : null;
    }

    private String getURL() {
        return getThing().getConfiguration().get("url") != null ? getThing().getConfiguration().get("url").toString() : "";
    }

    private void setAvailable() {
        if (ThingStatus.ONLINE != thing.getStatus()) {
            updateStatus(ThingStatus.ONLINE);
        }
    }

    private void setUnavailable() {
        if (ThingStatus.OFFLINE != thing.getStatus() && !isAlwaysOnline()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, UNAVAILABLE);
        }
    }

    private boolean isChannelLinked(Channel channel) {
        return isLinked(channel.getUID().getId());
    }

    protected void sendCommand(String cmd) {
        sendCommand(cmd, "[]");
    }

    protected void sendCommand(String cmd, String param) {
        if (getBridgeHandler() != null) {
            getBridgeHandler().sendCommand(getURL(), cmd, param);
        }
    }

    protected void executeActionGroup() {
        if (getBridgeHandler() != null) {
            getBridgeHandler().executeActionGroup(getURL());
        }
    }

    protected @Nullable String getCurrentExecutions() {
        if (getBridgeHandler() != null) {
            return getBridgeHandler().getCurrentExecutions(getURL());
        }
        return null;
    }

    protected void cancelExecution(String executionId) {
        if (getBridgeHandler() != null) {
            getBridgeHandler().cancelExecution(executionId);
        }
    }

    protected SomfyTahomaStatus getTahomaStatus(String id) {
        if (getBridgeHandler() != null) {
            return getBridgeHandler().getTahomaStatus(id);
        }
        return new SomfyTahomaStatus();
    }

    private void cacheStateType(SomfyTahomaState state) {
        if (state.getType() > 0 && !typeTable.containsKey(state.getName())) {
            typeTable.put(state.getName(), state.getType());
        }
    }

    protected void cacheStateType(String stateName, int type) {
        if (type > 0 && !typeTable.containsKey(stateName)) {
            typeTable.put(stateName, type);
        }
    }

    protected  @Nullable State parseTahomaState(@Nullable SomfyTahomaState state) {
        return parseTahomaState(null, state);
    }

    private @Nullable State parseTahomaState(@Nullable String acceptedState, @Nullable SomfyTahomaState state) {
        if (state == null) {
            return UnDefType.NULL;
        }

        int type = state.getType();

        try {
            if (typeTable.containsKey(state.getName())) {
                type = typeTable.get(state.getName());
            } else {
                cacheStateType(state);
            }

            if (type == 0) {
                logger.debug("Cannot recognize the state type for: {}!", state.getValue());
                return null;
            }

            logger.trace("Value to parse: {}, type: {}", state.getValue(), type);
            switch (type) {
                case TYPE_PERCENT:
                    Double valPct = Double.parseDouble(state.getValue().toString());
                    return new PercentType(valPct.intValue());
                case TYPE_DECIMAL:
                    Double valDec = Double.parseDouble(state.getValue().toString());
                    return new DecimalType(valDec);
                case TYPE_STRING:
                case TYPE_BOOLEAN:
                    String value = state.getValue().toString().toLowerCase();
                    if ("String".equals(acceptedState)) {
                        return new StringType(value);
                    } else {
                        return parseStringState(value);
                    }
                default:
                    return null;
            }
        } catch (NumberFormatException ex) {
            logger.debug("Error while parsing Tahoma state! Value: {} type: {}", state.getValue(), type, ex);
        }
        return null;
    }

    private State parseStringState(String value) {
        switch (value) {
            case "on":
            case "true":
                return OnOffType.ON;
            case "off":
            case "false":
                return OnOffType.OFF;
            case "notDetected":
            case "nopersoninside":
            case "closed":
            case "locked":
                return OpenClosedType.CLOSED;
            case "detected":
            case "personinside":
            case "open":
            case "unlocked":
                return OpenClosedType.OPEN;
            default:
                logger.debug("Unknown thing state returned: {}", value);
                return UnDefType.UNDEF;
        }
    }

    public void updateThingStatus(List<SomfyTahomaState> states) {
        SomfyTahomaState state = getStatusState(states);
        updateThingStatus(state);
    }

    private @Nullable SomfyTahomaState getStatusState(List<SomfyTahomaState> states) {
        for (SomfyTahomaState state : states) {
            if (STATUS_STATE.equals(state.getName()) && state.getType() == TYPE_STRING) {
                return state;
            }
        }
        return null;
    }

    private void updateThingStatus(@Nullable SomfyTahomaState state) {
        if (state == null) {
            //Most probably we are dealing with RTS device which does not return states
            //so we have to setup ONLINE status manually
            setAvailable();
            return;
        }
        if (STATUS_STATE.equals(state.getName()) && state.getType() == TYPE_STRING) {
            if (UNAVAILABLE.equals(state.getValue())) {
                setUnavailable();
            } else {
                setAvailable();
            }
        }
    }

    public void updateThingChannels(List<SomfyTahomaState> states) {
        for (SomfyTahomaState state : states) {
            logger.trace("processing state: {} with value: {}", state.getName(), state.getValue());
            updateProperty(state.getName(), state.getValue().toString());
            for (HashMap.Entry<String, String> entry : stateNames.entrySet()) {
                if (entry.getValue().equals(state.getName()) ) {
                    //get channel and update it if linked
                    Channel ch = thing.getChannel(entry.getKey());
                    if (ch != null && isChannelLinked(ch)) {
                        logger.trace("updating channel: {} with value: {}", entry.getKey(), state.getValue());
                        State newState = parseTahomaState(ch.getAcceptedItemType(), state);
                        updateState(ch.getUID(), newState);
                    }
                }
            }
        }
    }
}

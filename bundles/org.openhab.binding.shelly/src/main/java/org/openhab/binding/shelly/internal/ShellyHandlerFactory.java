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
package org.openhab.binding.shelly.internal;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.commons.lang.Validate;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.net.HttpServiceUtil;
import org.eclipse.smarthome.core.net.NetworkAddressService;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.shelly.internal.coap.ShellyCoapServer;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.handler.ShellyBaseHandler;
import org.openhab.binding.shelly.internal.handler.ShellyDeviceListener;
import org.openhab.binding.shelly.internal.handler.ShellyLightHandler;
import org.openhab.binding.shelly.internal.handler.ShellyProtectedHandler;
import org.openhab.binding.shelly.internal.handler.ShellyRelayHandler;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.annotations.NonNull;

/**
 * The {@link ShellyHandlerFactory} is responsible for creating things and thing handlers.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@Component(service = { ThingHandlerFactory.class, ShellyHandlerFactory.class }, configurationPid = "binding.shelly")
public class ShellyHandlerFactory extends BaseThingHandlerFactory {
    private final Logger logger = LoggerFactory.getLogger(ShellyHandlerFactory.class);
    private final ShellyCoapServer coapServer;
    private final Set<ShellyDeviceListener> deviceListeners = new CopyOnWriteArraySet<>();

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = ShellyBindingConstants.SUPPORTED_THING_TYPES_UIDS;
    private ShellyBindingConfiguration bindingConfig = new ShellyBindingConfiguration();
    private String localIP = "";
    private int httpPort = -1;

    /**
     * Activate the bundle: save properties
     *
     * @param componentContext
     * @param configProperties set of properties from cfg (use same names as in
     *            thing config)
     */
    @Activate
    public ShellyHandlerFactory(@Reference NetworkAddressService networkAddressService,
            ComponentContext componentContext, Map<String, @Nullable Object> configProperties) {
        logger.debug("Activate Shelly HandlerFactory");
        super.activate(componentContext);

        this.coapServer = new ShellyCoapServer();
        Validate.notNull(coapServer, "coapServer creation failed!");

        Validate.notNull(configProperties);
        bindingConfig.updateFromProperties(configProperties);
        httpPort = HttpServiceUtil.getHttpServicePort(componentContext.getBundleContext());
        if (httpPort == -1) {
            httpPort = 8080;
        }
        Validate.isTrue(httpPort > 0, "Unable to get OH HTTP port");
        logger.debug("Using OH HTTP port {}", httpPort);

        String lip = networkAddressService.getPrimaryIpv4HostAddress();
        localIP = lip != null ? lip : "";
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        String thingType = thingTypeUID.getId();
        ShellyBaseHandler handler = null;

        if (thingType.equals(THING_TYPE_SHELLYPROTECTED_STR)) {
            logger.debug("Create new thing of type {} using ShellyRelayHandler", thingTypeUID.getId());
            handler = new ShellyProtectedHandler(thing, bindingConfig, coapServer, localIP, httpPort);
        } else if (thingType.equals(THING_TYPE_SHELLYBULB.getId())
                || thingType.equals(THING_TYPE_SHELLYRGBW2_COLOR.getId())
                || thingType.equals(THING_TYPE_SHELLYRGBW2_WHITE.getId())) {
            logger.debug("Create new thing of type {} using ShellyLightHandler", thingTypeUID.getId());
            handler = new ShellyLightHandler(thing, bindingConfig, coapServer, localIP, httpPort);
        } else if (SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID)) {
            logger.debug("Create new thing of type {} using ShellyRelayHandler", thingTypeUID.getId());
            handler = new ShellyRelayHandler(thing, bindingConfig, coapServer, localIP, httpPort);
        }

        if (handler != null) {
            deviceListeners.add(handler);
            return handler;
        }

        logger.debug("Unable to create Thing Handler instance!");
        return null;
    }

    /**
     * Remove handler of things.
     */
    @SuppressWarnings("unlikely-arg-type")
    @Override
    protected synchronized void removeHandler(@NonNull ThingHandler thingHandler) {
        if (thingHandler instanceof ShellyBaseHandler) {
            deviceListeners.remove(thingHandler);
        }
    }

    /**
     * Dispatch event to registered devices.
     *
     * @param deviceName
     * @param componentIndex Index of component, e.g. 2 for relay2
     * @param eventType Type of event, e.g. light
     * @param parameters Input parameters from URL, e.g. on sensor reports
     */
    public void onEvent(String deviceName, String componentIndex, String eventType, Map<String, String> parameters) {
        logger.trace("Dispatch event to device handler {}", deviceName);
        for (ShellyDeviceListener listener : deviceListeners) {
            try {
                if (listener.onEvent(deviceName, componentIndex, eventType, parameters)) {
                    // event processed
                    break;
                }
            } catch (NullPointerException e) {
                logger.debug("Unable to process callback: {} ({}), deviceName={}, type={}, index={}, parameters={}\n{}",
                        e.getMessage(), e.getClass(), deviceName, eventType, componentIndex, parameters.toString(),
                        e.getStackTrace());
                // continue with next listener
            }
        }
    }

    @Nullable
    public ShellyBindingConfiguration getBindingConfig() {
        return bindingConfig;
    }
}

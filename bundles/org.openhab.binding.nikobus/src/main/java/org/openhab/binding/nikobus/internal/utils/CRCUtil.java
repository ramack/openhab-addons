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
package org.openhab.binding.nikobus.internal.utils;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.util.HexUtils;

/**
 * The {@link CRCUtil} class defines utility functions to calculate CRC used by the Nikobus communication protocol.
 *
 * @author Davy Vanherbergen - Initial contribution
 * @author Boris Krivonog - Removed dependency to javax.xml.bind.DatatypeConverter
 */
public class CRCUtil {

    private static final int CRC_INIT = 0xFFFF;

    private static final int POLYNOMIAL = 0x1021;

    /**
     * Calculate the CRC16-CCITT checksum on the input string and return the
     * input string with the checksum appended.
     *
     * @param input
     *            String representing hex numbers.
     * @return input string + CRC.
     */
    public static String appendCRC(String input) {
        if (input == null) {
            return null;
        }

        int check = CRC_INIT;

        for (byte b : HexUtils.hexToBytes(input)) {
            for (int i = 0; i < 8; i++) {
                if (((b >> (7 - i) & 1) == 1) ^ ((check >> 15 & 1) == 1)) {
                    check = check << 1;
                    check = check ^ POLYNOMIAL;
                } else {
                    check = check << 1;
                }
            }
        }

        check = check & CRC_INIT;
        String checksum = StringUtils.leftPad(Integer.toHexString(check), 4, "0");
        return (input + checksum).toUpperCase();
    }

    /**
     * Calculate the second checksum on the input string and return the
     * input string with the checksum appended.
     *
     * @param input
     *            String representing a nikobus command.
     * @return input string + CRC.
     */
    public static String appendCRC2(String input) {
        int check = 0;

        for (byte b : input.getBytes()) {

            check = check ^ b;

            for (int i = 0; i < 8; i++) {

                if (((check & 0xff) >> 7) != 0) {
                    check = check << 1;
                    check = check ^ 0x99;
                } else {
                    check = check << 1;
                }
                check = check & 0xff;
            }
        }

        return input + StringUtils.leftPad(Integer.toHexString(check), 2, "0").toUpperCase();
    }
}

/* ==================================================================
 * SDM120Data.java - 23/01/2016 5:33:22 pm
 * 
 * Copyright 2007-2016 SolarNetwork.net Dev Team
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 * 02111-1307 USA
 * ==================================================================
 */

package net.solarnetwork.node.hw.deson.meter;

import net.solarnetwork.node.io.modbus.ModbusConnection;

/**
 * Encapsulates raw Modbus register data from SDM 120 meters.
 * 
 * @author matt
 * @version 1.0
 */
public class SDM120Data extends BaseSDMData {

	// voltage (Float32)
	public static final int ADDR_DATA_V_NEUTRAL = 0;

	// current (Float32)
	public static final int ADDR_DATA_I = 6;

	// power (Float32)
	public static final int ADDR_DATA_ACTIVE_POWER = 12;
	public static final int ADDR_DATA_APPARENT_POWER = 18;
	public static final int ADDR_DATA_REACTIVE_POWER = 24;

	// power factor (Float32)
	public static final int ADDR_DATA_POWER_FACTOR = 30;

	// frequency (Float32)
	public static final int ADDR_DATA_FREQUENCY = 70;

	// total energy (Float32, k)
	public static final int ADDR_DATA_ACTIVE_ENERGY_IMPORT_TOTAL = 72;
	public static final int ADDR_DATA_ACTIVE_ENERGY_EXPORT_TOTAL = 74;
	public static final int ADDR_DATA_REACTIVE_ENERGY_IMPORT_TOTAL = 76;
	public static final int ADDR_DATA_REACTIVE_ENERGY_EXPORT_TOTAL = 78;

	/**
	 * Default constructor.
	 */
	public SDM120Data() {
		super();
	}

	/**
	 * Copy constructor.
	 * 
	 * @param other
	 *        the object to copy
	 */
	public SDM120Data(SDM120Data other) {
		super(other);
	}

	@Override
	public String toString() {
		return "SDM120Data{V=" + getVoltage(ADDR_DATA_V_NEUTRAL) + ",A=" + getCurrent(ADDR_DATA_I)
				+ ",PF=" + getPowerFactor(ADDR_DATA_POWER_FACTOR) + ",Hz="
				+ getFrequency(ADDR_DATA_FREQUENCY) + ",W=" + getPower(ADDR_DATA_ACTIVE_POWER) + ",var="
				+ getPower(ADDR_DATA_REACTIVE_POWER) + ",VA=" + getPower(ADDR_DATA_APPARENT_POWER)
				+ ",Wh-I=" + getEnergy(ADDR_DATA_ACTIVE_ENERGY_IMPORT_TOTAL) + ",varh-I="
				+ getEnergy(ADDR_DATA_REACTIVE_ENERGY_IMPORT_TOTAL) + ",Wh-E="
				+ getEnergy(ADDR_DATA_ACTIVE_ENERGY_EXPORT_TOTAL) + ",varh-E="
				+ getEnergy(ADDR_DATA_REACTIVE_ENERGY_EXPORT_TOTAL) + "}";
	}

	@Override
	public String dataDebugString() {
		final SDM120Data snapshot = new SDM120Data(this);
		return dataDebugString(snapshot);
	}

	@Override
	public boolean readMeterDataInternal(final ModbusConnection conn) {
		readIntData(conn, ADDR_DATA_V_NEUTRAL, ADDR_DATA_V_NEUTRAL + 80);
		return true;
	}

}
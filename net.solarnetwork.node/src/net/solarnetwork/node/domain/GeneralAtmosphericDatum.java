/* ==================================================================
 * GeneralAtmosphericDatum.java - Oct 22, 2014 2:30:22 PM
 * 
 * Copyright 2007-2014 SolarNetwork.net Dev Team
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

package net.solarnetwork.node.domain;

import net.solarnetwork.util.SerializeIgnore;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * GeneralLocationDatum that also implements {@link AtmosphericDatum}.
 * 
 * @author matt
 * @version 1.0
 */
public class GeneralAtmosphericDatum extends GeneralLocationDatum implements AtmosphericDatum {

	@Override
	@JsonIgnore
	@SerializeIgnore
	public Float getTemperature() {
		return getInstantaneousSampleFloat(TEMPERATURE_KEY);
	}

	public void setTemperature(Float value) {
		putInstantaneousSampleValue(TEMPERATURE_KEY, value);
	}

	@Override
	@JsonIgnore
	@SerializeIgnore
	public Float getDewPoint() {
		return getInstantaneousSampleFloat(DEW_POINT_KEY);
	}

	public void setDewPoint(Float value) {
		putInstantaneousSampleValue(DEW_POINT_KEY, value);
	}

	@Override
	@JsonIgnore
	@SerializeIgnore
	public Integer getHumidity() {
		return getInstantaneousSampleInteger(HUMIDITY_KEY);
	}

	public void setHumidity(Integer value) {
		putInstantaneousSampleValue(HUMIDITY_KEY, value);
	}

	@Override
	@JsonIgnore
	@SerializeIgnore
	public Integer getAtmosphericPressure() {
		return getInstantaneousSampleInteger(ATMOSPHERIC_PRESSURE_KEY);
	}

	public void setAtmosphericPressure(Integer value) {
		putInstantaneousSampleValue(ATMOSPHERIC_PRESSURE_KEY, value);
	}

	@Override
	@JsonIgnore
	@SerializeIgnore
	public Integer getVisibility() {
		return getInstantaneousSampleInteger(VISIBILITY_KEY);
	}

	public void setVisibility(Integer value) {
		putInstantaneousSampleValue(VISIBILITY_KEY, value);
	}

	@Override
	@JsonIgnore
	@SerializeIgnore
	public String getSkyConditions() {
		return getStatusSampleString(SKY_CONDITIONS_KEY);
	}

	public void setSkyConditions(String value) {
		putStatusSampleValue(SKY_CONDITIONS_KEY, value);
	}

}

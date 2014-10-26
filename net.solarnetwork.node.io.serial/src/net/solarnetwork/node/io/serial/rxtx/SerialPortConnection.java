/* ==================================================================
 * SerialPortConnection.java - Oct 23, 2014 2:21:31 PM
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

package net.solarnetwork.node.io.serial.rxtx;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;
import gnu.trove.list.array.TByteArrayList;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TooManyListenersException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.solarnetwork.node.LockTimeoutException;
import net.solarnetwork.node.io.serial.SerialConnection;
import net.solarnetwork.node.support.SerialPortBeanParameters;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RXTX implementation of {@link SerialConnection}.
 * 
 * @author matt
 * @version 1.0
 */
public class SerialPortConnection implements SerialConnection, SerialPortEventListener {

	/** A class-level logger. */
	private static final Logger log = LoggerFactory.getLogger(SerialPortConnection.class);

	/** A class-level logger with the suffix SERIAL_EVENT. */
	private static final Logger eventLog = LoggerFactory.getLogger(SerialPortConnection.class.getName()
			+ ".SERIAL_EVENT");

	private final SerialPortBeanParameters serialParams;
	private final ExecutorService executor;
	private long timeout = 0;

	private SerialPort serialPort;
	private InputStream in;
	private OutputStream out;
	private final boolean listening = false;
	private final boolean collecting = false;

	/**
	 * Constructor.
	 * 
	 * @param serialPort
	 *        the SerialPort to use
	 * @param serialParams
	 *        the parameters to use with the SerialPort
	 * @param maxWait
	 *        the maximum number of milliseconds to wait when waiting to read
	 *        data
	 */
	public SerialPortConnection(SerialPortBeanParameters params) {
		this.serialParams = params;
		if ( params.getMaxWait() > 0 ) {
			executor = Executors.newFixedThreadPool(1);
		} else {
			executor = null;
		}
	}

	@Override
	public void open() throws IOException, LockTimeoutException {
		CommPortIdentifier portId = getCommPortIdentifier(serialParams.getSerialPort());
		try {
			serialPort = (SerialPort) portId.open(serialParams.getCommPortAppName(), 2000);
			setupSerialPortParameters(this);
		} catch ( PortInUseException e ) {
			throw new IOException("Serial port " + serialParams.getSerialPort() + " in use", e);
		} catch ( TooManyListenersException e ) {
			try {
				close();
			} catch ( Exception e2 ) {
				// ignore this
			}
			throw new IOException("Serial port " + serialParams.getSerialPort()
					+ " has too many listeners", e);
		}
	}

	/**
	 * Test if the serial port has been opened.
	 * 
	 * @return boolean
	 */
	public boolean isOpen() {
		return (serialPort != null);
	}

	@Override
	public void close() {
		if ( serialPort == null ) {
			return;
		}
		try {
			log.debug("Closing serial port {}", this.serialPort);
			if ( in != null ) {
				try {
					in.close();
				} catch ( IOException e ) {
					// ignore this
				}
			}
			serialPort.close();
			log.trace("Serial port closed");
		} finally {
			if ( executor != null ) {
				try {
					// FIXME: this means it can't be started up again... should that be allowed?
					executor.shutdownNow();
				} catch ( Exception e ) {
					log.debug("Exception shutting down Executor", e);
				}
			}
			serialPort = null;
		}
	}

	@Override
	public byte[] readMarkedMessage(final byte[] startMarker, final byte[] endMarker) throws IOException {
		final InputStream input = getInputStream();
		final TByteArrayList sink = new TByteArrayList(1024);
		boolean result = false;
		if ( serialParams.getMaxWait() < 1 ) {
			do {
				result = readMarkedMessage(input, sink, startMarker, endMarker);
			} while ( !result );
			return sink.toArray();
		}

		final AtomicBoolean keepGoing = new AtomicBoolean(true);

		Callable<Boolean> task = new Callable<Boolean>() {

			@Override
			public Boolean call() throws Exception {
				boolean found = false;
				do {
					found = readMarkedMessage(input, sink, startMarker, endMarker);
				} while ( !found && keepGoing.get() );
				return found;
			}
		};
		timeoutStart();
		Future<Boolean> future = executor.submit(task);
		final long maxMs = Math.max(1, serialParams.getMaxWait() - System.currentTimeMillis() + timeout);
		eventLog.trace("Waiting at most {}ms for data", maxMs);
		try {
			result = future.get(maxMs, TimeUnit.MILLISECONDS);
		} catch ( InterruptedException e ) {
			log.debug("Interrupted waiting for serial data");
			throw new IOException("Interrupted waiting for serial data", e);
		} catch ( ExecutionException e ) {
			// log stack trace in DEBUG
			log.debug("Exception thrown reading from serial port", e.getCause());
			throw new IOException("Exception thrown reading from serial port", e.getCause());
		} catch ( TimeoutException e ) {
			log.warn("Timeout waiting {}ms for serial data, aborting read", maxMs);
			future.cancel(true);
			throw new IOException("Timeout waiting " + maxMs + "ms for serial data", e.getCause());
		} finally {
			keepGoing.set(false);
		}
		return (result ? sink.toArray() : null);
	}

	private boolean readMarkedMessage(final InputStream in, final TByteArrayList sink,
			final byte[] startMarker, final byte[] endMarker) throws IOException {
		boolean lookingForEndMarker = (sink.size() > startMarker.length);
		int max = (lookingForEndMarker ? endMarker.length : startMarker.length);
		byte[] buf = new byte[(max > 64 ? max : 64)];
		if ( eventLog.isTraceEnabled() ) {
			eventLog.trace("Sink contains {} bytes: {}", sink.size(), asciiDebugValue(sink.toArray()));
		}
		int len = -1;
		eventLog.trace("Attempting to read up to {} bytes from serial port", max);
		while ( max > 0 && (len = in.read(buf, 0, max)) > 0 ) {
			sink.add(buf, 0, len);
			int foundMarkerByteCount = findMarkerBytes(sink, len, (lookingForEndMarker ? endMarker
					: startMarker), lookingForEndMarker);
			if ( lookingForEndMarker == false && foundMarkerByteCount == startMarker.length ) {
				lookingForEndMarker = true;
				// immediately look for end marker, might already be in the buffer
				foundMarkerByteCount = findMarkerBytes(sink, startMarker.length, endMarker, true);
			}
			if ( lookingForEndMarker && foundMarkerByteCount == endMarker.length ) {
				return true;
			}
		}
		if ( eventLog.isTraceEnabled() ) {
			eventLog.debug("Looking for marker {}, buffer: {}",
					(lookingForEndMarker ? asciiDebugValue(endMarker) : asciiDebugValue(startMarker)),
					asciiDebugValue(sink.toArray()));
		}
		return false;
	}

	private InputStream getInputStream() throws IOException {
		if ( in != null ) {
			return in;
		}
		if ( !isOpen() ) {
			open();
		}
		in = serialPort.getInputStream();
		return in;
	}

	@SuppressWarnings("unchecked")
	private CommPortIdentifier getCommPortIdentifier(final String portId) throws IOException {
		// first try directly
		CommPortIdentifier commPortId = null;
		try {
			commPortId = CommPortIdentifier.getPortIdentifier(portId);
			if ( commPortId != null ) {
				log.debug("Found port identifier: {}", portId);
				return commPortId;
			}
		} catch ( NoSuchPortException e ) {
			log.debug("Port {} not found, inspecting available ports...", portId);
		}
		Enumeration<CommPortIdentifier> portIdentifiers = CommPortIdentifier.getPortIdentifiers();
		List<String> foundNames = new ArrayList<String>(5);
		while ( portIdentifiers.hasMoreElements() ) {
			CommPortIdentifier commPort = portIdentifiers.nextElement();
			log.trace("Inspecting available port identifier: {}", commPort.getName());
			foundNames.add(commPort.getName());
			if ( commPort.getPortType() == CommPortIdentifier.PORT_SERIAL
					&& portId.equals(commPort.getName()) ) {
				commPortId = commPort;
				log.debug("Found port identifier: {}", portId);
				break;
			}
		}
		if ( commPortId == null ) {
			throw new IOException("Couldn't find port identifier for [" + portId
					+ "]; available ports: " + foundNames);
		}
		return commPortId;
	}

	@Override
	public void serialEvent(SerialPortEvent event) {
		if ( eventLog.isTraceEnabled() && event.getEventType() != SerialPortEvent.DATA_AVAILABLE ) {
			eventLog.trace("SerialPortEvent {}; listening {}; collecting {}",
					new Object[] { event.getEventType(), listening, collecting });
		}
	}

	/**
	 * Set a "timeout" flag, so that all subsequent calls to use as the
	 * reference point for calculating the maximum time to wait for serial data.
	 * 
	 * <p>
	 * When called, the {@code handleSerialEvent} method will treat the time
	 * offset from the call to this method as the reference amount of time that
	 * has passed before the {@code maxWait} value triggers a timeout.
	 * </p>
	 */
	protected void timeoutStart() {
		if ( serialParams.getMaxWait() > 0 ) {
			timeout = System.currentTimeMillis();
		}
	}

	/**
	 * Clear the timeout flag, so no timeout used.
	 * 
	 * @see #timeoutStart()
	 */
	protected void timeoutClear() {
		timeout = 0;
	}

	/**
	 * Set up the SerialPort for use, configuring with class properties.
	 * 
	 * <p>
	 * This method can be called once when wanting to start using the serial
	 * port.
	 * </p>
	 * 
	 * @param listener
	 *        a listener to pass to
	 *        {@link SerialPort#addEventListener(SerialPortEventListener)}
	 */
	private void setupSerialPortParameters(SerialPortEventListener listener)
			throws TooManyListenersException {
		if ( listener != null ) {
			serialPort.addEventListener(listener);
		}

		serialPort.notifyOnDataAvailable(true);

		try {

			if ( serialParams.getReceiveFraming() >= 0 ) {
				serialPort.enableReceiveFraming(serialParams.getReceiveFraming());
				if ( !serialPort.isReceiveFramingEnabled() ) {
					log.warn("Receive framing configured as {} but not supported by driver.",
							serialParams.getReceiveFraming());
				} else if ( log.isDebugEnabled() ) {
					log.debug("Receive framing set to {}", serialParams.getReceiveFraming());
				}
			} else {
				serialPort.disableReceiveFraming();
			}

			if ( serialParams.getReceiveTimeout() >= 0 ) {
				serialPort.enableReceiveTimeout(serialParams.getReceiveTimeout());
				if ( !serialPort.isReceiveTimeoutEnabled() ) {
					log.warn("Receive timeout configured as {} but not supported by driver.",
							serialParams.getReceiveTimeout());
				} else if ( log.isDebugEnabled() ) {
					log.debug("Receive timeout set to {}", serialParams.getReceiveTimeout());
				}
			} else {
				serialPort.disableReceiveTimeout();
			}
			if ( serialParams.getReceiveThreshold() >= 0 ) {
				serialPort.enableReceiveThreshold(serialParams.getReceiveThreshold());
				if ( !serialPort.isReceiveThresholdEnabled() ) {
					log.warn("Receive threshold configured as [{}] but not supported by driver.",
							serialParams.getReceiveThreshold());
				} else if ( log.isDebugEnabled() ) {
					log.debug("Receive threshold set to {}", serialParams.getReceiveThreshold());
				}
			} else {
				serialPort.disableReceiveThreshold();
			}

			if ( log.isDebugEnabled() ) {
				log.debug(
						"Setting serial port baud = {}, dataBits = {}, stopBits = {}, parity = {}",
						new Object[] { serialParams.getBaud(), serialParams.getDataBits(),
								serialParams.getStopBits(), serialParams.getParity() });
			}
			serialPort.setSerialPortParams(serialParams.getBaud(), serialParams.getDataBits(),
					serialParams.getStopBits(), serialParams.getParity());

			if ( serialParams.getFlowControl() >= 0 ) {
				log.debug("Setting flow control to {}", serialParams.getFlowControl());
				serialPort.setFlowControlMode(serialParams.getFlowControl());
			}

			if ( serialParams.getDtrFlag() >= 0 ) {
				boolean mode = serialParams.getDtrFlag() > 0 ? true : false;
				log.debug("Setting DTR to {}", mode);
				serialPort.setDTR(mode);
			}
			if ( serialParams.getRtsFlag() >= 0 ) {
				boolean mode = serialParams.getRtsFlag() > 0 ? true : false;
				log.debug("Setting RTS to {}", mode);
				serialPort.setRTS(mode);
			}

		} catch ( UnsupportedCommOperationException e ) {
			throw new RuntimeException(e);
		}
	}

	private int findMarkerBytes(final TByteArrayList sink, final int appendedLength,
			final byte[] marker, final boolean end) {
		//final byte[] sinkBuf = sink.toArray();
		final int sinkBufLength = sink.size();
		int markerIdx = Math.max(0, sinkBufLength - appendedLength - marker.length);
		boolean foundMarker = false;
		int j = 0;

		eventLog.trace("Looking for {} marker bytes {} in buffer {}", new Object[] { marker.length,
				asciiDebugValue(marker), asciiDebugValue(sink.toArray()) });
		for ( ; markerIdx < sinkBufLength; markerIdx++ ) {
			foundMarker = true;
			for ( j = 0; j < marker.length && (j + markerIdx) < sinkBufLength; j++ ) {
				if ( sink.getQuick(markerIdx + j) != marker[j] ) {
					foundMarker = false;
					break;
				}
			}
			if ( foundMarker ) {
				break;
			}
		}
		// we may have only found a partial match at the end of the buffer, so test j here
		if ( foundMarker && j == marker.length ) {
			if ( eventLog.isDebugEnabled() ) {
				eventLog.debug("Found desired {} marker bytes at index {}", asciiDebugValue(marker),
						markerIdx);
			}
			if ( end ) {
				sink.remove(markerIdx + marker.length, sink.size() - markerIdx - marker.length);
			} else {
				// shift bytes to start at marker
				sink.remove(0, markerIdx);
			}
			if ( eventLog.isDebugEnabled() ) {
				eventLog.debug("Buffer message at marker: {}", asciiDebugValue(sink.toArray()));
			}
			return marker.length;
		} else if ( !end ) {
			// truncate sink to any partial match
			if ( j > 0 ) {
				sink.remove(0, markerIdx);
			} else {
				sink.resetQuick();
			}
		}
		return j;
	}

	/**
	 * Read from the InputStream until it is empty.
	 * 
	 * @param in
	 */
	private void drainInputStream(InputStream in) {
		byte[] buf = new byte[1024];
		int len = -1;
		int total = 0;
		try {
			final int max = Math.min(in.available(), buf.length);
			eventLog.trace("Attempting to drain {} bytes from serial port", max);
			while ( max > 0 && (len = in.read(buf, 0, max)) > 0 ) {
				// keep draining
				total += len;
			}
		} catch ( IOException e ) {
			// ignore this
		}
		eventLog.trace("Drained {} bytes from serial port", total);
	}

	private String asciiDebugValue(byte[] data) {
		if ( data == null || data.length < 1 ) {
			return "";
		}
		StringBuilder buf = new StringBuilder();
		buf.append(Hex.encodeHex(data)).append(" (");
		for ( byte b : data ) {
			if ( b >= 32 && b < 126 ) {
				buf.append(Character.valueOf((char) b));
			} else {
				buf.append('~');
			}
		}
		buf.append(")");
		return buf.toString();
	}

	public SerialPort getSerialPort() {
		return serialPort;
	}

}
